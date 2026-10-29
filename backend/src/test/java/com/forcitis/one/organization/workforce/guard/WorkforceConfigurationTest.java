package com.forcitis.one.organization.workforce.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.GuardMessage;
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

        assertThat(p.getProperty("forcitis.organization.workforce.sync.enabled")).as(GuardMessage.of("org-and-personnel-sync", "the workforce sync is not off by default", "set forcitis.organization.workforce.sync.enabled to ${WORKFORCE_SYNC_ENABLED:false}")).isEqualTo("${WORKFORCE_SYNC_ENABLED:false}");
        assertThat(p.getProperty("forcitis.organization.workforce.short-name.enabled")).as(GuardMessage.of("org-and-personnel-sync", "the short-name refresh is not off by default", "set forcitis.organization.workforce.short-name.enabled to ${WORKFORCE_SHORT_NAME_ENABLED:false}")).isEqualTo("${WORKFORCE_SHORT_NAME_ENABLED:false}");
        assertThat(Files.readString(CONFIG)).as(GuardMessage.of("CS-1.3 / CS-6.5", "the workforce properties hold a connection or credential setting", "keep connections and credentials in platform_integration.properties as environment placeholders; workforce reads through platform.integration")).doesNotContain("jdbc:").doesNotContain("password").doesNotContain("username");
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
        assertThat(java.time.Duration.between(first, second)).as(GuardMessage.of("org-and-personnel-sync", "the sync cron does not run every 5 minutes", "set forcitis.organization.workforce.sync.cron to a 5-minute period")).isEqualTo(java.time.Duration.ofMinutes(5));
        assertThat(java.time.Duration.between(second, third)).as(GuardMessage.of("org-and-personnel-sync", "the sync cron does not run every 5 minutes", "set forcitis.organization.workforce.sync.cron to a 5-minute period")).isEqualTo(java.time.Duration.ofMinutes(5));
        LocalDateTime day1 = names.next(LocalDateTime.of(2026, 10, 14, 0, 0));
        assertThat(java.time.Duration.between(day1, names.next(day1))).as(GuardMessage.of("org-and-personnel-sync", "the short-name cron does not run once a day", "set forcitis.organization.workforce.short-name.cron to a daily period")).isEqualTo(java.time.Duration.ofHours(24));
        assertThat(p.getProperty("forcitis.organization.workforce.sync.max-drop-percent")).as(GuardMessage.of("org-and-personnel-sync", "the shrink guard does not allow a 20 % drop", "set forcitis.organization.workforce.sync.max-drop-percent to 20, or change the rule in the process document first")).isEqualTo("20");
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
                {"LEGAL_ENTITY_UPDATE", Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE},
                {"LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE", Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE}}) {
            Field field = local.getDeclaredField(pair[0]);
            field.setAccessible(true);
            assertThat(field.get(null)).as(GuardMessage.of("org-and-personnel-sync", "WorkforcePermissions." + pair[0] + " differs from the code in platform.identity Permission.Codes", "set the constant in organization.workforce.web.WorkforcePermissions to the same string as the Permission.Codes constant")).isEqualTo(pair[1]);
        }
        assertThat(local.getDeclaredFields()).filteredOn(f -> f.getType() == String.class).as(GuardMessage.of("org-and-personnel-sync", "WorkforcePermissions has another number of permission codes than this test checks", "add the new code to the pairs of this test and compare it with Permission.Codes, then update the count")).hasSize(6);
    }

    @Test
    @DisplayName("the permission codes the use cases ask for themselves (data scope, release, correction) equal platform.identity's Permission.Codes")
    void useCasePermissionCodesMatchTheIdentityCatalogue() throws Exception {
        String application = "com.forcitis.one.organization.workforce.application.";
        for (String[] triple : new String[][] {
                {"OrgUnitVisibility", "ORG_UNIT_READ", Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ},
                {"ReleaseSyncRun", "SYNC_RUN_EXECUTE", Permission.Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE},
                {"ReleaseSyncRun", "SEPARATION_HOLD_APPROVE", Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE},
                {"CorrectLegalEntityAttribution", "PERMISSION", Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE}}) {
            Field field = Class.forName(application + triple[0]).getDeclaredField(triple[1]);
            field.setAccessible(true);
            assertThat(field.get(null)).as(GuardMessage.of("org-and-personnel-sync", triple[0] + "." + triple[1] + " differs from the code in platform.identity Permission.Codes", "set the constant in organization.workforce.application." + triple[0] + " to the same string as the Permission.Codes constant")).isEqualTo(triple[2]);
        }
    }
}
