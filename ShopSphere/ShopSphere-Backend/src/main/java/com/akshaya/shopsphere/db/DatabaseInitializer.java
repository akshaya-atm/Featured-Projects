package com.akshaya.shopsphere.db;

import org.flywaydb.core.Flyway;

public class DatabaseInitializer {

    // Set SHOPSPHERE_RESET_DB=true to wipe and reseed on startup; otherwise only pending
    // migrations are applied and existing data is never dropped.
    private static final boolean RESET_DB_ON_START = "true".equalsIgnoreCase(System.getenv("SHOPSPHERE_RESET_DB"));

    // Guards against running more than once per JVM lifetime, since multiple servlets could
    // otherwise race to initialize the schema concurrently.
    private static volatile boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        initializeDatabase();
    }

    private static void initializeDatabase() {
        // Reuses the same JDBC URL/username/password as the app's runtime connection pool, so
        // this can't silently target the wrong database/role.
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(DatabaseConnection.getJdbcUrl(), DatabaseConnection.getUsername(), DatabaseConnection.getPassword())
                    .cleanDisabled(!RESET_DB_ON_START)
                    .baselineOnMigrate(true)
                    .load();

            if (RESET_DB_ON_START) {
                flyway.clean();
                flyway.migrate();
                System.out.println("Flyway Clean & Reseed completed successfully. [SHOPSPHERE_RESET_DB=true]");
            } else {
                flyway.migrate();
                System.out.println("Flyway migration completed successfully. Existing data preserved.");
            }
        } catch (Exception e) {
            System.err.println("[DatabaseInitializer] FAILED to initialize/reset the database -- " +
                    "the app will keep starting, but the schema may be missing or out of date.");
            e.printStackTrace();
        }
    }
}
