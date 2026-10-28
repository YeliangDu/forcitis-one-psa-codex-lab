package com.forcitis.one.acceptance.platform.identity.data_scope;

import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.GUARD;
import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.REQUEST_UNITS;
import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.REQUEST_UNIT_INSERT;
import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.ROLE_VERSION_UNITS;
import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.ROLE_VERSION_UNIT_INSERT;
import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.T0;
import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.T1;
import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.T2;
import static com.forcitis.one.acceptance.platform.identity.data_scope.CustomOrgUnitRows.UNIQUE_VIOLATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-1 card (#728) constraint table rows 1, 2, 3 and 8, section 7.1 triggers: what the database itself keeps. A detail row hangs only on
 * an open role version whose scope kind is CUSTOM_ORG_UNITS, a request row only on a PENDING CREATE_ROLE or UPDATE_ROLE request whose
 * scope kind is CUSTOM_ORG_UNITS (the refusals come from guard_custom_org_unit_rows, restrict_violation 23001, not from a foreign key);
 * a row, once written, is never updated, deleted or truncated; the same unit twice under one parent is refused by the unique constraint.
 * The org unit is a cross-schema reference without a foreign key, so a unit that does not exist is accepted here too (card section on
 * references: a dangling one is caught by the scope review, not at save).
 */
class CustomOrgUnitGuardIT {

    private CustomOrgUnitRows db;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new CustomOrgUnitRows(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private void roleVersionUnitIsRefused(UUID version) throws SQLException {
        db.refusedByTrigger(GUARD, ROLE_VERSION_UNIT_INSERT, UUID.randomUUID(), version, UUID.randomUUID(), false, T1, T1);
    }

    private void requestUnitIsRefused(UUID request) throws SQLException {
        db.refusedByTrigger(GUARD, REQUEST_UNIT_INSERT, UUID.randomUUID(), request, UUID.randomUUID(), T1, T1);
    }

    @Test
    @DisplayName("F-1 不变量 2 开放且范围类型为 CUSTOM_ORG_UNITS 的角色版本可以插入明细（含「含下级」真与假两种）；这是后面各条拒绝用例的对照")
    void anOpenCustomVersionTakesUnits() throws SQLException {
        UUID version = db.openVersion(db.role(), 1, "CUSTOM_ORG_UNITS", T0);

        db.accepted(ROLE_VERSION_UNIT_INSERT, UUID.randomUUID(), version, UUID.randomUUID(), false, T1, T1);
        db.accepted(ROLE_VERSION_UNIT_INSERT, UUID.randomUUID(), version, UUID.randomUUID(), true, T1, T1);
    }

    @Test
    @DisplayName("F-1 不变量 2 范围类型不是 CUSTOM_ORG_UNITS 的开放版本（ALL、SELF、OWN_ORG_UNIT、OWN_ORG_UNIT_SUBTREE、SELF_AND_DIRECT_REPORTS）插入明细被 guard_custom_org_unit_rows 以 23001 拒绝")
    void aVersionOfAnotherScopeKindTakesNoUnits() throws SQLException {
        for (String kind : new String[] {"ALL", "SELF", "OWN_ORG_UNIT", "OWN_ORG_UNIT_SUBTREE", "SELF_AND_DIRECT_REPORTS"}) {
            roleVersionUnitIsRefused(db.openVersion(db.role(), 1, kind, T0));
        }
    }

    @Test
    @DisplayName("F-1 不变量 2 已关闭的角色版本（范围类型虽是 CUSTOM_ORG_UNITS，但已被新版本取代）插入明细被 guard_custom_org_unit_rows 以 23001 拒绝；该角色的开放新版本仍可插入")
    void aClosedVersionTakesNoUnits() throws SQLException {
        UUID role = db.role();
        UUID closed = db.replacedVersion(role, 1, "CUSTOM_ORG_UNITS", T0, T1);
        UUID current = db.openVersion(role, 2, "CUSTOM_ORG_UNITS", T1);

        roleVersionUnitIsRefused(closed);
        db.accepted(ROLE_VERSION_UNIT_INSERT, UUID.randomUUID(), current, UUID.randomUUID(), false, T2, T2);
    }

    @Test
    @DisplayName("F-1 不变量 2 不存在的角色版本 ID 插入明细被 guard_custom_org_unit_rows 拒绝（触发器先于外键，同样 23001 且不带约束名）")
    void aMissingVersionTakesNoUnits() throws SQLException {
        roleVersionUnitIsRefused(UUID.randomUUID());
    }

    @Test
    @DisplayName("F-1 不变量 8 待确认（PENDING）且范围类型为 CUSTOM_ORG_UNITS 的 CREATE_ROLE 与 UPDATE_ROLE 申请可以插入申请子行；这是后面各条拒绝用例的对照")
    void aPendingRoleRequestTakesUnits() throws SQLException {
        for (String kind : new String[] {"CREATE_ROLE", "UPDATE_ROLE"}) {
            UUID request = db.request(kind, "PENDING", "CUSTOM_ORG_UNITS");

            db.accepted(REQUEST_UNIT_INSERT, UUID.randomUUID(), request, UUID.randomUUID(), T1, T1);
        }
    }

    @Test
    @DisplayName("F-1 不变量 8 已确认（CONFIRMED）与已拒绝（REJECTED）的申请插入申请子行被 guard_custom_org_unit_rows 以 23001 拒绝")
    void aDecidedRequestTakesNoUnits() throws SQLException {
        requestUnitIsRefused(db.request("CREATE_ROLE", "CONFIRMED", "CUSTOM_ORG_UNITS"));
        requestUnitIsRefused(db.request("UPDATE_ROLE", "CONFIRMED", "CUSTOM_ORG_UNITS"));
        requestUnitIsRefused(db.request("CREATE_ROLE", "REJECTED", "CUSTOM_ORG_UNITS"));
        requestUnitIsRefused(db.request("UPDATE_ROLE", "REJECTED", "CUSTOM_ORG_UNITS"));
    }

    @Test
    @DisplayName("F-1 不变量 8 待确认的申请但范围类型不是 CUSTOM_ORG_UNITS（含范围为空）、或类型是 REPLACE_ROLES，插入申请子行被 guard_custom_org_unit_rows 以 23001 拒绝；不存在的申请 ID 同样被拒")
    void aRequestOfAnotherKindOrScopeTakesNoUnits() throws SQLException {
        for (String scope : new String[] {"ALL", "SELF", "OWN_ORG_UNIT", "OWN_ORG_UNIT_SUBTREE", "SELF_AND_DIRECT_REPORTS"}) {
            requestUnitIsRefused(db.request("CREATE_ROLE", "PENDING", scope));
        }
        requestUnitIsRefused(db.request("UPDATE_ROLE", "PENDING", null));
        requestUnitIsRefused(db.request("REPLACE_ROLES", "PENDING", "CUSTOM_ORG_UNITS"));
        requestUnitIsRefused(UUID.randomUUID());
    }

    @Test
    @DisplayName("F-1 不变量 3 明细行写入后不可改：UPDATE 含下级、改部门、改父版本、只改 updated_at、原值写回，都被 guard_custom_org_unit_rows 以 23001 拒绝，行不变")
    void aDetailRowIsNeverUpdated() throws SQLException {
        UUID version = db.openVersion(db.role(), 1, "CUSTOM_ORG_UNITS", T0);
        UUID otherVersion = db.openVersion(db.role(), 1, "CUSTOM_ORG_UNITS", T0);
        UUID orgUnit = UUID.randomUUID();
        UUID row = db.roleVersionUnit(version, orgUnit, false);
        String where = " where role_version_custom_org_unit_id = ?";
        String update = "update " + ROLE_VERSION_UNITS + " set ";

        db.refusedByTrigger(GUARD, update + "is_descendants_included = true" + where, row);
        db.refusedByTrigger(GUARD, update + "org_unit_id = ?" + where, UUID.randomUUID(), row);
        db.refusedByTrigger(GUARD, update + "role_version_id = ?" + where, otherVersion, row);
        db.refusedByTrigger(GUARD, update + "updated_at = ?::timestamptz" + where, T2, row);
        db.refusedByTrigger(GUARD, update + "is_descendants_included = is_descendants_included" + where, row);

        assertThat(db.count("select count(*) from " + ROLE_VERSION_UNITS + " where role_version_custom_org_unit_id = ? and org_unit_id = ?"
                + " and not is_descendants_included", row, orgUnit)).isEqualTo(1);
    }

    @Test
    @DisplayName("F-1 不变量 3 明细行写入后不可删：逐行 DELETE 与整表 DELETE 被 guard_custom_org_unit_rows 以 23001 拒绝，行仍在")
    void aDetailRowIsNeverDeleted() throws SQLException {
        UUID version = db.openVersion(db.role(), 1, "CUSTOM_ORG_UNITS", T0);
        UUID row = db.roleVersionUnit(version, UUID.randomUUID(), true);

        db.refusedByTrigger(GUARD, "delete from " + ROLE_VERSION_UNITS + " where role_version_custom_org_unit_id = ?", row);
        db.refusedByTrigger(GUARD, "delete from " + ROLE_VERSION_UNITS);

        assertThat(db.count("select count(*) from " + ROLE_VERSION_UNITS + " where role_version_id = ?", version)).isEqualTo(1);
    }

    @Test
    @DisplayName("F-1 不变量 3 明细表 TRUNCATE（单表、CASCADE）被语句级触发器 guard_custom_org_unit_rows 以 23001 拒绝，行仍在")
    void aDetailTableIsNeverTruncated() throws SQLException {
        UUID version = db.openVersion(db.role(), 1, "CUSTOM_ORG_UNITS", T0);
        db.roleVersionUnit(version, UUID.randomUUID(), false);

        db.refusedByTrigger(GUARD, "truncate table " + ROLE_VERSION_UNITS);
        db.refusedByTrigger(GUARD, "truncate table " + ROLE_VERSION_UNITS + " cascade");

        assertThat(db.count("select count(*) from " + ROLE_VERSION_UNITS + " where role_version_id = ?", version)).isEqualTo(1);
    }

    @Test
    @DisplayName("F-1 不变量 3 / 8 申请子行同样写入后不可改、不可删、不可截断：UPDATE、逐行与整表 DELETE、TRUNCATE 都被 guard_custom_org_unit_rows 以 23001 拒绝，行仍在（确认之后也一样）")
    void aRequestRowIsNeverChanged() throws SQLException {
        UUID request = db.openRequest();
        UUID row = db.requestUnit(request, UUID.randomUUID());
        String where = " where authorization_request_custom_org_unit_id = ?";
        String update = "update " + REQUEST_UNITS + " set ";

        db.refusedByTrigger(GUARD, update + "org_unit_id = ?" + where, UUID.randomUUID(), row);
        db.refusedByTrigger(GUARD, update + "request_id = ?" + where, db.openRequest(), row);
        db.refusedByTrigger(GUARD, update + "updated_at = ?::timestamptz" + where, T2, row);
        db.refusedByTrigger(GUARD, update + "org_unit_id = org_unit_id" + where, row);
        db.refusedByTrigger(GUARD, "delete from " + REQUEST_UNITS + where, row);
        db.refusedByTrigger(GUARD, "delete from " + REQUEST_UNITS);
        db.refusedByTrigger(GUARD, "truncate table " + REQUEST_UNITS);

        db.setup("update platform_identity.authorization_requests set request_status = 'CONFIRMED', decided_by = ?, decided_at = ?::timestamptz where request_id = ?",
                UUID.randomUUID(), T1, request);
        db.refusedByTrigger(GUARD, "delete from " + REQUEST_UNITS + where, row);
        db.refusedByTrigger(GUARD, update + "org_unit_id = ?" + where, UUID.randomUUID(), row);
        assertThat(db.count("select count(*) from " + REQUEST_UNITS + " where request_id = ?", request)).isEqualTo(1);
    }

    @Test
    @DisplayName("F-1 不变量 1 同一角色版本内同一部门再插入被唯一约束 uk_role_version_custom_org_units_role_version_id_org_unit_id 以 23505 拒绝（含下级取值不同也一样）；不同版本同一部门、同一版本不同部门可以")
    void theSameUnitTwiceInOneVersionIsRefused() throws SQLException {
        UUID role = db.role();
        UUID first = db.openVersion(role, 1, "CUSTOM_ORG_UNITS", T0);
        UUID orgUnit = UUID.randomUUID();
        db.setup(ROLE_VERSION_UNIT_INSERT, UUID.randomUUID(), first, orgUnit, false, T0, T0);
        db.setup("update platform_identity.role_versions set effective_to_at = ?::timestamptz, updated_at = ?::timestamptz where role_version_id = ?", T1, T1, first);
        UUID second = db.openVersion(role, 2, "CUSTOM_ORG_UNITS", T1);
        db.setup(ROLE_VERSION_UNIT_INSERT, UUID.randomUUID(), second, orgUnit, false, T1, T1);

        String constraint = "uk_role_version_custom_org_units_role_version_id_org_unit_id";
        db.refusedByConstraint(UNIQUE_VIOLATION, constraint, ROLE_VERSION_UNIT_INSERT, UUID.randomUUID(), second, orgUnit, false, T2, T2);
        db.refusedByConstraint(UNIQUE_VIOLATION, constraint, ROLE_VERSION_UNIT_INSERT, UUID.randomUUID(), second, orgUnit, true, T2, T2);
        db.accepted(ROLE_VERSION_UNIT_INSERT, UUID.randomUUID(), second, UUID.randomUUID(), false, T2, T2);
    }

    @Test
    @DisplayName("F-1 不变量 8 同一申请内同一部门再插入被唯一约束 uk_authorization_request_custom_org_units_request_org_unit 以 23505 拒绝；另一申请同一部门、同一申请不同部门可以")
    void theSameUnitTwiceInOneRequestIsRefused() throws SQLException {
        UUID request = db.openRequest();
        UUID otherRequest = db.openRequest();
        UUID orgUnit = UUID.randomUUID();
        db.requestUnit(request, orgUnit);
        db.requestUnit(otherRequest, orgUnit);

        db.refusedByConstraint(UNIQUE_VIOLATION, "uk_authorization_request_custom_org_units_request_org_unit", REQUEST_UNIT_INSERT,
                UUID.randomUUID(), request, orgUnit, T2, T2);
        db.accepted(REQUEST_UNIT_INSERT, UUID.randomUUID(), request, UUID.randomUUID(), T2, T2);
    }

    @Test
    @DisplayName("F-1 引用一节：org_unit_id 跨 schema 引用不建外键，库内不存在的部门 ID 照样写入，两张子表都是；存放时不核对部门是否存在（悬空由范围复核兜底）")
    void aUnitThatDoesNotExistIsStored() throws SQLException {
        UUID version = db.openVersion(db.role(), 1, "CUSTOM_ORG_UNITS", T0);
        UUID request = db.openRequest();
        UUID nobody = UUID.randomUUID();

        db.roleVersionUnit(version, nobody, false);
        db.requestUnit(request, nobody);

        assertThat(db.count("select count(*) from " + ROLE_VERSION_UNITS + " where org_unit_id = ?", nobody)).isEqualTo(1);
        assertThat(db.count("select count(*) from " + REQUEST_UNITS + " where org_unit_id = ?", nobody)).isEqualTo(1);
    }
}
