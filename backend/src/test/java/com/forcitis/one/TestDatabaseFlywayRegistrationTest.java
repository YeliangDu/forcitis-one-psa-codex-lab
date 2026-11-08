package com.forcitis.one;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;

/** #26: captures registration only; no datasource, container, Spring context or network. */
class TestDatabaseFlywayRegistrationTest {
    @Test
    void flywayUsesExactlyTheFinalCloneConnectionWithoutBypassProperties() {
        // Entirely synthetic values, never real credentials. Assertions do not print values.
        TestDatabase clone = new TestDatabase(
                "jdbc:postgresql://127.0.0.1:15432/synthetic_class_clone?connectTimeout=10&socketTimeout=120",
                "synthetic_fixture_user", "synthetic_fixture_password")
                .withUrlParam("currentSchema", "platform_approval")
                .withUrlParam("ApplicationName", "synthetic_flyway_witness");
        Map<String, Supplier<Object>> properties = new LinkedHashMap<>();
        DynamicPropertyRegistry capture = (name, value) -> {
            assertTrue(properties.putIfAbsent(name, value) == null, "property must be registered once");
        };
        clone.register(capture);

        for (String suffix : new String[] {"url", "username", "password"}) {
            String flywaySuffix = suffix.equals("username") ? "user" : suffix;
            String datasource = "spring.datasource." + suffix;
            String flyway = "spring.flyway." + flywaySuffix;
            assertTrue(properties.containsKey(datasource), "datasource property missing");
            assertTrue(properties.containsKey(flyway), "matching Flyway connection property missing");
            assertTrue(properties.get(datasource).get().equals(properties.get(flyway).get()),
                    "Flyway must use exactly the final clone connection");
        }
        assertTrue(clone.url().equals(properties.get("spring.datasource.url").get()),
                "final URL must preserve cloned database, currentSchema and URL parameters");
        assertTrue(clone.username().equals(properties.get("spring.datasource.username").get()),
                "synthetic username must be preserved");
        assertTrue(clone.password().equals(properties.get("spring.datasource.password").get()),
                "synthetic password must be preserved");
        assertTrue(properties.keySet().equals(java.util.Set.of(
                "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "spring.flyway.url", "spring.flyway.user", "spring.flyway.password")),
                "registration must add only connection settings, never migration/validation bypasses");
    }
}
