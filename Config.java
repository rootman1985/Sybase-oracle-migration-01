package com.example.migrator;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Config {
    private final Properties p = new Properties();

    public Config(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path)) { p.load(r); }
    }

    public String get(String key) { return p.getProperty(key); }
    public String get(String key, String def) { return p.getProperty(key, def); }
    public int getInt(String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim()); }
        catch (Exception e) { return def; }
    }
    public boolean getBoolean(String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
    }
}
