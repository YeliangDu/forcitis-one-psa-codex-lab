package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** DATA-REPAIR-identity: the database itself refuses what the baseline constraints rule out, and accepts what is legal. */
class IdentityBaselineConstraintsIntegrationTest extends IdentityIntegrationTest {

    private void insertRole(String code, String kind) {
        jdbc.sql("insert into platform_identity.roles (role_id, code, kind) values (uuidv7(), :code, :kind)")
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
                .query(Long.class).single()).as("the declared system roles, written by the start-up reconciliation, pass").isEqualTo(6);
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

    private void insertNavigationItem(String parent, String name) {
        jdbc.sql("insert into platform_identity.navigation_items (item_id, parent_item_id, name) values (uuidv7(), cast(:parent as uuid), :name)")
                .param("parent", parent).param("name", name).update();
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-11 a navigation item name is unique under its parent, top level included")
    void id11NameUniqueUnderParent() {
        String top = jdbc.sql("select item_id::text from platform_identity.navigation_items where parent_item_id is null limit 1").query(String.class).single();
        try {
            insertNavigationItem(null, "ID11 top");
            insertNavigationItem(top, "ID11 child");
            refused(() -> insertNavigationItem(null, "ID11 top"), "uk_navigation_items_parent_item_id_name");
            refused(() -> insertNavigationItem(top, "ID11 child"), "uk_navigation_items_parent_item_id_name");
            insertNavigationItem(null, "ID11 child");
        } finally {
            jdbc.sql("delete from platform_identity.navigation_items where name like 'ID11 %'").update();
        }
    }

    private void insertAccount(long person, String lastLoginAt, String address) {
        jdbc.sql("insert into platform_identity.user_accounts (user_id, employee_id, last_login_at, last_login_address) values (:user, :employee, cast(:at as timestamptz), :address)")
                .param("user", com.forcitis.one.fixture.doubles.TestAccounts.account(person).value())
                .param("employee", com.forcitis.one.fixture.doubles.TestAccounts.employee(person).value())
                .param("at", lastLoginAt).param("address", address).update();
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-12 a last login address needs a last login time; the time alone and neither are legal")
    void id12LoginPair() {
        refused(() -> insertAccount(201, null, "203.0.113.9"), "ck_user_accounts_last_login");
        insertAccount(202, null, null);
        insertAccount(203, "2026-10-16T08:00:00Z", "203.0.113.9");
        insertAccount(204, "2026-10-16T08:00:00Z", null);
        refused(() -> jdbc.sql("update platform_identity.user_accounts set last_login_at = null where employee_id = :e")
                .param("e", com.forcitis.one.fixture.doubles.TestAccounts.employee(203).value()).update(), "ck_user_accounts_last_login");
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-12 a last login address is 1 to 64 characters; empty and over-long are refused, a zone id is legal")
    void id12AddressLength() {
        refused(() -> insertAccount(205, "2026-10-16T08:00:00Z", ""), "ck_user_accounts_last_login_address");
        refused(() -> insertAccount(206, "2026-10-16T08:00:00Z", "1".repeat(65)), "ck_user_accounts_last_login_address");
        insertAccount(207, "2026-10-16T08:00:00Z", "1".repeat(64));
        insertAccount(208, "2026-10-16T08:00:00Z", "fe80::1%eth0");
    }
}
