package com.example.migrator;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class CsvConfigLoader {
    private CsvConfigLoader() {}

    public static List<TableConfig> loadTables(Path path) throws IOException {
        List<TableConfig> result = new ArrayList<>();
        for (String raw : Files.readAllLines(path)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] a = split(line);
            if (a.length < 6) throw new IOException("Invalid tables.csv line: " + line);
            result.add(new TableConfig(a[0].trim(), a[1].trim(), a[2].trim(), a[3].trim(),
                    emptyToNull(a[4]), Boolean.parseBoolean(a[5].trim())));
        }
        return result;
    }

    public static List<ColumnMapping> loadMappings(Path path) throws IOException {
        List<ColumnMapping> result = new ArrayList<>();
        if (!Files.exists(path)) return result;
        for (String raw : Files.readAllLines(path)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] a = split(line);
            if (a.length < 3) throw new IOException("Invalid column-mappings.csv line: " + line);
            result.add(new ColumnMapping(a[0].trim(), a[1].trim(), a[2].trim(),
                    a.length >= 4 ? emptyToNull(a[3]) : null));
        }
        return result;
    }

    private static String emptyToNull(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }

    // Simple CSV: no quoted commas required by the supplied configuration format.
    private static String[] split(String s) { return s.split(",", -1); }
}
