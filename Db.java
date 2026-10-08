package com.example.migrator;

import java.sql.*;
import java.util.Properties;

public final class Db implements AutoCloseable {
    private final Connection connection;

    private Db(Connection connection) { this.connection = connection; }

    public static Db open(String driver, String url, String user, String password) throws Exception {
        Class.forName(driver);
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        Connection c = DriverManager.getConnection(url, props);
        c.setAutoCommit(false);
        return new Db(c);
    }

    public Connection connection() { return connection; }

    public void setRowCount(int n) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("SET ROWCOUNT " + n);
        }
    }

    @Override public void close() {
        try { connection.close(); } catch (Exception ignored) {}
    }
}
