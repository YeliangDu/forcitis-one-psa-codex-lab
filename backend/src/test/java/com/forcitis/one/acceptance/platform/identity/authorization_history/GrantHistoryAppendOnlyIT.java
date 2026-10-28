package com.forcitis.one.acceptance.platform.identity.authorization_history;

import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.CHECK_VIOLATION;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.T0;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.T1;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.T2;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.T3;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0034 guard rule 1 (GrantHistoryAppendOnlyIntegrationTest), ADR-0034-IMPL card section 4 rows 0034-G1-01..05 and the triggers of
 * card 7.1: role_versions and account_role_grants only grow. No row is deleted or truncated, a closed row never changes, an open row may
 * only be closed once (its closing columns written, nothing else), closing a grant writes the cause and the audit entry with the end, and a
 * role without a current version cannot be granted.
 */
class GrantHistoryAppendOnlyIT {

    private static final String REWRITE = "refuse_history_rewrite";
    private static final String OPEN_VERSION = "require_open_role_version";

    private AuthorizationHistoryRows db;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new AuthorizationHistoryRows(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    @Test
    @DisplayName("0034-G1-01 两张新表各有开放行与已关闭行：DELETE 与 TRUNCATE 均被触发器 refuse_history_rewrite 以 restrict_violation（23001）拒绝")
    void deleteAndTruncateAreRefusedOnBothTables() throws SQLException {
        UUID role = db.role();
        UUID oldVersion = db.replacedVersion(role, 1, T0, T1);
        UUID currentVersion = db.openVersion(role, 2, T1);
        UUID account = db.account();
        UUID revoked = db.revokedGrant(account, role, T0, T1);
        UUID open = db.openGrant(account, role, T1);

        db.refusedByTrigger(REWRITE, "delete from platform_identity.role_versions where role_version_id = ?", currentVersion);
        db.refusedByTrigger(REWRITE, "delete from platform_identity.role_versions where role_version_id = ?", oldVersion);
        db.refusedByTrigger(REWRITE, "delete from platform_identity.role_versions");
        assertThat(db.refused("truncate table platform_identity.role_versions").getSQLState())
                .as("TRUNCATE role_versions is refused: by the child tables' foreign keys first (0A000) or by the trigger (23001)")
                .isIn("0A000", "23001");
        db.refusedByTrigger(REWRITE, "delete from platform_identity.account_role_grants where account_role_grant_id = ?", open);
        db.refusedByTrigger(REWRITE, "delete from platform_identity.account_role_grants where account_role_grant_id = ?", revoked);
        db.refusedByTrigger(REWRITE, "delete from platform_identity.account_role_grants");
        db.refusedByTrigger(REWRITE, "truncate table platform_identity.account_role_grants");

        assertThat(db.count("select count(*) from platform_identity.role_versions where role_id = ?", role)).isEqualTo(2);
        assertThat(db.count("select count(*) from platform_identity.account_role_grants where user_id = ?", account)).isEqualTo(2);
    }

    @Test
    @DisplayName("0034-G1-02 已关闭的角色版本与已关闭的授予行：任何 UPDATE（含关闭列、updated_at、原值写回）都被 refuse_history_rewrite 以 23001 拒绝")
    void aClosedRowNeverChanges() throws SQLException {
        UUID role = db.role();
        UUID oldVersion = db.replacedVersion(role, 1, T0, T1);
        db.openVersion(role, 2, T1);
        UUID account = db.account();
        UUID closer = db.account();
        UUID revoked = db.revokedGrant(account, role, T0, T1);

        String version = " where role_version_id = ?";
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set effective_to_at = effective_to_at" + version, oldVersion);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set effective_to_at = ?::timestamptz" + version, T2, oldVersion);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set updated_at = ?::timestamptz" + version, T3, oldVersion);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set closed_by = ?, closure_reason = 'deleted later',"
                + " closure_audit_entry_id = ?" + version, closer, UUID.randomUUID(), oldVersion);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set role_name = ?" + version, AuthorizationHistoryRows.uniqueName(), oldVersion);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set effective_to_at = null, updated_at = ?::timestamptz" + version, T3, oldVersion);

        String grant = " where account_role_grant_id = ?";
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set effective_to_at = effective_to_at" + grant, revoked);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set effective_to_at = ?::timestamptz" + grant, T2, revoked);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set updated_at = ?::timestamptz" + grant, T3, revoked);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set revocation_audit_entry_id = ?" + grant, UUID.randomUUID(), revoked);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set revocation_cause_kind = 'MANUAL', revoked_by = ?" + grant, closer, revoked);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set effective_to_at = null, revocation_cause_kind = null,"
                + " revocation_audit_entry_id = null" + grant, revoked);
    }

    @Test
    @DisplayName("0034-G1-03 开放的角色版本：改 role_id、permission_codes、role_name 等关闭列以外的列被 23001 拒；只改 updated_at 被拒；关闭时夹带改名被拒；只写 effective_to_at 与 updated_at 关闭成功；再改一次被拒")
    void anOpenVersionCanOnlyBeClosedOnce() throws SQLException {
        UUID role = db.role();
        UUID otherRole = db.role();
        db.openVersion(otherRole, 1, T0);
        UUID current = db.openVersion(role, 1, T0);
        String where = " where role_version_id = ?";

        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set role_id = ?" + where, otherRole, current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set permission_codes = '{platform.identity:role:read,platform.identity:role:update}'" + where, current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set role_name = ?" + where, AuthorizationHistoryRows.uniqueName(), current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set role_status = 'DISABLED'" + where, current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set access_scope_kind = 'ALL'" + where, current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set version_number = 2" + where, current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set effective_from_at = ?::timestamptz" + where, T1, current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set version_audit_entry_id = ?" + where, UUID.randomUUID(), current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set created_at = ?::timestamptz" + where, T1, current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set updated_at = ?::timestamptz" + where, T1, current);
        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set effective_to_at = ?::timestamptz, updated_at = ?::timestamptz, role_name = ?" + where,
                T1, T1, AuthorizationHistoryRows.uniqueName(), current);

        db.setup("update platform_identity.role_versions set effective_to_at = ?::timestamptz, updated_at = ?::timestamptz" + where, T1, T1, current);
        assertThat(db.count("select count(*) from platform_identity.role_versions where role_version_id = ? and effective_to_at = ?::timestamptz", current, T1))
                .as("the version is closed at T1").isEqualTo(1);

        db.refusedByTrigger(REWRITE, "update platform_identity.role_versions set effective_to_at = ?::timestamptz, updated_at = ?::timestamptz" + where, T2, T2, current);
    }

    @Test
    @DisplayName("0034-G1-03 角色删除：开放版本同时写 effective_to_at、closed_by、closure_reason、closure_audit_entry_id、updated_at 关闭成功；再改一次被 23001 拒")
    void anOpenVersionIsClosedByDeletingTheRole() throws SQLException {
        UUID role = db.role();
        UUID current = db.openVersion(role, 1, T0);
        UUID deleter = db.account();
        String close = "update platform_identity.role_versions set effective_to_at = ?::timestamptz, closed_by = ?, closure_reason = ?,"
                + " closure_audit_entry_id = ?, updated_at = ?::timestamptz where role_version_id = ?";

        db.setup(close, T1, deleter, "role deleted", UUID.randomUUID(), T1, current);
        assertThat(db.count("select count(*) from platform_identity.role_versions where role_id = ? and effective_to_at is null", role))
                .as("the deleted role has no current version").isZero();

        db.refusedByTrigger(REWRITE, close, T2, deleter, "role deleted again", UUID.randomUUID(), T2, current);
    }

    @Test
    @DisplayName("0034-G1-03 开放授予：改 role_id、grant_channel_kind、user_id 等关闭列以外的列被 23001 拒；只改 updated_at 被拒；收回时夹带改渠道被拒；同时写 effective_to_at 与收回列成功；再改一次被拒")
    void anOpenGrantCanOnlyBeRevokedOnce() throws SQLException {
        UUID role = db.role();
        db.openVersion(role, 1, T0);
        UUID otherRole = db.role();
        db.openVersion(otherRole, 1, T0);
        UUID account = db.account();
        UUID otherAccount = db.account();
        UUID granter = db.account();
        UUID open = db.openGrant(account, role, T0);
        String where = " where account_role_grant_id = ?";

        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set role_id = ?" + where, otherRole, open);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set grant_channel_kind = 'OPERATIONS'" + where, open);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set grant_channel_kind = 'MANUAL', granted_by = ?" + where, granter, open);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set user_id = ?" + where, otherAccount, open);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set effective_from_at = ?::timestamptz" + where, T1, open);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set grant_audit_entry_id = ?" + where, UUID.randomUUID(), open);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set created_at = ?::timestamptz" + where, T1, open);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set updated_at = ?::timestamptz" + where, T1, open);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = ?" + where,
                UUID.randomUUID(), open);
        db.refusedByTrigger(REWRITE, "update platform_identity.account_role_grants set effective_to_at = ?::timestamptz, revocation_cause_kind = 'OPERATIONS',"
                + " revocation_audit_entry_id = ?, updated_at = ?::timestamptz, grant_channel_kind = 'OPERATIONS'" + where, T1, UUID.randomUUID(), T1, open);

        String revoke = "update platform_identity.account_role_grants set effective_to_at = ?::timestamptz, revocation_cause_kind = 'OPERATIONS',"
                + " revocation_audit_entry_id = ?, updated_at = ?::timestamptz" + where;
        db.setup(revoke, T1, UUID.randomUUID(), T1, open);
        assertThat(db.count("select count(*) from platform_identity.account_role_grants where user_id = ? and effective_to_at is null", account))
                .as("the account holds no current grant").isZero();

        db.refusedByTrigger(REWRITE, revoke, T2, UUID.randomUUID(), T2, open);
    }

    @Test
    @DisplayName("0034-G1-03 离职收回：开放授予同时写 effective_to_at、revocation_cause_kind=SEPARATION、revocation_separation_command_id、revocation_audit_entry_id、updated_at 成功；人工收回（MANUAL + revoked_by）亦成功")
    void anOpenGrantIsClosedBySeparationOrByHand() throws SQLException {
        UUID role = db.role();
        db.openVersion(role, 1, T0);
        UUID account = db.account();
        UUID revoker = db.account();
        UUID separated = db.openGrant(account, role, T0);
        UUID byHand = db.openGrant(revoker, role, T0);

        db.setup("update platform_identity.account_role_grants set effective_to_at = ?::timestamptz, revocation_cause_kind = 'SEPARATION',"
                + " revocation_separation_command_id = ?, revocation_audit_entry_id = ?, updated_at = ?::timestamptz where account_role_grant_id = ?",
                T1, UUID.randomUUID(), UUID.randomUUID(), T1, separated);
        db.setup("update platform_identity.account_role_grants set effective_to_at = ?::timestamptz, revocation_cause_kind = 'MANUAL', revoked_by = ?,"
                + " revocation_audit_entry_id = ?, updated_at = ?::timestamptz where account_role_grant_id = ?", T1, revoker, UUID.randomUUID(), T1, byHand);

        assertThat(db.count("select count(*) from platform_identity.account_role_grants where role_id = ? and effective_to_at = ?::timestamptz", role, T1))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("0034-G1-04 开放授予只置 effective_to_at、不写 revocation_cause_kind 与审计条目 ID：通过触发器后被成对 CHECK ck_account_role_grants_revocation（23514）拒；只缺审计条目 ID 或只缺原因亦被拒")
    void revokingWithoutCauseAndAuditEntryIsRefusedByThePairedCheck() throws SQLException {
        UUID role = db.role();
        db.openVersion(role, 1, T0);
        UUID account = db.account();
        UUID open = db.openGrant(account, role, T0);
        String where = " where account_role_grant_id = ?";

        db.refusedByConstraint(CHECK_VIOLATION, "ck_account_role_grants_revocation",
                "update platform_identity.account_role_grants set effective_to_at = ?::timestamptz" + where, T1, open);
        db.refusedByConstraint(CHECK_VIOLATION, "ck_account_role_grants_revocation",
                "update platform_identity.account_role_grants set effective_to_at = ?::timestamptz, updated_at = ?::timestamptz" + where, T1, T1, open);
        db.refusedByConstraint(CHECK_VIOLATION, "ck_account_role_grants_revocation",
                "update platform_identity.account_role_grants set effective_to_at = ?::timestamptz, revocation_cause_kind = 'OPERATIONS', updated_at = ?::timestamptz" + where,
                T1, T1, open);
        db.refusedByConstraint(CHECK_VIOLATION, "ck_account_role_grants_revocation",
                "update platform_identity.account_role_grants set effective_to_at = ?::timestamptz, revocation_audit_entry_id = ?, updated_at = ?::timestamptz" + where,
                T1, UUID.randomUUID(), T1, open);

        assertThat(db.count("select count(*) from platform_identity.account_role_grants where account_role_grant_id = ? and effective_to_at is null", open))
                .as("the grant is still current").isEqualTo(1);
    }

    @Test
    @DisplayName("0034-G1-05 角色没有开放版本（从无版本，或唯一版本已关闭即已删除）：插入该角色的授予被 BEFORE INSERT 触发器 require_open_role_version 以 23001 拒；有开放版本的角色可授予")
    void aRoleWithoutCurrentVersionCannotBeGranted() throws SQLException {
        UUID neverVersioned = db.role();
        UUID deleted = db.role();
        UUID deleter = db.account();
        UUID deletedVersion = db.openVersion(deleted, 1, T0);
        db.setup("update platform_identity.role_versions set effective_to_at = ?::timestamptz, closed_by = ?, closure_reason = 'role deleted',"
                + " closure_audit_entry_id = ?, updated_at = ?::timestamptz where role_version_id = ?", T1, deleter, UUID.randomUUID(), T1, deletedVersion);
        UUID current = db.role();
        db.openVersion(current, 1, T0);
        UUID account = db.account();

        for (UUID role : new UUID[] {neverVersioned, deleted}) {
            db.refusedByTrigger(OPEN_VERSION, AuthorizationHistoryRows.GRANT_INSERT, UUID.randomUUID(), account, role, T2, null, UUID.randomUUID(), null, null, T2, T2);
        }
        db.accepted(AuthorizationHistoryRows.GRANT_INSERT, UUID.randomUUID(), account, current, T2, null, UUID.randomUUID(), null, null, T2, T2);
    }
}
