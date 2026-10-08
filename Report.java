package com.example.migrator;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class Report {
    private final Path path;
    public Report(Path dir) throws IOException {
        Files.createDirectories(dir);
        path = dir.resolve("migration-report.csv");
        if (!Files.exists(path)) Files.writeString(path,
                "timestamp,sourceTable,targetTable,column,sourceType,targetType,result,message\n");
    }

    public synchronized void add(String sourceTable, String targetTable, String column,
                                  String sourceType, String targetType, String result, String message) {
        try {
            String line = String.join(",",
                    csv(Instant.now().toString()), csv(sourceTable), csv(targetTable), csv(column),
                    csv(sourceType), csv(targetType), csv(result), csv(message)) + "\n";
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Cannot write report: " + e.getMessage());
        }
    }

    private String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
