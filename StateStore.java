package com.example.migrator;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class StateStore {
    private final Path dir;
    public StateStore(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
    }

    public String load(String key) throws IOException {
        Path p = file(key);
        return Files.exists(p) ? Files.readString(p).trim() : null;
    }

    public void save(String key, String value) throws IOException {
        Path p = file(key);
        Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
        Files.writeString(tmp, value, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Path file(String key) {
        return dir.resolve(key.replaceAll("[^A-Za-z0-9_.-]", "_") + ".state");
    }
}
