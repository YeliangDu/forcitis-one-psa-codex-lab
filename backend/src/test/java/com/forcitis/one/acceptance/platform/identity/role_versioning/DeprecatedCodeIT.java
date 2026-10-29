package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ADR-0034 decision 1 item 3 (运行时读到未知或已停用的码：该码被忽略并告警，不抛异常，不影响同一角色的其他码) and guard rule 8; ADR-0034-IMPL card
 * row 0034-G8-04 (运行时读到未知或已停用码，只忽略该码并告警，同一角色其他码照常生效、不抛异常). The unknown-code branch is PermissionCatalogueIT;
 * this is the deprecated-code branch.
 * <p>
 * Today the catalogue has no deprecated code (card 3.2), so one is made for the length of the case: the role is written while both codes are
 * live (the only way to write it, decision 1 item 3), then the catalogue marks one of them deprecated, as a later release would, and the
 * holder signs in. The mark is set on the private field behind {@code Permission.deprecated()} and always put back. This couples the test to
 * the field's name (the contract only names the method); if the implementation renames it, the test fails with a message that says so.
 */
class DeprecatedCodeIT extends RoleVersioningSupport {

    static final TestDatabase postgres = TestPostgres.database(DeprecatedCodeIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String CURRENT_USER = "/api/v1/platform/sessions:current";
    static final String STAYS = "organization.workforce:org-unit:read";
    static final String RETIRED = "organization.workforce:sync-run:read";

    private static Permission permissionOf(String code) {
        for (Permission permission : Permission.values()) {
            if (permission.code().equals(code)) {
                return permission;
            }
        }
        throw new AssertionError("the catalogue has no " + code);
    }

    private static Field markerField() {
        try {
            Field field = Permission.class.getDeclaredField("deprecated");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Permission has no field 'deprecated' behind deprecated(): the test seam of DeprecatedCodeIT (see its javadoc) needs adapting", e);
        }
    }

    private static void mark(Permission permission, boolean deprecated) {
        try {
            markerField().setBoolean(permission, deprecated);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot set the deprecated mark of " + permission, e);
        }
        assertThat(permission.deprecated()).as("deprecated() reads the mark just set").isEqualTo(deprecated);
    }

    @Test
    @DisplayName("0034-G8-04 运行时读到已停用的权限码：持有人照常登录、读当前会话 200；已停用的码被忽略不进权限，同一角色版本的其他码照常生效；写一条带该码的 ALERT，不抛异常")
    void aDeprecatedCodeIsIgnoredAndAlertedTheOthersStillCount() throws Exception {
        SignedIn administrator = roleAdministrator();
        String code = "rv_deprecated_" + UUID.randomUUID().toString().substring(0, 8);
        UUID role = createdRole(administrator, code, new Definition("Role versioning " + code, true, "SELF", List.of(STAYS, RETIRED)));
        assertThat(versionsOf(role).getLast().permissionCodes()).as("written while both codes were live").containsExactlyInAnyOrder(STAYS, RETIRED);
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID holderEmployee = newEmployeeId();
        employee(holderEmployee, unit, null);
        grantByOperations(holderEmployee, code);
        Permission retired = permissionOf(RETIRED);
        assertThat(retired.deprecated()).as("the fixture starts from a live code").isFalse();

        mark(retired, true);
        try {
            int mark = logMark();
            SignedIn holder = signIn(holderEmployee);
            MockHttpServletResponse current = mvc.perform(get(CURRENT_USER).with(session(holder))).andReturn().getResponse();

            assertThat(current.getStatus()).as(current.getContentAsString()).isEqualTo(200);
            List<String> permissions = JsonPath.read(current.getContentAsString(), "$.permissionCodes");
            assertThat(permissions).as("the other code of the same role version still counts").contains(STAYS);
            assertThat(permissions).as("the deprecated code is ignored (fail-closed)").doesNotContain(RETIRED);
            assertThat(alertsSince(mark)).as("an ALERT names the deprecated code").anySatisfy(alert -> assertThat(alert).contains(RETIRED));
            assertThat(versionsOf(role).getLast().permissionCodes()).as("the stored version is not rewritten by reading").containsExactlyInAnyOrder(STAYS, RETIRED);
        } finally {
            mark(retired, false);
        }
    }
}
