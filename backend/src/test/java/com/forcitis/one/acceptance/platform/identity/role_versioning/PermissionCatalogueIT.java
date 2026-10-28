package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * ADR-0034 decision 1 item 3 (写入时码必须存在且未停用；运行时读到未知或已停用的码：只拒绝该码并告警（fail-closed），不抛异常，不影响同一角色的其他码)
 * and guard rule 8 (权限目录：测试库中全部角色的当前版本的码都存在且未停用；运行时读到未知码：该码被忽略并告警，同一角色的其他码照常生效，不抛异常);
 * ADR-0034-IMPL card row 0034-G8-04 and card section 6 (未知或已停用权限码只忽略该码并告警). The snapshot side is in PermissionCatalogueTest.
 */
@Testcontainers
class PermissionCatalogueIT extends RoleVersioningSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    static final String CURRENT_USER = "/api/v1/platform/sessions:current";
    static final String KNOWN = "organization.workforce:org-unit:read";
    static final String UNKNOWN = "platform.unknown:ghost:read";

    @Test
    @DisplayName("0034-G8-04 测试库全部角色的当前版本（至少含六个系统角色）的权限码都在 Permission 目录中且未停用")
    void currentVersionsHoldOnlyLiveCatalogueCodes() throws Exception {
        Map<String, Permission> catalogue = java.util.Arrays.stream(Permission.values()).collect(Collectors.toMap(Permission::code, Function.identity()));

        List<VersionRow> current = versions("select * from role_versions where effective_to_at is null order by role_id");

        assertThat(current.stream().map(VersionRow::roleId).map(UUID::toString).toList()).as("the system roles have current versions after start-up")
                .contains("019a0000-0000-7000-8000-000000000101", "019a0000-0000-7000-8000-000000000102", "019a0000-0000-7000-8000-000000000103",
                        "019a0000-0000-7000-8000-000000000104", "019a0000-0000-7000-8000-000000000105", "019a0000-0000-7000-8000-000000000106");
        List<String> problems = new ArrayList<>();
        for (VersionRow version : current) {
            for (String code : version.permissionCodes()) {
                Permission permission = catalogue.get(code);
                if (permission == null) {
                    problems.add(version.roleId() + " holds unknown code " + code);
                } else if (PermissionCatalogueTest.isDeprecated(permission)) {
                    problems.add(version.roleId() + " holds deprecated code " + code);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("0034-G8-04 运行时读到未知权限码：持有人照常登录、读当前会话 200；未知码被忽略不进权限，同一角色版本的其他码照常生效；写一条带该码的 ALERT，不抛异常")
    void anUnknownCodeIsIgnoredAndAlertedTheOthersStillCount() throws Exception {
        SignedIn administrator = roleAdministrator();
        String code = "rv_unknown_" + UUID.randomUUID().toString().substring(0, 8);
        String name = "Role versioning " + code;
        UUID role = createdRole(administrator, code, new Definition(name, true, "SELF", List.of()));
        assertThat(versionsOf(role)).as("creating the role wrote version 1").hasSize(1);
        VersionRow created = versionsOf(role).getLast();
        Instant at = NOW.plus(Duration.ofMinutes(5));
        OffsetDateTime moment = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
        execute("update role_versions set effective_to_at = ?, updated_at = ? where role_version_id = ?", moment, moment, created.roleVersionId());
        execute("""
                insert into role_versions (role_version_id, role_id, version_number, effective_from_at, effective_to_at, role_name, role_status,
                       access_scope_kind, permission_codes, change_channel_kind, recorded_by, change_reason, authorization_request_id,
                       declaration_revision_number, version_audit_entry_id, closed_by, closure_reason, closure_audit_entry_id, created_at, updated_at)
                values (?, ?, 2, ?, null, ?, 'ENABLED', 'SELF', ?::text[], 'MANUAL', ?, 'acceptance: a code the catalogue does not know', null,
                        null, ?, null, null, null, ?, ?)""",
                UUID.randomUUID(), role, moment, name, "{" + KNOWN + "," + UNKNOWN + "}", administrator.userId(), UUID.randomUUID(), moment, moment);
        clock.set(at.plus(Duration.ofMinutes(1)));
        int mark = logMark();
        try {
            UUID unit = newOrgUnitId();
            orgUnit(unit, null, null);
            UUID holderEmployee = newEmployeeId();
            employee(holderEmployee, unit, null);
            grantByOperations(holderEmployee, code);

            SignedIn holder = signIn(holderEmployee);
            MockHttpServletResponse current = mvc.perform(get(CURRENT_USER).with(session(holder))).andReturn().getResponse();

            assertThat(current.getStatus()).as(current.getContentAsString()).isEqualTo(200);
            List<String> permissions = JsonPath.read(current.getContentAsString(), "$.permissionCodes");
            assertThat(permissions).as("the other code of the same role version still counts").contains(KNOWN);
            assertThat(permissions).as("the unknown code is ignored (fail-closed)").doesNotContain(UNKNOWN);
            assertThat(alertsSince(mark)).as("an ALERT names the unknown code").anySatisfy(alert -> assertThat(alert).contains(UNKNOWN));
        } finally {
            // Isolation: role_versions is append-only, so the case cannot delete its rows; it closes the version that holds the unknown code, so
            // that no other case of this class (the whole-database scan in currentVersionsHoldOnlyLiveCatalogueCodes) sees it as a current version.
            OffsetDateTime closedAt = OffsetDateTime.ofInstant(clock.instant().plus(Duration.ofMinutes(1)), ZoneOffset.UTC);
            execute("update role_versions set effective_to_at = ?, updated_at = ? where role_id = ? and effective_to_at is null", closedAt, closedAt, role);
        }
    }
}
