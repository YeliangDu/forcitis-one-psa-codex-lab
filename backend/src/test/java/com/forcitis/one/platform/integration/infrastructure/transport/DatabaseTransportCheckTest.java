package com.forcitis.one.platform.integration.infrastructure.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTransportCheckTest {
    @TempDir
    Path directory;

    private Map<String, String> configuration(String primaryUrl) {
        Map<String, String> properties = new HashMap<>();
        properties.put("spring.datasource.url", primaryUrl);
        return properties;
    }

    private Path caFile() throws IOException {
        return Files.writeString(directory.resolve("ca.pem"), "not a secret, only has to exist");
    }

    @Test
    @DisplayName("N-3 production: verify-full with a readable CA file passes; require, a missing CA path and a missing CA file fail")
    void productionNeedsVerifyFullAndTheCaFile() throws IOException {
        String ca = caFile().toString();
        var required = DatabaseTransportRequirement.VERIFIED_SERVER;

        assertThat(DatabaseTransportCheck.problems(required, configuration("jdbc:postgresql://db/one?sslmode=verify-full&sslrootcert=" + ca)::get)).isEmpty();
        assertThat(DatabaseTransportCheck.problems(required, configuration("jdbc:postgresql://db/one?sslmode=require&sslrootcert=" + ca)::get))
                .singleElement().asString().contains("production needs verify-full");
        assertThat(DatabaseTransportCheck.problems(required, configuration("jdbc:postgresql://db/one?sslmode=verify-full")::get))
                .singleElement().asString().contains("no sslrootcert");
        assertThat(DatabaseTransportCheck.problems(required, configuration("jdbc:postgresql://db/one?sslmode=verify-full&sslrootcert=" + directory.resolve("gone.pem"))::get))
                .singleElement().asString().contains("not a readable file");
        assertThat(DatabaseTransportCheck.problems(required, configuration("jdbc:postgresql://db/one")::get))
                .singleElement().asString().contains("sslmode=prefer");
    }

    @Test
    @DisplayName("N-3 dev: require, verify-ca and verify-full pass; disable, allow, prefer and no sslmode fail")
    void devNeedsAtLeastRequire() {
        var required = DatabaseTransportRequirement.ENCRYPTED;

        assertThat(DatabaseTransportCheck.problems(required, configuration("jdbc:postgresql://db/one?sslmode=require")::get)).isEmpty();
        assertThat(DatabaseTransportCheck.problems(required, configuration("jdbc:postgresql://db/one?sslmode=verify-ca")::get)).isEmpty();
        for (String weak : new String[] {"?sslmode=disable", "?sslmode=allow", "?sslmode=prefer", ""}) {
            assertThat(DatabaseTransportCheck.problems(required, configuration("jdbc:postgresql://db/one" + weak)::get)).as(weak).hasSize(1);
        }
    }

    @Test
    @DisplayName("N-3 the A8, legacy short-name and legacy inbox connections follow the same rule when they are switched on, and are not looked at when off")
    void externalReadOnlySourcesAreChecked() throws IOException {
        String ca = caFile().toString();
        Map<String, String> properties = configuration("jdbc:postgresql://db/one?sslmode=verify-full&sslrootcert=" + ca);
        properties.put("forcitis.platform.integration.seeyon.views.jdbc-url", "jdbc:sqlserver://a8:1433;databaseName=a8;encrypt=true;trustServerCertificate=true");
        properties.put("forcitis.platform.integration.legacy.jdbc-url", "jdbc:sqlserver://old:1433;databaseName=old");
        properties.put("forcitis.platform.integration.legacy.inbox.url", "jdbc:sqlserver://old:1433;databaseName=old;encrypt=true;trustServerCertificate=false;trustStore=" + ca);

        assertThat(DatabaseTransportCheck.problems(DatabaseTransportRequirement.VERIFIED_SERVER, properties::get)).as("views and legacy switched off").isEmpty();

        properties.put("forcitis.platform.integration.seeyon.views.enabled", "true");
        properties.put("forcitis.platform.integration.legacy.enabled", "true");
        assertThat(DatabaseTransportCheck.problems(DatabaseTransportRequirement.VERIFIED_SERVER, properties::get)).hasSize(2)
                .anyMatch(p -> p.contains("A8 views") && p.contains("trustServerCertificate=true"))
                .anyMatch(p -> p.contains("legacy short names") && p.contains("encrypt=false"));
        assertThat(DatabaseTransportCheck.problems(DatabaseTransportRequirement.ENCRYPTED, properties::get)).hasSize(1)
                .anyMatch(p -> p.contains("legacy short names"));
    }

    @Test
    @DisplayName("N-3 the legacy inbox connection is checked under its integration key; the former approval key is no longer read")
    void legacyInboxIsCheckedUnderItsIntegrationKey() throws IOException {
        String ca = caFile().toString();
        Map<String, String> properties = configuration("jdbc:postgresql://db/one?sslmode=verify-full&sslrootcert=" + ca);
        properties.put("forcitis.platform.integration.legacy.inbox.url", "jdbc:sqlserver://old:1433;databaseName=old;encrypt=true;trustServerCertificate=true");
        properties.put("forcitis.platform.approval.legacy.url", "jdbc:sqlserver://old:1433;databaseName=old");

        assertThat(DatabaseTransportCheck.problems(DatabaseTransportRequirement.VERIFIED_SERVER, properties::get)).singleElement().asString()
                .contains("legacy inbox").contains("forcitis.platform.integration.legacy.inbox.url").contains("trustServerCertificate=true");
    }

    @Test
    @DisplayName("N-3 profiles decide the requirement: prod verifies, dev encrypts, anything else (tests, local) is not checked; prod wins over dev")
    void profilesDecideTheRequirement() {
        assertThat(DatabaseTransportRequirement.forProfiles(Set.of("prod"))).isEqualTo(DatabaseTransportRequirement.VERIFIED_SERVER);
        assertThat(DatabaseTransportRequirement.forProfiles(Set.of("dev", "prod"))).isEqualTo(DatabaseTransportRequirement.VERIFIED_SERVER);
        assertThat(DatabaseTransportRequirement.forProfiles(Set.of("dev"))).isEqualTo(DatabaseTransportRequirement.ENCRYPTED);
        assertThat(DatabaseTransportRequirement.forProfiles(Set.of())).isEqualTo(DatabaseTransportRequirement.NONE);
        assertThat(DatabaseTransportCheck.problems(DatabaseTransportRequirement.NONE, configuration("jdbc:postgresql://db/one")::get)).isEmpty();
    }

    @Test
    @DisplayName("N-3 a production start without a primary database URL fails instead of falling back to a default")
    void missingPrimaryUrlFails() {
        assertThat(DatabaseTransportCheck.problems(DatabaseTransportRequirement.VERIFIED_SERVER, new HashMap<String, String>()::get))
                .singleElement().asString().contains("not configured");
    }

    @Test
    @DisplayName("N-3 the application context refuses to start in production with sslmode=require, and starts in dev with it")
    void contextRefusesToStart() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(DatabaseTransportConfiguration.class)
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://db/one?sslmode=require");

        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("refusing to start"));
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context).hasNotFailed());
    }
}
