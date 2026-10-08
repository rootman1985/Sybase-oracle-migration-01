package com.example.migrator;

import java.sql.*;
import java.util.*;

public final class MetadataService {
    public List<ColumnInfo> readColumns(Connection c, String schema, String table) throws SQLException {
        List<ColumnInfo> result = new ArrayList<>();
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getColumns(null, schema, table, null)) {
            while (rs.next()) {
                result.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getInt("DATA_TYPE"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("COLUMN_SIZE"),
                        rs.getInt("DECIMAL_DIGITS"),
                        rs.getInt("ORDINAL_POSITION"),
                        "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"))
                ));
            }
        }
        if (result.isEmpty()) {
            // Some DB/driver combinations are case-sensitive for metadata.
            try (ResultSet rs = md.getColumns(null, schema == null ? null : schema.toUpperCase(),
                    table == null ? null : table.toUpperCase(), null)) {
                while (rs.next()) {
                    result.add(new ColumnInfo(
                            rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME"),
                            rs.getInt("COLUMN_SIZE"), rs.getInt("DECIMAL_DIGITS"),
                            rs.getInt("ORDINAL_POSITION"), "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"))
                    ));
                }
            }
        }
        result.sort(Comparator.comparingInt(ColumnInfo::ordinal));
        if (result.isEmpty()) throw new SQLException("No columns found for " + schema + "." + table);
        return result;
    }
}
