package com.forcitis.one.acceptance.platform.identity.data_scope;

import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.UNIQUE_VIOLATION;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.platform.identity.data_scope.PermissionAdjustmentRows.Row;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * F-1 card (#729) constraint table rows 9-12, 15-17, 32 and 35: what a permission adjustment is about, where it came from and how the
 * three dedupe keys hold, refused by the database on direct inserts. Each refusal changes one column of a valid shape so that exactly the
 * named constraint is broken; the valid shapes themselves are accepted first.
 */
class PermissionAdjustmentShapeIT {

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
    @DisplayName("F-1 7.1 卡里的五种合法形状都能写入：敏感角色复核（调部门、只改岗位、到期重新生成）、范围复核来源①（带收件人、新部门无负责人收件人为空）、来源②")
    void theCardsShapesAreAccepted() throws SQLException {
        rows.accepted(rows.sensitiveOrgUnitChange());
        rows.accepted(rows.sensitivePositionChange());
        rows.accepted(rows.regenerated());
        rows.accepted(rows.scopeReviewTransfer());
        rows.accepted(rows.scopeReviewTransfer().with("recipient_employee_id", null));
        rows.accepted(rows.scopeReviewInactiveUnit());
    }

    @Test
    @DisplayName("F-1 7.1 取值集合：kind、status、trigger_field_kind、origin_kind 各拒绝卡外的取值")
    void valuesOutsideTheCardsSetsAreRefused() throws SQLException {
        // kind also breaks kind_origin; CHECK constraints are tested in name order, so kind is the one named
        rows.refusedByCheck("ck_permission_adjustments_kind", rows.sensitiveOrgUnitChange().with("kind", "ROLE_REVIEW"));
        rows.refusedByCheck("ck_permission_adjustments_status", rows.sensitiveOrgUnitChange().with("status", "CLOSED"));
        rows.refusedByCheck("ck_permission_adjustments_trigger_field_kind", rows.sensitiveOrgUnitChange().with("trigger_field_kind", "DEPARTMENT"));
        // an unknown origin breaks several origin-keyed checks at once; only that it is a CHECK refusal on this table is asserted
        Row unknownOrigin = rows.sensitiveOrgUnitChange().with("origin_kind", "GRANT_MODE_UPGRADE");
        PSQLException refusal = db.refused(PermissionAdjustmentRows.insertSql(unknownOrigin), unknownOrigin.columns.values().toArray());
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(PermissionAdjustmentRows.CHECK_VIOLATION);
        assertThat(refusal.getServerErrorMessage().getConstraint()).startsWith("ck_permission_adjustments_");
    }

    @Test
    @DisplayName("F-1 不变量 9 类型与来源相容：敏感角色复核不来自部门停用，范围复核不来自维持到期")
    void kindAndOriginGoTogether() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_kind_origin", rows.scopeReviewInactiveUnit().with("kind", "SENSITIVE_ROLE_REVIEW"));
        rows.refusedByCheck("ck_permission_adjustments_kind_origin", rows.regenerated().with("kind", "SCOPE_REVIEW"));
    }

    @Test
    @DisplayName("F-1 不变量 10 关于人与关于角色 × 部门互斥：任职变化条目不带角色，部门停用条目不带人且角色、部门都有")
    void anItemIsAboutAPersonOrAboutARoleAndUnit() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_subject", rows.sensitiveOrgUnitChange().with("role_id", db.role()));
        rows.refusedByCheck("ck_permission_adjustments_subject", rows.sensitiveOrgUnitChange().with("user_id", null));
        rows.refusedByCheck("ck_permission_adjustments_subject", rows.scopeReviewInactiveUnit().with("user_id", db.account()));
        rows.refusedByCheck("ck_permission_adjustments_subject", rows.scopeReviewInactiveUnit().with("org_unit_id", null));
        rows.refusedByCheck("ck_permission_adjustments_subject", rows.scopeReviewInactiveUnit().with("role_id", null));
    }

    @Test
    @DisplayName("F-1 不变量 11 任职变化条目有触发字段与触发前后部门，其他来源没有；同步触发（任职变化、部门停用）的条目有触发同步运行，重新生成的没有")
    void onlyAnAssignmentChangeCarriesTheTrigger() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_assignment_change", rows.sensitiveOrgUnitChange().with("trigger_field_kind", null));
        rows.refusedByCheck("ck_permission_adjustments_assignment_change", rows.sensitiveOrgUnitChange().with("after_org_unit_id", null));
        rows.refusedByCheck("ck_permission_adjustments_assignment_change", rows.sensitivePositionChange().with("before_org_unit_id", null));
        rows.refusedByCheck("ck_permission_adjustments_assignment_change",
                rows.scopeReviewInactiveUnit().with("before_org_unit_id", UUID.randomUUID()).with("after_org_unit_id", UUID.randomUUID()));
        rows.refusedByCheck("ck_permission_adjustments_assignment_change", rows.regenerated().with("before_position_id", UUID.randomUUID()));

        rows.refusedByCheck("ck_permission_adjustments_trigger_sync_run_id", rows.sensitiveOrgUnitChange().with("trigger_sync_run_id", null));
        rows.refusedByCheck("ck_permission_adjustments_trigger_sync_run_id", rows.scopeReviewInactiveUnit().with("trigger_sync_run_id", null));
        rows.refusedByCheck("ck_permission_adjustments_trigger_sync_run_id", rows.regenerated().with("trigger_sync_run_id", UUID.randomUUID()));
    }

    @Test
    @DisplayName("F-1 不变量 35 触发字段为部门时触发前后部门不同；只改岗位的条目前后部门可以相同")
    void anOrgUnitTriggerMovesToAnotherUnit() throws SQLException {
        Row sameUnit = rows.sensitiveOrgUnitChange();
        sameUnit.with("after_org_unit_id", sameUnit.columns.get("before_org_unit_id"));
        rows.refusedByCheck("ck_permission_adjustments_assignment_change", sameUnit);
        rows.accepted(rows.sensitivePositionChange());
    }

    @Test
    @DisplayName("F-1 不变量 12 范围复核来源①只因部门变化触发；收件人只出现在来源①上（敏感角色复核、来源②都不带收件人）")
    void onlyAScopeReviewOfATransferHasARecipient() throws SQLException {
        Row positionOnly = rows.scopeReviewTransfer().with("trigger_field_kind", "POSITION").with("before_position_id", UUID.randomUUID())
                .with("after_position_id", UUID.randomUUID());
        rows.refusedByCheck("ck_permission_adjustments_recipient", positionOnly);
        rows.refusedByCheck("ck_permission_adjustments_recipient", positionOnly.copy().with("recipient_employee_id", null));
        rows.refusedByCheck("ck_permission_adjustments_recipient", rows.sensitiveOrgUnitChange().with("recipient_employee_id", UUID.randomUUID()));
        rows.refusedByCheck("ck_permission_adjustments_recipient", rows.scopeReviewInactiveUnit().with("recipient_employee_id", UUID.randomUUID()));
    }

    @Test
    @DisplayName("F-1 7.1 原条目只在维持到期重新生成的条目上、且不指向自身；其他来源不带原条目")
    void onlyARegeneratedItemNamesAnOriginal() throws SQLException {
        rows.refusedByCheck("ck_permission_adjustments_original", rows.regenerated().with("original_permission_adjustment_id", null));
        Row self = rows.regenerated();
        self.with("original_permission_adjustment_id", self.id());
        rows.refusedByCheck("ck_permission_adjustments_original", self);
        Row earlier = rows.sensitiveOrgUnitChange();
        rows.insert(earlier);
        rows.refusedByCheck("ck_permission_adjustments_original", rows.sensitiveOrgUnitChange().with("original_permission_adjustment_id", earlier.id()));
    }

    @Test
    @DisplayName("F-1 不变量 15、32 同步触发按（类型，人，触发同步运行，触发字段）去重：同一键第二条 23505；同一次同步改部门与岗位各一条，同一次触发两类条目各一条，另一次同步再一条")
    void anAssignmentChangeIsRaisedOncePerKindPersonRunAndField() throws SQLException {
        Row first = rows.sensitiveOrgUnitChange();
        rows.insert(first);

        rows.refusedBy(UNIQUE_VIOLATION, "uk_permission_adjustments_assignment_trigger", first.copy());
        rows.refusedBy(UNIQUE_VIOLATION, "uk_permission_adjustments_assignment_trigger", first.copy().with("status", "CLOSED_BY_SEPARATION")
                .with("closed_at", PermissionAdjustmentRows.T1).with("closure_sync_run_id", UUID.randomUUID()));
        rows.accepted(first.copy().with("trigger_field_kind", "POSITION").with("before_position_id", UUID.randomUUID())
                .with("after_position_id", UUID.randomUUID()));
        rows.accepted(first.copy().with("kind", "SCOPE_REVIEW"));
        rows.accepted(first.copy().with("trigger_sync_run_id", UUID.randomUUID()));
        rows.accepted(first.copy().with("user_id", db.account()));
    }

    @Test
    @DisplayName("F-1 不变量 16 到期重新生成按原条目去重：同一原条目第二条重新生成 23505")
    void anExpiredItemIsRegeneratedOnce() throws SQLException {
        Row regenerated = rows.regenerated();
        rows.insert(regenerated);

        rows.refusedBy(UNIQUE_VIOLATION, "uk_permission_adjustments_original_permission_adjustment_id", regenerated.copy());
    }

    @Test
    @DisplayName("F-1 不变量 17 来源②按（角色，部门）至多一条待处理或已维持；已调整或因部门恢复关闭后可再生成；另一部门、另一角色各自一条")
    void anInactiveUnitHasAtMostOneLiveItemPerRole() throws SQLException {
        Row open = rows.scopeReviewInactiveUnit();
        rows.insert(open);
        rows.refusedBy(UNIQUE_VIOLATION, "uk_permission_adjustments_inactive_org_unit", open.copy().with("trigger_sync_run_id", UUID.randomUUID()));
        rows.accepted(open.copy().with("org_unit_id", UUID.randomUUID()));
        rows.accepted(open.copy().with("role_id", db.role()));

        Row upheld = rows.upheld(rows.scopeReviewInactiveUnit());
        rows.insert(upheld);
        rows.refusedBy(UNIQUE_VIOLATION, "uk_permission_adjustments_inactive_org_unit", rows.scopeReviewInactiveUnit()
                .with("role_id", upheld.columns.get("role_id")).with("org_unit_id", upheld.columns.get("org_unit_id")));

        Row adjusted = rows.scopeReviewInactiveUnit().with("status", "ADJUSTED").with("adjusted_at", PermissionAdjustmentRows.T1)
                .with("adjusted_audit_entry_id", UUID.randomUUID());
        rows.insert(adjusted);
        rows.accepted(rows.scopeReviewInactiveUnit().with("role_id", adjusted.columns.get("role_id")).with("org_unit_id", adjusted.columns.get("org_unit_id")));

        Row reactivated = rows.closed(rows.scopeReviewInactiveUnit(), "CLOSED_BY_REACTIVATION");
        rows.insert(reactivated);
        rows.accepted(rows.scopeReviewInactiveUnit().with("role_id", reactivated.columns.get("role_id"))
                .with("org_unit_id", reactivated.columns.get("org_unit_id")));
    }
}
