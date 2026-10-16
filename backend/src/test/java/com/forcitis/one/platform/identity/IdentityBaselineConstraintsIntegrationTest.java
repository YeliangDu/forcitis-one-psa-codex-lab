package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** DATA-REPAIR-identity: the database itself refuses what the baseline constraints rule out, and accepts what is legal. */
class IdentityBaselineConstraintsIntegrationTest extends IdentityIntegrationTest {

    private void insertRole(String code, String kind) {
        jdbc.sql("insert into platform_identity.roles (role_id, code, name, kind) values (uuidv7(), :code, :code, :kind)")
                .param("code", code).param("kind", kind).update();
    }

    private void refused(Runnable write, String constraint) {
        assertThatThrownBy(write::run).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-07 the reserved code super_admin, in any letter case, belongs to SYSTEM roles only")
    void id07ReservedCode() {
        refused(() -> insertRole("Super_Admin", "CUSTOM"), "ck_roles_reserved_code");
        refused(() -> insertRole("super_admin", "CUSTOM"), "ck_roles_reserved_code");
        assertThat(jdbc.sql("select count(*) from platform_identity.roles where kind = 'SYSTEM' and code in ('super_admin', 'employee', 'process_administrator', 'org_data_admin', 'business_admin', 'security_auditor')")
                .query(Long.class).single()).as("the seeded system roles pass").isEqualTo(6);
        insertRole("Role.editor-1_a", "CUSTOM");
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-07 a role code follows the same format as Role.CODE")
    void id07CodeFormat() {
        for (String bad : new String[] {"", " ", "-lead", "a b", "a/b", "角色", "a".repeat(65)}) {
            refused(() -> insertRole(bad, "CUSTOM"), "ck_roles_code");
        }
        insertRole("a".repeat(64), "CUSTOM");
    }
}
