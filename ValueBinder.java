package com.example.migrator;

import java.io.*;
import java.math.BigDecimal;
import java.sql.*;

public final class ValueBinder {
    private ValueBinder() {}

    public static void bind(ResultSet rs, int sourceIndex, PreparedStatement ps, int targetIndex,
                            ColumnInfo source, ColumnInfo target, String sourceType) throws Exception {
        if (rs.getObject(sourceIndex) == null) {
            ps.setNull(targetIndex, target.jdbcType());
            return;
        }

        String s = sourceType.toLowerCase();

        // CRITICAL: Sybase ASE timestamp is an 8-byte binary/version value, not a date.
        if ("timestamp".equals(s)) {
            byte[] bytes = rs.getBytes(sourceIndex);
            if (bytes == null) {
                ps.setNull(targetIndex, target.jdbcType());
            } else {
                if (bytes.length != 8) {
                    throw new SQLException("ASE timestamp expected 8 bytes, got " + bytes.length +
                            " for column " + source.name());
                }
                ps.setBytes(targetIndex, bytes);
            }
            return;
        }

        switch (s) {
            case "int" -> ps.setInt(targetIndex, rs.getInt(sourceIndex));
            case "smallint" -> ps.setShort(targetIndex, rs.getShort(sourceIndex));
            case "tinyint", "bit", "usmallint" -> {
                Object o = rs.getObject(sourceIndex);
                if (o instanceof Number n) ps.setInt(targetIndex, n.intValue());
                else ps.setString(targetIndex, o.toString());
            }
            case "bigint" -> ps.setLong(targetIndex, rs.getLong(sourceIndex));
            case "numeric", "decimal" -> ps.setBigDecimal(targetIndex, rs.getBigDecimal(sourceIndex));
            case "float", "real" -> ps.setDouble(targetIndex, rs.getDouble(sourceIndex));
            case "varchar", "char" -> ps.setString(targetIndex, rs.getString(sourceIndex));
            case "text" -> {
                Reader r = rs.getCharacterStream(sourceIndex);
                if (r != null) ps.setCharacterStream(targetIndex, r);
                else ps.setNull(targetIndex, target.jdbcType());
            }
            case "image" -> {
                InputStream in = rs.getBinaryStream(sourceIndex);
                if (in != null) ps.setBinaryStream(targetIndex, in);
                else ps.setNull(targetIndex, target.jdbcType());
            }
            case "datetime", "smalldatetime" -> ps.setTimestamp(targetIndex, rs.getTimestamp(sourceIndex));
            case "date" -> ps.setDate(targetIndex, rs.getDate(sourceIndex));
            case "time" -> ps.setTime(targetIndex, rs.getTime(sourceIndex));
            default -> bindGeneric(rs, sourceIndex, ps, targetIndex, target);
        }
    }

    private static void bindGeneric(ResultSet rs, int si, PreparedStatement ps, int ti, ColumnInfo target)
            throws SQLException {
        Object value = rs.getObject(si);
        if (value == null) {
            ps.setNull(ti, target.jdbcType());
        } else if (value instanceof byte[] b) {
            ps.setBytes(ti, b);
        } else if (value instanceof BigDecimal bd) {
            ps.setBigDecimal(ti, bd);
        } else if (value instanceof Number n) {
            ps.setObject(ti, n);
        } else if (value instanceof java.sql.Date d) {
            ps.setDate(ti, d);
        } else if (value instanceof java.sql.Timestamp t) {
            ps.setTimestamp(ti, t);
        } else {
            ps.setObject(ti, value);
        }
    }
}
