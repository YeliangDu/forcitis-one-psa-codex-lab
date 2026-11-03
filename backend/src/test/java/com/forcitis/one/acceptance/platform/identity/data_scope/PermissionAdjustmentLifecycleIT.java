package com.forcitis.one.acceptance.platform.identity.data_scope;

import static com.forcitis.one.acceptance.platform.identity.data_scope.PermissionAdjustmentRows.FOREIGN_KEY_VIOLATION;
import static com.forcitis.one.acceptance.platform.identity.data_scope.PermissionAdjustmentRows.T0;
import static com.forcitis.one.acceptance.platform.identity.data_scope.PermissionAdjustmentRows.T1;
import static com.forcitis.one.acceptance.platform.identity.data_scope.PermissionAdjustmentRows.T2;
import static com.forcitis.one.acceptance.platform.identity.data_scope.PermissionAdjustmentRows.T3;

import com.forcitis.one.acceptance.platform.identity.data_scope.PermissionAdjustmentRows.Row;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-1 card (#729) constraint table rows 13, 14, 19, 23 (database part), 25, 28-30 and 36: the columns each status carries, refused by
 * the database on direct inserts. A valid row of every status is accepted first; each refusal then breaks exactly the named constraint.
 */
class PermissionAdjustmentLifecycleIT {

    private CustomOrgUnitRows db;
    private PermissionAdjustmentRows rows;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new CustomOrgUnitRows(getClass());
        rows = new PermissionAdjustmentRows(db);
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    @Test
    @DisplayName("F-1 状态机 每个状态的合法行都能写入：已维持（两类）、已到期（已判定、在等放行）、已调整（人办、来源②系统转）、因离职关闭")
    void aValidRowOfEveryStatusIsAccepted() throws SQLException {
        rows.accepted(rows.upheld(rows.sensitiveOrgUnitChange()));
        rows.accepted(rows.upheld(rows.scopeReviewTransfer()));
        rows.accepted(rows.upheld(rows.scopeReviewInactiveUnit()));
        rows.accepted(rows.expired(rows.sensitiveOrgUnitChange()));
        rows.accepted(rows.expired(rows.sensitivePositionChange()).with("is_held_at_expiry", true).with("regeneration_decided_at", null));
        rows.accepted(rows.adjusted(rows.sensitiveOrgUnitChange()));
        rows.accepted(rows.adjusted(rows.scopeReviewTransfer()).with("adjusted_audit_entry_id", null).with("adjusted_authorization_request_id", db.openRequest()));
        rows.accepted(rows.adjusted(rows.scopeReviewInactiveUnit()).with("adjusted_by", null));
        rows.accepted(rows.closed(rows.sensitiveOrgUnitChange(), "CLOSED_BY_SEPARATION"));
        rows.accepted(rows.closed(rows.scopeReviewTransfer(), "CLOSED_BY_SEPARATION"));
        rows.accepted(rows.sensitiveOrgUnitChange().with("escalated_at", T2));
    }

    @Test
    @DisplayName("F-1 7.1 维持三列（时刻、维持人、原因）随已维持、已到期一起有、一起无")
    void theUpholdColumnsComeWithUpheldAndExpired() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_uphold", rows.upheld(rows.scopeReviewInactiveUnit()).with("upheld_by", null));
        rows.refusedByCheck("ck_permission_adjustments_uphold", rows.upheld(rows.scopeReviewInactiveUnit()).with("uphold_reason", null));
        rows.refusedByCheck("ck_permission_adjustments_uphold",
                rows.upheld(rows.scopeReviewInactiveUnit()).with("upheld_at", null).with("upheld_by", null).with("uphold_reason", null));
        rows.refusedByCheck("ck_permission_adjustments_uphold", rows.upheld(rows.scopeReviewInactiveUnit()).with("status", "OPEN"));
    }

    @Test
    @DisplayName("F-1 不变量 13 敏感角色复核维持后业务负责人与有效至必有、成对；范围复核维持不带业务负责人、不设有效至；未维持的不带")
    void onlyAnUpheldSensitiveRoleReviewHasAnOwnerAndAnExpiry() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_uphold_terms", rows.upheld(rows.sensitiveOrgUnitChange()).with("business_owner_employee_id", null));
        rows.refusedByCheck("ck_permission_adjustments_uphold_terms",
                rows.upheld(rows.sensitiveOrgUnitChange()).with("business_owner_employee_id", null).with("uphold_expires_at", null));
        rows.refusedByCheck("ck_permission_adjustments_uphold_terms", rows.upheld(rows.sensitiveOrgUnitChange()).with("uphold_expires_at", null));
        rows.refusedByCheck("ck_permission_adjustments_uphold_terms",
                rows.upheld(rows.scopeReviewTransfer()).with("business_owner_employee_id", UUID.randomUUID()).with("uphold_expires_at", T2));
        rows.refusedByCheck("ck_permission_adjustments_uphold_terms", rows.upheld(rows.scopeReviewInactiveUnit()).with("uphold_expires_at", T2));
        rows.refusedByCheck("ck_permission_adjustments_uphold_terms", rows.sensitiveOrgUnitChange().with("business_owner_employee_id", UUID.randomUUID()));
    }

    @Test
    @DisplayName("F-1 不变量 14 维持原因去掉空格后 1 至 500 字（按字符计，500 个汉字可写，501 个拒绝；全空格、空串拒绝）")
    void theUpholdReasonIsOneToFiveHundredCharacters() throws SQLException {
        rows.accepted(rows.upheld(rows.scopeReviewInactiveUnit()).with("uphold_reason", "留"));
        rows.accepted(rows.upheld(rows.scopeReviewInactiveUnit()).with("uphold_reason", "留".repeat(500)));
        rows.refusedByCheck("ck_permission_adjustments_uphold_reason", rows.upheld(rows.scopeReviewInactiveUnit()).with("uphold_reason", "留".repeat(501)));
        rows.refusedByCheck("ck_permission_adjustments_uphold_reason", rows.upheld(rows.scopeReviewInactiveUnit()).with("uphold_reason", "   "));
        rows.refusedByCheck("ck_permission_adjustments_uphold_reason", rows.upheld(rows.scopeReviewInactiveUnit()).with("uphold_reason", ""));
    }

    @Test
    @DisplayName("F-1 不变量 23（库内部分）有效至晚于维持时刻：等于或早于维持时刻都拒绝")
    void theExpiryIsAfterTheUphold() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_uphold_expires_at", rows.upheld(rows.sensitiveOrgUnitChange()).with("uphold_expires_at", T1));
        rows.refusedByCheck("ck_permission_adjustments_uphold_expires_at", rows.upheld(rows.sensitiveOrgUnitChange()).with("uphold_expires_at", T0));
    }

    @Test
    @DisplayName("F-1 不变量 25、26 （#729 DD 裁定新形）已到期只来自敏感角色复核的维持、到期时刻不早于有效至（等于可）；已到期的必记到期时是否暂停，不在暂停的必已判定重新生成，暂停的可等放行后补判；未到期的不带这两列")
    void onlyAnUpheldSensitiveRoleReviewExpiresAtItsExpiry() throws SQLException {
        rows.accepted(rows.expired(rows.sensitiveOrgUnitChange()).with("expired_at", T2));
        rows.accepted(rows.expired(rows.sensitiveOrgUnitChange()).with("expired_at", T3));
        rows.refusedByCheck("ck_permission_adjustments_expiry", rows.expired(rows.sensitiveOrgUnitChange()).with("expired_at", T1));
        rows.refusedByCheck("ck_permission_adjustments_expiry",
                rows.upheld(rows.scopeReviewInactiveUnit()).with("status", "EXPIRED").with("expired_at", T2).with("is_held_at_expiry", false)
                        .with("regeneration_decided_at", T2));
        rows.refusedByCheck("ck_permission_adjustments_expiry", rows.expired(rows.sensitiveOrgUnitChange()).with("expired_at", null));
        rows.refusedByCheck("ck_permission_adjustments_expiry", rows.expired(rows.sensitiveOrgUnitChange()).with("regeneration_decided_at", null));
        rows.refusedByCheck("ck_permission_adjustments_expiry", rows.expired(rows.sensitiveOrgUnitChange()).with("is_held_at_expiry", null));
        rows.accepted(rows.expired(rows.sensitiveOrgUnitChange()).with("is_held_at_expiry", true));
        rows.refusedByCheck("ck_permission_adjustments_expiry", rows.upheld(rows.sensitiveOrgUnitChange()).with("expired_at", T2));
        rows.refusedByCheck("ck_permission_adjustments_expiry", rows.upheld(rows.sensitiveOrgUnitChange()).with("regeneration_decided_at", T2));
        rows.refusedByCheck("ck_permission_adjustments_expiry", rows.upheld(rows.sensitiveOrgUnitChange()).with("is_held_at_expiry", false));
    }

    @Test
    @DisplayName("F-1 不变量 28 已调整恰好关联一个授权申请或审计条目；人办的有调整人，只有来源②（角色出新版本系统转）可无调整人；未调整的不带调整列")
    void anAdjustmentLinksExactlyOneRecord() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_adjusted", rows.adjusted(rows.sensitiveOrgUnitChange()).with("adjusted_audit_entry_id", null));
        rows.refusedByCheck("ck_permission_adjustments_adjusted",
                rows.adjusted(rows.sensitiveOrgUnitChange()).with("adjusted_authorization_request_id", db.openRequest()));
        rows.refusedByCheck("ck_permission_adjustments_adjusted", rows.adjusted(rows.sensitiveOrgUnitChange()).with("adjusted_by", null));
        rows.refusedByCheck("ck_permission_adjustments_adjusted", rows.adjusted(rows.scopeReviewTransfer()).with("adjusted_by", null));
        rows.refusedByCheck("ck_permission_adjustments_adjusted", rows.adjusted(rows.sensitiveOrgUnitChange()).with("adjusted_at", null));
        rows.refusedByCheck("ck_permission_adjustments_adjusted", rows.adjusted(rows.sensitiveOrgUnitChange()).with("status", "OPEN"));
        rows.refusedByCheck("ck_permission_adjustments_adjusted", rows.sensitiveOrgUnitChange().with("adjusted_by", db.account()));
        rows.refusedByCheck("ck_permission_adjustments_adjusted", rows.sensitiveOrgUnitChange().with("adjusted_audit_entry_id", UUID.randomUUID()));
    }

    @Test
    @DisplayName("F-1 不变量 29（#729 DD 裁定：只剩离职）因离职关闭只用于关于人的条目；关闭时刻与所据人事命令成对，随关闭状态有、无")
    void eachClosureFitsItsItems() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_closed", rows.closed(rows.scopeReviewInactiveUnit(), "CLOSED_BY_SEPARATION"));
        rows.refusedByCheck("ck_permission_adjustments_closed", rows.closed(rows.sensitiveOrgUnitChange(), "CLOSED_BY_SEPARATION").with("closure_personnel_command_id", null));
        rows.refusedByCheck("ck_permission_adjustments_closed", rows.closed(rows.sensitiveOrgUnitChange(), "CLOSED_BY_SEPARATION").with("closed_at", null));
        rows.refusedByCheck("ck_permission_adjustments_closed", rows.closed(rows.sensitiveOrgUnitChange(), "CLOSED_BY_SEPARATION").with("status", "OPEN"));
    }

    @Test
    @DisplayName("F-1 不变量 19 本人不处理关于本人的条目：维持人或调整人是当事人都拒绝；来源②没有当事人，不受此限")
    void nobodyHandlesAnItemAboutThemselves() throws SQLException {
        Row upheld = rows.upheld(rows.sensitiveOrgUnitChange());
        rows.refusedByCheck("ck_permission_adjustments_not_own", upheld.with("upheld_by", upheld.columns.get("user_id")));
        Row adjusted = rows.adjusted(rows.scopeReviewTransfer());
        rows.refusedByCheck("ck_permission_adjustments_not_own", adjusted.with("adjusted_by", adjusted.columns.get("user_id")));
    }

    @Test
    @DisplayName("F-1 不变量 30（库内部分）升级期限晚于创建时刻：等于或早于都拒绝")
    void theEscalationIsDueAfterCreation() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_escalation_due_at", rows.sensitiveOrgUnitChange().with("escalation_due_at", T0));
        rows.refusedByCheck("ck_permission_adjustments_escalation_due_at",
                rows.sensitiveOrgUnitChange().with("escalation_due_at", T0).with("created_at", T1).with("updated_at", T1));
    }

    @Test
    @DisplayName("F-1 不变量 36 / B4 当事人、维持人、调整人须是本 schema 的账号，角色、原条目、关联授权申请须存在（23503，按外键名）")
    void referencesInsideTheSchemaMustExist() throws SQLException {
        rows.refusedBy(FOREIGN_KEY_VIOLATION, "fk_permission_adjustments_user_id", rows.sensitiveOrgUnitChange().with("user_id", UUID.randomUUID()));
        rows.refusedBy(FOREIGN_KEY_VIOLATION, "fk_permission_adjustments_upheld_by",
                rows.upheld(rows.sensitiveOrgUnitChange()).with("upheld_by", UUID.randomUUID()));
        rows.refusedBy(FOREIGN_KEY_VIOLATION, "fk_permission_adjustments_adjusted_by",
                rows.adjusted(rows.sensitiveOrgUnitChange()).with("adjusted_by", UUID.randomUUID()));
        rows.refusedBy(FOREIGN_KEY_VIOLATION, "fk_permission_adjustments_role_id", rows.scopeReviewInactiveUnit().with("role_id", UUID.randomUUID()));
        rows.refusedBy(FOREIGN_KEY_VIOLATION, "fk_permission_adjustments_original_permission_adjustment_id",
                rows.regenerated().with("original_permission_adjustment_id", UUID.randomUUID()));
        rows.refusedBy(FOREIGN_KEY_VIOLATION, "fk_permission_adjustments_adjusted_authorization_request_id",
                rows.adjusted(rows.sensitiveOrgUnitChange()).with("adjusted_audit_entry_id", null).with("adjusted_authorization_request_id", UUID.randomUUID()));
    }
}
