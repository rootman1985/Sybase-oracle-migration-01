package com.example.migrator;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public final class MigrationService {
    private final Db sybase;
    private final Db oracle;
    private final MetadataService metadata;
    private final StateStore state;
    private final Report report;
    private final int batchSize;
    private final int rangeSize;
    private final int fetchSize;
    private final int commitEveryBatches;

    public MigrationService(Db sybase, Db oracle, MetadataService metadata, StateStore state,
                            Report report, int batchSize, int rangeSize, int fetchSize, int commitEveryBatches) {
        this.sybase = sybase; this.oracle = oracle; this.metadata = metadata;
        this.state = state; this.report = report; this.batchSize = batchSize;
        this.rangeSize = rangeSize; this.fetchSize = fetchSize; this.commitEveryBatches = commitEveryBatches;
    }

    public void check(TableConfig t, List<ColumnMapping> mappings) throws Exception {
        List<ColumnInfo> src = metadata.readColumns(sybase.connection(), t.sourceSchema(), t.sourceTable());
        List<ColumnInfo> dst = metadata.readColumns(oracle.connection(), t.targetSchema(), t.targetTable());
        List<ResolvedColumn> resolved = resolve(src, dst, t.sourceTable(), mappings);

        System.out.println("\n=== CHECK " + t.sourceSchema() + "." + t.sourceTable() +
                " -> " + t.targetSchema() + "." + t.targetTable() + " ===");

        boolean ok = true;
        for (ResolvedColumn c : resolved) {
            boolean compatible = TypeMapper.compatible(c.sourceType, c.source, c.target);
            String result = compatible ? "OK" : "WARNING/INCOMPATIBLE";
            if (!compatible) ok = false;
            System.out.printf("%-30s %-18s -> %-18s %s%n",
                    c.source.name(), c.sourceType, c.target.jdbcTypeName(), result);
            report.add(t.sourceTable(), t.targetTable(), c.source.name(), c.sourceType,
                    c.target.jdbcTypeName(), result, TypeMapper.expectedConversion(c.sourceType, c.target));
        }
        System.out.println("CHECK RESULT: " + (ok ? "OK" : "REVIEW REQUIRED"));
    }

    public void migrate(TableConfig t, List<ColumnMapping> mappings) throws Exception {
        List<ColumnInfo> src = metadata.readColumns(sybase.connection(), t.sourceSchema(), t.sourceTable());
        List<ColumnInfo> dst = metadata.readColumns(oracle.connection(), t.targetSchema(), t.targetTable());
        List<ResolvedColumn> cols = resolve(src, dst, t.sourceTable(), mappings);

        ColumnInfo id = src.get(0);
        String idName = t.idColumn() == null ? id.name() : t.idColumn();
        ColumnInfo idSource = find(src, idName);
        if (idSource == null) throw new SQLException("ID column not found: " + idName);
        String idType = sourceTypeFor(idSource, mappings, t.sourceTable());

        if (!isKeysetType(idType)) {
            throw new SQLException("ID column " + idName + " has unsupported keyset type " + idType +
                    ". Expected numeric integer type for this implementation.");
        }

        for (ResolvedColumn c : cols) {
            if (!TypeMapper.compatible(c.sourceType, c.source, c.target)) {
                throw new SQLException("Incompatible mapping: " + c.source.name() + " " + c.sourceType +
                        " -> " + c.target.jdbcTypeName());
            }
        }

        String stateKey = t.sourceSchema() + "_" + t.sourceTable();
        String lastState = state.load(stateKey);
        long lastId = lastState == null || lastState.isBlank() ? Long.MIN_VALUE : Long.parseLong(lastState);

        long maxId = readMaxId(t, idName);
        if (maxId == Long.MIN_VALUE) {
            System.out.println("No rows: " + t.sourceTable());
            return;
        }

        String select = buildSelect(t, cols, idName);
        String insert = buildInsert(t, cols);

        System.out.println("\n=== MIGRATE " + t.sourceTable() + " -> " + t.targetTable() + " ===");
        System.out.println("ID column: " + idName + ", max ID: " + maxId);
        System.out.println("Resuming after: " + lastId);

        long total = 0;
        int batchesSinceCommit = 0;

        sybase.setRowCount(rangeSize);

        try (PreparedStatement psSelect = sybase.connection().prepareStatement(
                    select, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement psInsert = oracle.connection().prepareStatement(insert)) {

            psSelect.setFetchSize(fetchSize);

            long cursor = lastId == Long.MIN_VALUE ? Long.MIN_VALUE : lastId;

            while (true) {
                psSelect.clearParameters();
                psSelect.setLong(1, cursor);
                try (ResultSet rs = psSelect.executeQuery()) {
                    long pendingLastId = cursor;
                    int batchCount = 0;
                    boolean gotRows = false;

                    while (rs.next()) {
                        gotRows = true;
                        for (int i = 0; i < cols.size(); i++) {
                            ResolvedColumn c = cols.get(i);
                            ValueBinder.bind(rs, c.sourceIndex, psInsert, i + 1,
                                    c.source, c.target, c.sourceType);
                        }
                        long currentId = rs.getLong(idSource.ordinal());
                        psInsert.addBatch();
                        batchCount++;
                        pendingLastId = currentId;
                        total++;

                        if (batchCount >= batchSize) {
                            psInsert.executeBatch();
                            batchesSinceCommit++;
                            batchCount = 0;

                            if (batchesSinceCommit >= commitEveryBatches) {
                                oracle.connection().commit();
                                state.save(stateKey, String.valueOf(pendingLastId));
                                cursor = pendingLastId;
                                batchesSinceCommit = 0;
                                System.out.printf("  %d rows migrated, lastId=%d%n", total, cursor);
                            }
                        }
                    }

                    if (batchCount > 0) {
                        psInsert.executeBatch();
                        batchesSinceCommit++;
                    }

                    if (gotRows) {
                        oracle.connection().commit();
                        state.save(stateKey, String.valueOf(pendingLastId));
                        cursor = pendingLastId;
                        batchesSinceCommit = 0;
                        System.out.printf("  %d rows migrated, lastId=%d%n", total, cursor);
                    } else {
                        break;
                    }
                }

                if (cursor >= maxId) break;
            }
        } catch (Exception e) {
            try { oracle.connection().rollback(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { sybase.setRowCount(0); } catch (Exception ignored) {}
        }

        System.out.println("DONE: " + total + " rows processed for " + t.sourceTable());
    }

    private long readMaxId(TableConfig t, String idName) throws SQLException {
        String sql = "SELECT MAX(" + q(idName) + ") FROM " + q(t.sourceSchema()) + "." + q(t.sourceTable());
        try (Statement st = sybase.connection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next() || rs.getObject(1) == null) return Long.MIN_VALUE;
            return rs.getLong(1);
        }
    }

    private String buildSelect(TableConfig t, List<ResolvedColumn> cols, String idName) {
        String columnList = cols.stream().map(c -> q(c.source.name())).collect(Collectors.joining(", "));
        return "SELECT " + columnList +
                " FROM " + q(t.sourceSchema()) + "." + q(t.sourceTable()) +
                " WHERE " + q(idName) + " > ?" +
                " ORDER BY " + q(idName);
    }

    private String buildInsert(TableConfig t, List<ResolvedColumn> cols) {
        String names = cols.stream().map(c -> q(c.target.name())).collect(Collectors.joining(", "));
        String params = String.join(", ", Collections.nCopies(cols.size(), "?"));
        return "INSERT INTO " + q(t.targetSchema()) + "." + q(t.targetTable()) +
                " (" + names + ") VALUES (" + params + ")";
    }

    private List<ResolvedColumn> resolve(List<ColumnInfo> src, List<ColumnInfo> dst,
                                         String sourceTable, List<ColumnMapping> mappings) throws SQLException {
        List<ResolvedColumn> result = new ArrayList<>();
        for (ColumnInfo s : src) {
            ColumnMapping m = mappings.stream()
                    .filter(x -> x.sourceTable().equalsIgnoreCase(sourceTable)
                            && x.sourceColumn().equalsIgnoreCase(s.name()))
                    .findFirst().orElse(null);
            String targetName = m != null ? m.targetColumn() : s.name();
            ColumnInfo d = find(dst, targetName);
            if (d == null) throw new SQLException("Destination column not found for " + sourceTable + "." + s.name()
                    + " -> " + targetName);
            result.add(new ResolvedColumn(s, d, s.ordinal(),
                    TypeMapper.effectiveSourceType(s, m == null ? null : m.sourceTypeOverride())));
        }
        return result;
    }

    private String sourceTypeFor(ColumnInfo c, List<ColumnMapping> mappings, String table) {
        return mappings.stream()
                .filter(x -> x.sourceTable().equalsIgnoreCase(table) &&
                        x.sourceColumn().equalsIgnoreCase(c.name()))
                .map(ColumnMapping::sourceTypeOverride).filter(Objects::nonNull).findFirst()
                .orElse(TypeMapper.effectiveSourceType(c, null));
    }

    private ColumnInfo find(List<ColumnInfo> list, String name) {
        return list.stream().filter(x -> x.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private boolean isKeysetType(String t) {
        return switch (t.toLowerCase()) {
            case "int", "smallint", "tinyint", "usmallint", "bigint", "numeric", "decimal" -> true;
            default -> false;
        };
    }

    private String q(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record ResolvedColumn(ColumnInfo source, ColumnInfo target, int sourceIndex, String sourceType) {}
}
