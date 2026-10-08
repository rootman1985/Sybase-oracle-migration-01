#!/bin/sh
mvn -q package
java -cp "target/classes:lib/*" com.example.migrator.MigrationApplication MIGRATE
