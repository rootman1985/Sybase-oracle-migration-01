package com.example.migrator;

public record ColumnInfo(
        String name,
        int jdbcType,
        String jdbcTypeName,
        int size,
        int scale,
        int ordinal,
        boolean nullable) {

    public String normalizedTypeName() {
        return jdbcTypeName == null ? "" : jdbcTypeName.trim().toLowerCase();
    }
}
