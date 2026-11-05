package com.forcitis.one.platform.integration.infrastructure.transport;

import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Release pipeline step: with the profile "migrate" the process runs the Flyway migrations with the migration account and exits, before any
 * application context exists, so no web server, job, process engine or event listener starts. Exit code 0 when the migrations succeed, 1 when not.
 * "migrate" alone is refused: the environment profile (dev, prod or, on a developer's own machine, local) decides the database transport requirement and must be named.
 * The settings are Spring Boot's own spring.flyway.* binding (FlywayProperties); the connection is spring.flyway.url / user / password, falling back to
 * spring.datasource.*. The running service has spring.flyway.enabled=false.
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
            LOG.error("MIGRATE-ONLY FAILED: {}", failure.getMessage(), failure);
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    static MigrateResult migrate(ConfigurableEnvironment environment) {
        if (!environment.acceptsProfiles(Profiles.of("dev | prod | local"))) {
            throw new IllegalStateException("the profile migrate needs the environment profile too (migrate,dev, migrate,prod or migrate,local): it decides the database transport requirement");
        }
        FlywayProperties properties = Binder.get(environment).bindOrCreate("spring.flyway", FlywayProperties.class);
        String url = first(properties.getUrl(), environment.getProperty("spring.datasource.url"), "spring.flyway.url or spring.datasource.url");
        String user = first(properties.getUser(), environment.getProperty("spring.datasource.username"), "spring.flyway.user or spring.datasource.username");
        String password = first(properties.getPassword(), environment.getProperty("spring.datasource.password"), "spring.flyway.password or spring.datasource.password");
        requireTransport(environment, url);
        return configure(properties, url, user, password).load().migrate();
    }

    static FluentConfiguration configure(FlywayProperties properties, String url, String user, String password) {
        FluentConfiguration flyway = Flyway.configure().dataSource(url, user, password);
        PropertyMapper map = PropertyMapper.get();
        map.from(properties::getLocations).to(locations -> flyway.locations(locations.toArray(String[]::new)));
        map.from(properties::getCallbackLocations).to(locations -> flyway.callbackLocations(locations.toArray(String[]::new)));
        map.from(properties::isFailOnMissingLocations).to(flyway::failOnMissingLocations);
        map.from(properties::getConnectRetries).to(flyway::connectRetries);
        map.from(properties::getLockRetryCount).to(flyway::lockRetryCount);
        map.from(properties::getDefaultSchema).to(flyway::defaultSchema);
        map.from(properties::getSchemas).to(schemas -> flyway.schemas(schemas.toArray(String[]::new)));
        map.from(properties::isCreateSchemas).to(flyway::createSchemas);
        map.from(properties::getTable).to(flyway::table);
        map.from(properties::getTablespace).to(flyway::tablespace);
        map.from(properties::getBaselineDescription).to(flyway::baselineDescription);
        map.from(properties::getBaselineVersion).to(flyway::baselineVersion);
        map.from(properties::getInstalledBy).to(flyway::installedBy);
        map.from(properties::getPlaceholders).to(flyway::placeholders);
        map.from(properties::getPlaceholderPrefix).to(flyway::placeholderPrefix);
        map.from(properties::getPlaceholderSuffix).to(flyway::placeholderSuffix);
        map.from(properties::getPlaceholderSeparator).to(flyway::placeholderSeparator);
        map.from(properties::isPlaceholderReplacement).to(flyway::placeholderReplacement);
        map.from(properties::getSqlMigrationPrefix).to(flyway::sqlMigrationPrefix);
        map.from(properties::getSqlMigrationSuffixes).to(suffixes -> flyway.sqlMigrationSuffixes(suffixes.toArray(String[]::new)));
        map.from(properties::getSqlMigrationSeparator).to(flyway::sqlMigrationSeparator);
        map.from(properties::getRepeatableSqlMigrationPrefix).to(flyway::repeatableSqlMigrationPrefix);
        map.from(properties::getTarget).to(flyway::target);
        map.from(properties::isBaselineOnMigrate).to(flyway::baselineOnMigrate);
        map.from(properties::isCleanDisabled).to(flyway::cleanDisabled);
        map.from(properties::isGroup).to(flyway::group);
        map.from(properties::isMixed).to(flyway::mixed);
        map.from(properties::isOutOfOrder).to(flyway::outOfOrder);
        map.from(properties::isSkipDefaultCallbacks).to(flyway::skipDefaultCallbacks);
        map.from(properties::isSkipDefaultResolvers).to(flyway::skipDefaultResolvers);
        map.from(properties::isValidateMigrationNaming).to(flyway::validateMigrationNaming);
        map.from(properties::isValidateOnMigrate).to(flyway::validateOnMigrate);
        map.from(properties::isExecuteInTransaction).to(flyway::executeInTransaction);
        map.from(properties::getIgnoreMigrationPatterns).to(patterns -> flyway.ignoreMigrationPatterns(patterns.toArray(String[]::new)));
        map.from(properties::getJdbcProperties).to(flyway::jdbcProperties);
        return flyway;
    }

    private static void requireTransport(ConfigurableEnvironment environment, String url) {
        DatabaseTransportRequirement required = DatabaseTransportRequirement.forProfiles(Set.of(environment.getActiveProfiles()));
        List<String> problems = DatabaseTransportCheck.problems(required, key -> key.equals("spring.datasource.url") ? url : environment.getProperty(key));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("database transport requirement " + required + " is not met: " + String.join("; ", problems));
        }
    }

    private static String first(String preferred, String fallback, String names) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        throw new IllegalStateException("none of " + names + " is set: the migration account's connection is required");
    }
}
