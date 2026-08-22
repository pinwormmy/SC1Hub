package com.sc1hub;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MariaDbConnectorIntegrationTest {

    @Test
    void connectsWithMariaDbConnectorWhenCompatibilityDatabaseIsConfigured() throws Exception {
        String url = System.getenv("SC1HUB_DB_COMPAT_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "Set SC1HUB_DB_COMPAT_URL to run the optional database compatibility test");

        String username = environmentOrDefault("SC1HUB_DB_COMPAT_USERNAME", "root");
        String password = environmentOrDefault("SC1HUB_DB_COMPAT_PASSWORD", "");
        Class.forName("org.mariadb.jdbc.Driver");

        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            assertTrue(connection.getMetaData().getDriverName().contains("MariaDB Connector/J"));
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }
}
