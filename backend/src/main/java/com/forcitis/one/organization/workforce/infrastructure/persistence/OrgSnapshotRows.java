package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.Employee;
import com.forcitis.one.organization.workforce.domain.Gender;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** The SELECTs and row mappers of the org snapshot, shared by the repository (loading for a sync) and the queries (browsing). */
final class OrgSnapshotRows {
    private OrgSnapshotRows() {
    }

    static final String SELECT_ORG_UNITS = """
            select id, code, name, parent_org_unit_id, sort_order, status, head_employee_id,
                   regional_receivables_manager_id, region_category, is_delivery_unit, short_name
              from organization_workforce.org_units
            """;

    static final String SELECT_STAFF = """
            select e.id, e.employee_code, e.display_name, e.login_name, e.email, e.gender, e.wecom_user_id,
                   e.employment_status, e.is_fee_earner,
                   a.org_unit_id, a.position_id, a.reports_to_employee_id
              from organization_workforce.employees e
              join organization_workforce.assignments a on a.employee_id = e.id
            """;


    static OrgUnit orgUnit(ResultSet rs) throws SQLException {
        return new OrgUnit(new OrgUnitId(rs.getObject("id", UUID.class)), rs.getString("code"), rs.getString("name"),
                orgUnitId(rs, "parent_org_unit_id"), integer(rs, "sort_order"),
                OrgUnitStatus.valueOf(rs.getString("status")), employeeId(rs, "head_employee_id"),
                employeeId(rs, "regional_receivables_manager_id"),
                rs.getString("region_category") == null ? null : RegionCategory.valueOf(rs.getString("region_category")),
                rs.getBoolean("is_delivery_unit"), rs.getString("short_name"));
    }

    static StaffEmployee staff(ResultSet rs) throws SQLException {
        EmployeeId id = new EmployeeId(rs.getObject("id", UUID.class));
        String wecom = rs.getString("wecom_user_id");
        String gender = rs.getString("gender");
        Employee employee = new Employee(id, rs.getString("employee_code"), rs.getString("display_name"),
                rs.getString("login_name"), rs.getString("email"), gender == null ? null : Gender.valueOf(gender),
                wecom == null ? null : new WecomUserId(wecom), EmploymentStatus.valueOf(rs.getString("employment_status")),
                rs.getBoolean("is_fee_earner"));
        OrgUnitId orgUnit = new OrgUnitId(rs.getObject("org_unit_id", UUID.class));
        UUID position = rs.getObject("position_id", UUID.class);
        PositionId positionId = position == null ? null : new PositionId(position);
        Assignment assignment = new Assignment(id, orgUnit, positionId, employeeId(rs, "reports_to_employee_id"));
        return new StaffEmployee(employee, assignment);
    }

    static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static EmployeeId employeeId(ResultSet rs, String column) throws SQLException {
        UUID value = rs.getObject(column, UUID.class);
        return value == null ? null : new EmployeeId(value);
    }

    static OrgUnitId orgUnitId(ResultSet rs, String column) throws SQLException {
        UUID value = rs.getObject(column, UUID.class);
        return value == null ? null : new OrgUnitId(value);
    }
}
