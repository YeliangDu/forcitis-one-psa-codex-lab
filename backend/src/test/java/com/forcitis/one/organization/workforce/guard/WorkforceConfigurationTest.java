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
    @DisplayName("ADR-0069 s8 no short-name refresh is configured, and the workforce properties hold no connection or credential setting")
    void noShortNameJobAndNoConnections() throws IOException {
        Properties p = load();

        assertThat(p.stringPropertyNames()).as(GuardMessage.of("ADR-0069 s8", "a short-name refresh setting is back in the workforce properties", "short_name is read from org_unit_versions; do not schedule a refresh from the legacy system")).noneMatch(k -> k.contains("short-name"));
        assertThat(Files.readString(CONFIG)).as(GuardMessage.of("CS-1.3 / CS-6.5", "the workforce properties hold a connection or credential setting", "keep connections and credentials in platform_integration.properties as environment placeholders; workforce reads through platform.integration")).doesNotContain("jdbc:").doesNotContain("password").doesNotContain("username");
    }

    @Test
    @DisplayName("the permission codes repeated in the web layer equal platform.identity's Permission.Codes (module graph forbids the import)")
    void permissionCodesMatchTheIdentityCatalogue() throws Exception {
        Class<?> local = Class.forName("com.forcitis.one.organization.workforce.web.WorkforcePermissions");
        for (String[] pair : new String[][] {
                {"ORG_UNIT_READ", Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ},
                {"ORG_RECONCILIATION_EXECUTE", Permission.Codes.ORGANIZATION_WORKFORCE_ORG_RECONCILIATION_EXECUTE},
                {"SEPARATION_HOLD_APPROVE", Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE},
                {"PERSONNEL_COMMAND_CREATE", Permission.Codes.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_CREATE},
                {"EMPLOYEE_READ", Permission.Codes.ORGANIZATION_WORKFORCE_EMPLOYEE_READ},
                {"LEGAL_ENTITY_UPDATE", Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE},
                {"LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE", Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE},
                {"LEGAL_ENTITY_ATTRIBUTION_APPROVE", Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_APPROVE},
                {"ORG_STRUCTURE_COMMAND_CREATE", Permission.Codes.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_CREATE}}) {
            Field field = local.getDeclaredField(pair[0]);
            field.setAccessible(true);
            assertThat(field.get(null)).as(GuardMessage.of("org-and-personnel-sync", "WorkforcePermissions." + pair[0] + " differs from the code in platform.identity Permission.Codes", "set the constant in organization.workforce.web.WorkforcePermissions to the same string as the Permission.Codes constant")).isEqualTo(pair[1]);
        }
        assertThat(local.getDeclaredFields()).filteredOn(f -> f.getType() == String.class).as(GuardMessage.of("org-and-personnel-sync", "WorkforcePermissions has another number of permission codes than this test checks", "add the new code to the pairs of this test and compare it with Permission.Codes, then update the count")).hasSize(9);
    }

    @Test
    @DisplayName("the permission codes the use cases ask for themselves (data scope, release, correction) equal platform.identity's Permission.Codes")
    void useCasePermissionCodesMatchTheIdentityCatalogue() throws Exception {
        String application = "com.forcitis.one.organization.workforce.application.";
        for (String[] triple : new String[][] {
                {"OrgUnitVisibility", "ORG_UNIT_READ", Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ},
                {"SeparationHoldService", "PERMISSION", Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE},
                {"PersonnelCommandService", "PERMISSION", Permission.Codes.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_CREATE},
                {"CorrectLegalEntityAttribution", "PERMISSION", Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE}}) {
            Field field = Class.forName(application + triple[0]).getDeclaredField(triple[1]);
            field.setAccessible(true);
            assertThat(field.get(null)).as(GuardMessage.of("org-and-personnel-sync", triple[0] + "." + triple[1] + " differs from the code in platform.identity Permission.Codes", "set the constant in organization.workforce.application." + triple[0] + " to the same string as the Permission.Codes constant")).isEqualTo(triple[2]);
        }
    }
}
