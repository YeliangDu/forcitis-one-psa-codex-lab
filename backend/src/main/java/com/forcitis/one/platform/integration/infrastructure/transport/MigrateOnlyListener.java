package com.forcitis.one.platform.integration.infrastructure.transport;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Release pipeline step: with the profile "migrate" the process runs the Flyway migrations with the migration account and exits, before any
 * application context exists, so no web server, job, process engine or event listener starts. Exit code 0 when the migrations succeed, 1 when not.
 * The connection is spring.flyway.url / user / password, falling back to spring.datasource.*; the running service has spring.flyway.enabled=false.
 */
final class MigrateOnlyListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(MigrateOnlyListener.class);
    static final String PROFILE = "migrate";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of(PROFILE))) {
            return;
        }
        int exitCode;
        try {
            MigrateResult result = migrate(environment);
            LOG.info("MIGRATE-ONLY OK: {} migration(s) applied, schema version {}", result.migrationsExecuted, result.targetSchemaVersion);
            exitCode = 0;
        } catch (RuntimeException failure) {
            LOG.error("MIGRATE-ONLY FAILED: {}", failure.getMessage());
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    static MigrateResult migrate(ConfigurableEnvironment environment) {
        String url = first(environment, "spring.flyway.url", "spring.datasource.url");
        String user = first(environment, "spring.flyway.user", "spring.datasource.username");
        String password = first(environment, "spring.flyway.password", "spring.datasource.password");
        requireTransport(environment, url);
        Flyway flyway = Flyway.configure().dataSource(url, user, password)
                .locations(list(environment, "spring.flyway.locations", "classpath:db/migration"))
                .defaultSchema(required(environment, "spring.flyway.default-schema"))
                .schemas(list(environment, "spring.flyway.schemas", null))
                .createSchemas(Boolean.parseBoolean(environment.getProperty("spring.flyway.create-schemas", "false")))
                .load();
        return flyway.migrate();
    }

    private static void requireTransport(ConfigurableEnvironment environment, String url) {
        DatabaseTransportRequirement required = DatabaseTransportRequirement.forProfiles(Set.of(environment.getActiveProfiles()));
        List<String> problems = DatabaseTransportCheck.problems(required, key -> key.equals("spring.datasource.url") ? url : environment.getProperty(key));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("database transport requirement " + required + " is not met: " + String.join("; ", problems));
        }
    }

    private static String first(ConfigurableEnvironment environment, String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException("none of " + Arrays.toString(keys) + " is set: the migration account's connection is required");
    }

    private static String required(ConfigurableEnvironment environment, String key) {
        return first(environment, key);
    }

    private static String[] list(ConfigurableEnvironment environment, String key, String fallback) {
        String value = environment.getProperty(key, fallback);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }
}
