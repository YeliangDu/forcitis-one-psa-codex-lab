package com.forcitis.one.organization.workforce.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.identity.api.Permission;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

/** Configuration hygiene: no credentials in the repository, safe defaults, the agreed periods, and the permission codes. */
class WorkforceConfigurationTest {

    static final Path CONFIG = Path.of("src/main/resources/config/organization_workforce.properties");

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
    @DisplayName("both external sources are off unless switched on; with A8 on, the start-up check is on by default")
    void safeDefaults() throws IOException {
        Properties p = load();

        assertThat(p.getProperty("forcitis.organization.workforce.a8.enabled")).isEqualTo("${WORKFORCE_A8_ENABLED:false}");
        assertThat(p.getProperty("forcitis.organization.workforce.legacy.enabled")).isEqualTo("${WORKFORCE_LEGACY_ENABLED:false}");
        assertThat(p.getProperty("forcitis.organization.workforce.a8.validate-on-startup")).isEqualTo("${WORKFORCE_A8_VALIDATE_ON_STARTUP:true}");
        assertThat(p.getProperty("forcitis.organization.workforce.legacy.validate-on-startup")).isEqualTo("${WORKFORCE_LEGACY_VALIDATE_ON_STARTUP:true}");
    }

    @Test
    @DisplayName("RULE-356 relationship type ids and enum ids are configuration items (with the known values of today's A8 instance)")
    void a8InstanceIdsAreConfiguration() throws IOException {
        Properties p = load();

        assertThat(p.stringPropertyNames()).contains("forcitis.organization.workforce.a8.head-relationship-type-id",
                "forcitis.organization.workforce.a8.regional-receivables-manager-relationship-type-id",
                "forcitis.organization.workforce.a8.delivery-unit-yes-enum-id", "forcitis.organization.workforce.a8.fee-earner-yes-enum-id");
    }

    @Test
    @DisplayName("the sync runs every 5 minutes, the short names once a day, the shrink guard allows a 20 % drop")
    void periods() throws IOException {
        Properties p = load();
        CronExpression sync = CronExpression.parse(p.getProperty("forcitis.organization.workforce.sync.cron"));
        CronExpression names = CronExpression.parse(p.getProperty("forcitis.organization.workforce.short-name.cron"));

        LocalDateTime first = sync.next(LocalDateTime.of(2026, 10, 14, 9, 0, 1));
        LocalDateTime second = sync.next(first);
        LocalDateTime third = sync.next(second);
        assertThat(java.time.Duration.between(first, second)).isEqualTo(java.time.Duration.ofMinutes(5));
        assertThat(java.time.Duration.between(second, third)).isEqualTo(java.time.Duration.ofMinutes(5));
        LocalDateTime day1 = names.next(LocalDateTime.of(2026, 10, 14, 0, 0));
        assertThat(java.time.Duration.between(day1, names.next(day1))).isEqualTo(java.time.Duration.ofHours(24));
        assertThat(p.getProperty("forcitis.organization.workforce.sync.max-drop-percent")).isEqualTo("20");
    }

    @Test
    @DisplayName("the permission codes repeated in the web layer equal platform.identity's Permission.Codes (module graph forbids the import)")
    void permissionCodesMatchTheIdentityCatalogue() throws Exception {
        Class<?> local = Class.forName("com.forcitis.one.organization.workforce.web.WorkforcePermissions");
        for (String[] pair : new String[][] {
                {"ORG_UNIT_READ", Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ},
                {"SYNC_RUN_READ", Permission.Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_READ},
                {"SYNC_RUN_EXECUTE", Permission.Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE}}) {
            Field field = local.getDeclaredField(pair[0]);
            field.setAccessible(true);
            assertThat(field.get(null)).as(pair[0]).isEqualTo(pair[1]);
        }
        assertThat(local.getDeclaredFields()).filteredOn(f -> f.getType() == String.class).hasSize(3);
    }
}
