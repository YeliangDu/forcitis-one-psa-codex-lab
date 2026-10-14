package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntegrationConfigurationTest {

    static final Path CONFIG = Path.of("src/main/resources/config/platform_integration.properties");
    static final String SEEYON = "forcitis.platform.integration.seeyon.";
    static final String LEGACY = "forcitis.platform.integration.legacy.";

    private static Properties load() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG)) {
            p.load(in);
        }
        return p;
    }

    @Test
    @DisplayName("credentials and connection settings are environment placeholders, never values")
    void credentialsAreEnvironmentPlaceholders() throws IOException {
        Properties p = load();
        for (String key : p.stringPropertyNames()) {
            if (key.endsWith(".password") || key.endsWith(".username") || key.endsWith(".jdbc-url")) {
                assertThat(p.getProperty(key)).as(key).matches("\\$\\{[A-Z0-9_]+:}");
            }
        }
        assertThat(Files.readString(CONFIG)).doesNotContain("jdbc:sqlserver").doesNotContain("jdbc:postgresql");
    }

    @Test
    @DisplayName("both external reads are off unless switched on; once on, the start-up checks are on by default")
    void safeDefaults() throws IOException {
        Properties p = load();

        assertThat(p.getProperty(SEEYON + "views.enabled")).isEqualTo("${INTEGRATION_SEEYON_VIEWS_ENABLED:false}");
        assertThat(p.getProperty(LEGACY + "enabled")).isEqualTo("${INTEGRATION_LEGACY_ENABLED:false}");
        assertThat(p.getProperty(SEEYON + "views.validate-on-startup")).isEqualTo("${INTEGRATION_SEEYON_VALIDATE_ON_STARTUP:true}");
        assertThat(p.getProperty(LEGACY + "validate-on-startup")).isEqualTo("${INTEGRATION_LEGACY_VALIDATE_ON_STARTUP:true}");
    }

    @Test
    @DisplayName("RULE-356 / M-18 relationship type ids and enum ids are configuration items without a shipped default: missing means the start-up check refuses")
    void instanceIdsAreConfiguration() throws IOException {
        Properties p = load();

        assertThat(p.stringPropertyNames()).doesNotContain(SEEYON + "views.head-relationship-type-id",
                SEEYON + "views.regional-receivables-manager-relationship-type-id",
                SEEYON + "views.delivery-unit-yes-enum-id", SEEYON + "views.fee-earner-yes-enum-id", SEEYON + "views.root-path");
    }

    @Test
    @DisplayName("the event publication registry lives in platform_integration, created by Flyway and not by the framework")
    void eventRegistryUsesTheIntegrationSchema() throws IOException {
        Properties p = load();

        assertThat(p.getProperty("spring.modulith.events.jdbc.schema")).isEqualTo("platform_integration");
        assertThat(p.getProperty("spring.modulith.events.jdbc.schema-initialization.enabled")).isEqualTo("false");
        assertThat(Files.readString(Path.of("src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_create_event_publication.sql")))
                .contains("CREATE TABLE platform_integration.event_publication");
    }
}
