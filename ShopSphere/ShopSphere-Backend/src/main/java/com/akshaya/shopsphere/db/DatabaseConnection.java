package com.akshaya.shopsphere.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {
    // Connection details come from environment variables so real credentials never live in
    // source control; falls back to local-dev defaults when unset.
    private static final String URL = envOrDefault("SHOPSPHERE_DB_URL", "jdbc:postgresql://localhost:5432/shopsphere");
    private static final String USERNAME = envOrDefault("SHOPSPHERE_DB_USER", "shopsphere");
    private static final String PASSWORD = envOrDefault("SHOPSPHERE_DB_PASSWORD", "shopsphere");
    private static final HikariDataSource dataSource;
    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if(value == null || value.isEmpty()){
            System.out.println("[WARNING] Environment variable " + name + " is not set — using default: " + defaultValue);            return defaultValue;
        } return value;
    }

    static {
        try {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("org.postgresql.Driver");
            config.setJdbcUrl(URL);
            config.setUsername(USERNAME);
            config.setPassword(PASSWORD);
            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            System.err.println("[FATAL] Could not connect to the database at " + URL +
                    " as user '" + USERNAME + "'. Is Postgres running?");
            throw e;
        }
    }

    public static Connection getConnection() throws SQLException {
       return dataSource.getConnection();

    }

    // Exposed so DatabaseInitializer's Flyway setup reuses the same connection details as the
    // app's runtime connection pool.
    public static String getJdbcUrl() {
        return URL;
    }

    public static String getUsername() {
        return USERNAME;
    }

    public static String getPassword() {
        return PASSWORD;
    }
}
