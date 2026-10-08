package com.example.migrator;

import java.nio.file.*;
import java.util.*;

public final class MigrationApplication {
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "CHECK" : args[0].toUpperCase(Locale.ROOT);
        Path base = Paths.get(".");
        Config cfg = new Config(base.resolve("src/main/resources/application.properties"));
        List<TableConfig> tables = CsvConfigLoader.loadTables(base.resolve("src/main/resources/tables.csv"));
        List<ColumnMapping> mappings = CsvConfigLoader.loadMappings(base.resolve("src/main/resources/column-mappings.csv"));

        try (Db sybase = Db.open(cfg.get("sybase.driver"), cfg.get("sybase.url"),
                cfg.get("sybase.user"), cfg.get("sybase.password"));
             Db oracle = Db.open(cfg.get("oracle.driver"), cfg.get("oracle.url"),
                cfg.get("oracle.user"), cfg.get("oracle.password"))) {

            StateStore state = new StateStore(Paths.get(cfg.get("migration.stateDirectory", "state")));
            Report report = new Report(Paths.get(cfg.get("migration.logDirectory", "logs")));
            MigrationService service = new MigrationService(
                    sybase, oracle, new MetadataService(), state, report,
                    cfg.getInt("migration.batchSize", 1000),
                    cfg.getInt("migration.rangeSize", 10000),
                    cfg.getInt("migration.fetchSize", 1000),
                    cfg.getInt("migration.commitEveryBatches", 10)
            );

            for (TableConfig t : tables) {
                if (!t.enabled()) continue;
                try {
                    if (mode.equals("CHECK")) {
                        service.check(t, mappings);
                    } else if (mode.equals("MIGRATE")) {
                        service.migrate(t, mappings);
                    } else {
                        throw new IllegalArgumentException("Use CHECK or MIGRATE");
                    }
                } catch (Exception e) {
                    System.err.println("ERROR " + t.sourceTable() + ": " + e.getMessage());
                    e.printStackTrace(System.err);
                    if (mode.equals("MIGRATE")) {
                        // Continue with next table. The failed table's Oracle transaction is rolled back.
                    }
                }
            }
        }
    }
}
