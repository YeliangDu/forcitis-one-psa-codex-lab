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
    @DisplayName("the sync and the short-name refresh are off unless switched on; the connections they read live in platform.integration")
    void safeDefaults() throws IOException {
        Properties p = load();

        assertThat(p.getProperty("forcitis.organization.workforce.sync.enabled")).isEqualTo("${WORKFORCE_SYNC_ENABLED:false}");
        assertThat(p.getProperty("forcitis.organization.workforce.short-name.enabled")).isEqualTo("${WORKFORCE_SHORT_NAME_ENABLED:false}");
        assertThat(Files.readString(CONFIG)).doesNotContain("jdbc:").doesNotContain("password").doesNotContain("username");
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
                {"SYNC_RUN_EXECUTE", Permission.Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE},
                {"SEPARATION_HOLD_APPROVE", Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE},
                {"LEGAL_ENTITY_UPDATE", Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE}}) {
            Field field = local.getDeclaredField(pair[0]);
            field.setAccessible(true);
            assertThat(field.get(null)).as(pair[0]).isEqualTo(pair[1]);
        }
        assertThat(local.getDeclaredFields()).filteredOn(f -> f.getType() == String.class).hasSize(5);
    }
}
