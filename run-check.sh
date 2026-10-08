#!/bin/sh
mvn -q clean package
java -cp "target/classes:lib/*" com.example.migrator.MigrationApplication CHECK
