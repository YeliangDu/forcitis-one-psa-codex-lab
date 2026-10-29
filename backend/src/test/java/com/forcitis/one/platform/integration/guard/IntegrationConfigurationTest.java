package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.GuardMessage;
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
                assertThat(p.getProperty(key)).as(GuardMessage.of("CS-6.5", "the property " + key + " holds a value instead of an environment placeholder", "write it as ${ENV_NAME:} and give the value only through the environment")).matches("\\$\\{[A-Z0-9_]+:}");
            }
        }
        assertThat(Files.readString(CONFIG)).as(GuardMessage.of("CS-6.5", "the integration properties contain a JDBC URL", "use a ${ENV_NAME:} placeholder for the jdbc-url")).doesNotContain("jdbc:sqlserver").doesNotContain("jdbc:postgresql");
    }

    @Test
    @DisplayName("both external reads are off unless switched on; once on, the checks always run (ADR37-1: no switch, see ReadOnlyCheckSwitchGuardTest)")
    void safeDefaults() throws IOException {
        Properties p = load();

        assertThat(p.getProperty(SEEYON + "views.enabled")).as(GuardMessage.of("ADR37-1", "the Seeyon read is not off by default", "set it to ${INTEGRATION_SEEYON_VIEWS_ENABLED:false}")).isEqualTo("${INTEGRATION_SEEYON_VIEWS_ENABLED:false}");
        assertThat(p.getProperty(LEGACY + "enabled")).as(GuardMessage.of("ADR37-1", "the old-system read is not off by default", "set it to ${INTEGRATION_LEGACY_ENABLED:false}")).isEqualTo("${INTEGRATION_LEGACY_ENABLED:false}");
        assertThat(p.stringPropertyNames()).as(GuardMessage.of("ADR37-1", "the keys of the two read-only connections differ from the allowed ones", "keep only enabled, jdbc-url, username, password (and view-name for legacy); a read-only check switch is not allowed"))
                .filteredOn(key -> key.startsWith(SEEYON + "views.") || (key.startsWith(LEGACY) && !key.startsWith(LEGACY + "inbox.")))
                .containsExactlyInAnyOrder(SEEYON + "views.enabled", SEEYON + "views.jdbc-url", SEEYON + "views.username", SEEYON + "views.password",
                        LEGACY + "enabled", LEGACY + "jdbc-url", LEGACY + "username", LEGACY + "password", LEGACY + "view-name");
        assertThat(p.stringPropertyNames()).as(GuardMessage.of("ADR37-1", "a key of the removed old to-do connection (legacy.inbox.*) ships", "delete the legacy.inbox.* keys"))
                .filteredOn(key -> key.startsWith(LEGACY + "inbox."))
                .isEmpty();
    }

    @Test
    @DisplayName("RULE-356 / M-18 relationship type ids and enum ids are configuration items without a shipped default: missing means the start-up check refuses")
    void instanceIdsAreConfiguration() throws IOException {
        Properties p = load();

        assertThat(p.stringPropertyNames()).as(GuardMessage.of("RULE-356 / M-18", "a relationship type id, enum id or root path ships with a default", "remove the key from platform_integration.properties; set it only through the environment so a missing value makes the start-up check refuse")).doesNotContain(SEEYON + "views.head-relationship-type-id",
                SEEYON + "views.regional-receivables-manager-relationship-type-id",
                SEEYON + "views.delivery-unit-yes-enum-id", SEEYON + "views.fee-earner-yes-enum-id", SEEYON + "views.root-path");
    }

    @Test
    @DisplayName("the event publication registry lives in platform_integration, created by Flyway and not by the framework")
    void eventRegistryUsesTheIntegrationSchema() throws IOException {
        Properties p = load();

        assertThat(p.getProperty("spring.modulith.events.jdbc.schema")).as(GuardMessage.of("CS-3.1", "the event publication registry is not in the platform_integration schema", "set spring.modulith.events.jdbc.schema=platform_integration")).isEqualTo("platform_integration");
        assertThat(p.getProperty("spring.modulith.events.jdbc.schema-initialization.enabled")).as(GuardMessage.of("CS-3.1", "the framework creates the event publication table", "set spring.modulith.events.jdbc.schema-initialization.enabled=false and create the table by Flyway")).isEqualTo("false");
        assertThat(Files.readString(Path.of("src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql")))
                .as(GuardMessage.of("CS-3.1", "the platform_integration baseline does not create event_publication", "create platform_integration.event_publication in the baseline migration"))
                .contains("CREATE TABLE platform_integration.event_publication");
    }
}
