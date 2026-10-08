package com.example.migrator;

import java.sql.Types;
import java.util.Locale;

public final class TypeMapper {
    private TypeMapper() {}

    public static String effectiveSourceType(ColumnInfo c, String override) {
        if (override != null && !override.isBlank()) return override.toLowerCase(Locale.ROOT);
        String n = c.normalizedTypeName();
        // ASE jConnect may expose timestamp through a binary JDBC type.
        if (n.equals("timestamp") || n.equals("rowversion")) return "timestamp";
        return n;
    }

    public static boolean isAseTimestamp(ColumnInfo source, String override) {
        return "timestamp".equals(effectiveSourceType(source, override));
    }

    public static boolean compatible(String sourceType, ColumnInfo src, ColumnInfo dst) {
        String s = sourceType.toLowerCase(Locale.ROOT);
        String d = dst.normalizedTypeName();

        if (s.equals("timestamp")) {
            return d.equals("raw") && dst.size == 8;
        }
        if (s.equals("int") || s.equals("smallint") || s.equals("tinyint") ||
            s.equals("usmallint") || s.equals("bigint")) {
            return isNumericOracle(d);
        }
        if (s.equals("numeric") || s.equals("decimal")) return isNumericOracle(d);
        if (s.equals("varchar")) return d.equals("varchar2") || d.equals("varchar") || d.equals("char");
        if (s.equals("char")) return d.equals("char") || d.equals("varchar2") || d.equals("varchar");
        if (s.equals("datetime") || s.equals("smalldatetime")) return d.startsWith("timestamp") || d.equals("date");
        if (s.equals("date")) return d.equals("date") || d.startsWith("timestamp");
        if (s.equals("time")) return d.startsWith("timestamp") || d.equals("interval day to second") || d.equals("time");
        if (s.equals("text")) return d.equals("clob") || d.equals("varchar2");
        if (s.equals("image")) return d.equals("blob") || d.equals("raw");
        if (s.equals("float") || s.equals("real")) return isNumericOracle(d) || d.equals("binary_double") || d.equals("binary_float");
        if (s.equals("bit")) return isNumericOracle(d) || d.equals("char") || d.equals("varchar2");
        return src.jdbcType() == dst.jdbcType() || isBroadJdbcCompatible(src.jdbcType(), dst.jdbcType());
    }

    private static boolean isNumericOracle(String d) {
        return d.equals("number") || d.startsWith("number(") || d.equals("binary_double") ||
               d.equals("binary_float") || d.equals("integer") || d.equals("float");
    }

    private static boolean isBroadJdbcCompatible(int s, int d) {
        if (isNumericJdbc(s) && isNumericJdbc(d)) return true;
        if ((s == Types.VARCHAR || s == Types.LONGVARCHAR || s == Types.CHAR) &&
            (d == Types.VARCHAR || d == Types.LONGVARCHAR || d == Types.CHAR)) return true;
        return false;
    }

    private static boolean isNumericJdbc(int t) {
        return t == Types.INTEGER || t == Types.SMALLINT || t == Types.TINYINT ||
               t == Types.BIGINT || t == Types.NUMERIC || t == Types.DECIMAL ||
               t == Types.FLOAT || t == Types.REAL || t == Types.DOUBLE;
    }

    public static String expectedConversion(String sourceType, ColumnInfo target) {
        if ("timestamp".equalsIgnoreCase(sourceType)) return "ASE timestamp 8 bytes -> Oracle RAW(8), binary copy";
        return sourceType + " -> " + target.jdbcTypeName();
    }
}
