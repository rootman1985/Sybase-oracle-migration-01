# Sybase ASE -> Oracle Migrator (Java 17)

## Goal

Generic migration engine for many Sybase ASE tables to Oracle, including tables with
millions/tens of millions of rows.

The engine:
- reads source/target table mappings from `tables.csv`;
- reads column metadata from both databases;
- compares source and target columns;
- automatically maps common ASE -> Oracle types;
- treats Sybase ASE `timestamp` as an 8-byte binary value, NOT as a date;
- writes ASE timestamp bytes to Oracle `RAW(8)` with `setBytes()`;
- migrates using keyset/range pagination on the first column (normally the identity ID);
- uses `SET ROWCOUNT` on the Sybase connection to limit each result set;
- inserts with JDBC batches and commits periodically;
- persists the last successfully processed key for resume;
- writes errors and a validation report.

## Important: Sybase ASE timestamp

In ASE, `timestamp` is an 8-byte binary/version value. It is NOT a date/time.
The migration therefore does:

    Sybase timestamp -> ResultSet.getBytes() -> Oracle PreparedStatement.setBytes()

If the target is RAW(8), the 8 bytes are copied without interpretation.

For an ASE column whose JDBC metadata says BINARY/VARBINARY but whose declared type name is
`timestamp`, the metadata reader uses `TYPE_NAME` and detects it as ASE TIMESTAMP.
You can also force it with `column-mappings.csv` using `sourceTypeOverride=timestamp`.

## ID pagination

For a table with first/ID column `IdLog` and last processed key `12345`, the reader uses:

    SET ROWCOUNT 10000

then:

    SELECT col1, col2, ...
    FROM schema.table
    WHERE IdLog > ?
    ORDER BY IdLog

After each row is inserted, the state is advanced only after the Oracle batch is
successfully committed. The next run resumes from that key.

This is intentionally keyset pagination rather than OFFSET pagination.

## Recommended procedure

1. Put the Sybase jConnect JAR in `lib/` (for example jconn4.jar or the version supplied by your company).
2. Configure `application.properties`.
3. Configure `tables.csv`.
4. Optionally configure `column-mappings.csv`.
5. Run CHECK first:
       java -cp "target/classes;lib/*;..." com.example.migrator.MigrationApplication CHECK
6. Review logs/migration-report.csv.
7. Run:
       java -cp "target/classes;lib/*;..." com.example.migrator.MigrationApplication MIGRATE

With the assembly JAR:
       java -jar target/sybase-oracle-migrator-2.0.0-jar-with-dependencies.jar CHECK
       java -jar target/sybase-oracle-migrator-2.0.0-jar-with-dependencies.jar MIGRATE

Note: the assembly contains Oracle/SLF4J dependencies but not the Sybase jConnect driver.
Keep the Sybase JAR in `lib/` and use the classpath launch if the driver is not packaged.

## Type mapping

Typical mappings:
- int/smallint/tinyint/usmallint/bigint -> Oracle NUMBER
- numeric/decimal -> Oracle NUMBER
- varchar -> VARCHAR2
- char -> CHAR
- datetime/smalldatetime -> TIMESTAMP
- date -> DATE
- time -> TIME/TIMESTAMP depending on target metadata
- text -> CLOB
- image -> BLOB
- float -> BINARY_DOUBLE/NUMBER depending on target metadata
- ASE timestamp -> RAW(8) when target is RAW(8)

The program validates actual source and target metadata before migration.

## Security

Do not commit passwords. For production, use environment variables or an external
properties file and restrict file permissions.
