package com.forcitis.one;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.DynamicPropertyRegistry;

/** #26 infrastructure fixture witness, not a module's business-schema test. Hosted only. */
class TestDatabaseFlywayChecksumIT {
    // Exactly one new class-owned clone. TestPostgres migrates its shared template;
    // this test changes ONLY the returned clone, never template/shared state.
    static final TestDatabase clone = TestPostgres.database(TestDatabaseFlywayChecksumIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @Test
    void normalBootFlywayInitializationRejectsOneCorruptVersionedChecksumOnSameClone() throws Exception {
        Map<String, Supplier<Object>> captured = new LinkedHashMap<>();
        DynamicPropertyRegistry registry = (name, supplier) -> {
            assertTrue(captured.putIfAbsent(name, supplier) == null, "duplicate fixture registration");
        };
        clone.register(registry);
        for (String key : List.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "spring.flyway.url", "spring.flyway.user", "spring.flyway.password")) {
            assertTrue(captured.containsKey(key), "connection fixture must explicitly register datasource and Flyway properties");
        }
        assertTrue(clone.url().equals(captured.get("spring.flyway.url").get()), "Flyway must point at this exact clone");
        assertTrue(clone.username().equals(captured.get("spring.flyway.user").get()), "Flyway user must match clone");
        assertTrue(clone.password().equals(captured.get("spring.flyway.password").get()), "Flyway credentials must match clone");

        // Reuse checked-in Flyway locations/default schema/schema list unchanged.
        Properties settings = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertTrue(input != null, "application migration settings must exist");
            settings.load(input);
        }
        String schema = settings.getProperty("spring.flyway.default-schema");
        String table = settings.getProperty("spring.flyway.table", "flyway_schema_history");
        assertTrue(schema != null && schema.matches("[a-z_][a-z0-9_]*"), "fixed migration schema required");
        assertTrue(table.matches("[a-z_][a-z0-9_]*"), "fixed migration history identifier required");
        String history = "\"" + schema + "\".\"" + table + "\"";
        int rank;
        int original;
        String version;
        List<String> before;
        try (Connection connection = connect()) {
            assertTrue(connection.getCatalog().equals(databaseName(clone.url())), "history mutation must use only class clone");
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "select installed_rank, version, checksum from " + history
                    + " where version is not null and checksum is not null and success = true order by installed_rank limit 1")) {
                assertTrue(rows.next(), "one existing versioned non-null checksum required");
                rank = rows.getInt(1);
                version = rows.getString(2);
                original = rows.getInt(3);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "update " + history + " set checksum = ? where installed_rank = ? and checksum = ?")) {
                statement.setInt(1, original ^ 1);
                statement.setInt(2, rank);
                statement.setInt(3, original);
                assertTrue(statement.executeUpdate() == 1, "mutate exactly one existing clone checksum");
            }
            before = historySnapshot(connection, history);
        }

        List<String> properties = new ArrayList<>();
        settings.forEach((key, value) -> {
            if (key.toString().startsWith("spring.flyway.")) properties.add(key + "=" + value);
        });
        // Explicitly retain validation, not a repair/ignore/disabled-migration workaround.
        properties.add("spring.flyway.enabled=true");
        properties.add("spring.flyway.validate-on-migrate=true");
        captured.forEach((key, supplier) -> properties.add(key + "=" + supplier.get()));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withPropertyValues(properties.toArray(String[]::new))
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertTrue(failure != null, "normal Spring Flyway initializer must refuse corrupt history");
                    FlywayValidateException mismatch = null;
                    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                        if (cause instanceof FlywayValidateException found) { mismatch = found; break; }
                    }
                    assertTrue(mismatch != null, "expected Flyway validation failure, not transport or unrelated startup error");
                    String message = mismatch.getMessage();
                    assertTrue(message != null && message.contains("Migration checksum mismatch for migration version " + version),
                            "refusal must identify the corrupted version's checksum mismatch");
                });

        try (Connection connection = connect()) {
            assertTrue(before.equals(historySnapshot(connection, history)), "startup must not repair, replace or append history");
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(clone.url(), clone.username(), clone.password());
    }

    private static String databaseName(String url) {
        String path = url.substring(url.indexOf('/', "jdbc:postgresql://".length()) + 1);
        return path.split("\\?", 2)[0];
    }

    private static List<String> historySnapshot(Connection connection, String history) throws Exception {
        List<String> result = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "select installed_rank, version, checksum, success from " + history + " order by installed_rank")) {
            while (rows.next()) result.add(rows.getString(1) + ":" + rows.getString(2) + ":" + rows.getString(3) + ":" + rows.getBoolean(4));
        }
        return result;
    }
}
