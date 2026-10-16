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

    private void insertEmergencyAdmin(long person, String accountName, int failedAttempts, long totpStep) {
        createAccount(person);
        jdbc.sql("""
                insert into platform_identity.emergency_admins (user_id, account_name, password_hash, totp_secret_encrypted, failed_attempts, last_totp_step)
                values (:user, :name, 'h', 's', :failed, :step)
                """).param("user", com.forcitis.one.fixture.doubles.TestAccounts.account(person).value()).param("name", accountName)
                .param("failed", failedAttempts).param("step", totpStep).update();
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-08 the emergency account name follows the registration format")
    void id08AccountName() {
        for (String bad : new String[] {"Admin1", "ab", "1admin", "has space", "a".repeat(33)}) {
            refused(() -> insertEmergencyAdmin(101, bad, 0, 0), "ck_emergency_admins_account_name");
        }
        insertEmergencyAdmin(102, "ops.admin-1_a", 0, 0);
        insertEmergencyAdmin(103, "a" + "b".repeat(31), 0, 0);
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-08 the failure counter and the TOTP step are never negative")
    void id08Counters() {
        refused(() -> insertEmergencyAdmin(104, "counter-neg", -1, 0), "ck_emergency_admins_failed_attempts");
        refused(() -> insertEmergencyAdmin(105, "step-neg", 0, -1), "ck_emergency_admins_last_totp_step");
        insertEmergencyAdmin(106, "counter-ok", 5, 123456789L);
    }
}
