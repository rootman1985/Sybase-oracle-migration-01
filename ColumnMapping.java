package com.example.migrator;

public record ColumnMapping(
        String sourceTable,
        String sourceColumn,
        String targetColumn,
        String sourceTypeOverride) {}
