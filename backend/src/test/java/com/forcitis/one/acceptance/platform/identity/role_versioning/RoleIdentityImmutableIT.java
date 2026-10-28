package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0034 decision 1 item 1 and guard rule 1 (roles: code, kind, is_administrative cannot change); ADR-0034-IMPL card row 0034-G1-06 and
 * the roles part of card 7.2 (roles keeps only its immutable identity; name, status and access scope move to role_versions;
 * role_permissions retires). Checked on the database the migrations build, before any application start.
 */
class RoleIdentityImmutableIT {

    private static final String IDENTITY = "refuse_role_identity_change";

    private MigratedRoleRows db;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new MigratedRoleRows(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    /** Card 0034-G1-06; ADR-0034 decision 1 item 1 (trigger refuses code, kind, is_administrative); card 7.2 trigger also guards role_id. */
    @Test
    @DisplayName("0034-G1-06 roles 行：改 code（含只改大小写）、kind、is_administrative、role_id 各被触发器 refuse_role_identity_change 以 restrict_violation（23001）拒")
    void identityColumnsCannotChange() throws SQLException {
        UUID role = db.customRole();
        String where = " where role_id = ?";

        db.refusedByTrigger(IDENTITY, "update platform_identity.roles set code = ?" + where, "rv_other_" + role.toString().substring(0, 8), role);
        db.refusedByTrigger(IDENTITY, "update platform_identity.roles set code = upper(code)" + where, role);
        db.refusedByTrigger(IDENTITY, "update platform_identity.roles set kind = 'SYSTEM'" + where, role);
        db.refusedByTrigger(IDENTITY, "update platform_identity.roles set is_administrative = true" + where, role);
        db.refusedByTrigger(IDENTITY, "update platform_identity.roles set role_id = ?" + where, UUID.randomUUID(), role);
        db.refusedByTrigger(IDENTITY, "update platform_identity.roles set version = version + 1, kind = 'SYSTEM'" + where, role);

        assertThat(db.column("select code from platform_identity.roles" + where, role)).as("the row is unchanged")
                .containsExactly("rv_" + role.toString().substring(0, 8));
    }

    /** Card 0034-G1-06 (改 version、event_count 成功); ADR-0034 decision 1 item 1 keeps the optimistic lock version and event_count on roles. */
    @Test
    @DisplayName("0034-G1-06 roles 行：改 version、event_count（各自、同时、连同 updated_at）成功")
    void versionAndEventCountChange() throws SQLException {
        UUID role = db.customRole();
        String where = " where role_id = ?";

        db.accepted("update platform_identity.roles set version = version + 1" + where, role);
        db.accepted("update platform_identity.roles set event_count = event_count + 1" + where, role);
        db.accepted("update platform_identity.roles set version = version + 1, event_count = event_count + 1, updated_at = ?::timestamptz" + where,
                "2026-11-02T02:00:00Z", role);
    }

    /** Card 7.2 (roles 删除 name、status、access_scope 三列；保留 uq_roles_code 与两条 CHECK；新增 tg_roles_identity_immutable); ADR-0034 decision 1 item 1. */
    @Test
    @DisplayName("0034-G1-06 / 卡 7.2 roles 只留不可变列：列为 role_id、code、kind、is_administrative、created_at、updated_at、version、event_count；无 uq_roles_name；保留 uq_roles_code、ck_roles_reserved_code、ck_roles_code；有 BEFORE UPDATE 行触发器 tg_roles_identity_immutable")
    void rolesKeepsOnlyItsIdentity() throws SQLException {
        assertThat(db.column("""
                select a.attname::text from pg_attribute a where a.attrelid = 'platform_identity.roles'::regclass and a.attnum > 0 and not a.attisdropped
                 order by a.attnum""")).containsExactly("role_id", "code", "kind", "is_administrative", "created_at", "updated_at", "version", "event_count");
        assertThat(db.column("select c.relname::text from pg_index i join pg_class c on c.oid = i.indexrelid where i.indrelid = 'platform_identity.roles'::regclass"))
                .contains("uq_roles_code").doesNotContain("uq_roles_name");
        assertThat(db.column("select conname::text from pg_constraint where conrelid = 'platform_identity.roles'::regclass and contype = 'c'"))
                .contains("ck_roles_reserved_code", "ck_roles_code");
        List<Object> triggers = db.column("select pg_get_triggerdef(oid) from pg_trigger where tgrelid = 'platform_identity.roles'::regclass and not tgisinternal");
        assertThat(triggers).containsExactly(
                "CREATE TRIGGER tg_roles_identity_immutable BEFORE UPDATE ON platform_identity.roles FOR EACH ROW EXECUTE FUNCTION platform_identity.refuse_role_identity_change()");
    }

    /** Card 7.2 (删除整张 role_permissions；不得出现 account_roles 的变化——授予退役属 PR-3); ADR-0034 decision 1 item 3. */
    @Test
    @DisplayName("0034-G1-06 / 卡 7.2 role_permissions 整张删除；account_roles 仍在（PR-2 不动授予）")
    void rolePermissionsRetiresAccountRolesStays() throws SQLException {
        assertThat(db.column("select to_regclass('platform_identity.role_permissions')::text")).containsExactly((Object) null);
        assertThat(db.column("select to_regclass('platform_identity.account_roles')::text")).containsExactly("platform_identity.account_roles");
    }
}
