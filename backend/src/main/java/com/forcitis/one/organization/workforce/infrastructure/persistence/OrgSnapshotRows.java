package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.Employee;
import com.forcitis.one.organization.workforce.api.Gender;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** The SELECTs and row mappers of the org snapshot: the repository loads the snapshot rows for the reconciliation, the queries browse as of a business day. */
final class OrgSnapshotRows {
    private OrgSnapshotRows() {
    }

    static final String SELECT_ORG_UNITS = """
            select id, code, name, parent_org_unit_id, sort_order, status, head_employee_id,
                   regional_receivables_manager_id, region_category, is_delivery_unit, short_name
              from organization_workforce.org_units
            """;

    static final String SELECT_ORG_UNITS_ON_DAY = """
            select u.id, u.code, case when v.org_unit_version_id is not null then v.name else u.name end as name,
                   u.parent_org_unit_id, u.sort_order, u.status, u.head_employee_id, u.regional_receivables_manager_id,
                   case when v.org_unit_version_id is not null then v.region_kind else u.region_category end as region_category,
                   case when v.org_unit_version_id is not null then v.is_delivery_unit else u.is_delivery_unit end as is_delivery_unit,
                   case when v.org_unit_version_id is not null then v.short_name else u.short_name end as short_name
              from organization_workforce.org_units u
              left join organization_workforce.org_unit_versions v on v.org_unit_id = u.id and v.superseded_at is null
                   and v.effective_from_date <= :day and (v.effective_to_date is null or :day < v.effective_to_date)
            """;

    /** The snapshot rows as stored; for the reconciliation's own comparison only, readers use SELECT_STAFF_ON_DAY. */
    static final String SELECT_STAFF = """
            select e.id, e.employee_code, e.display_name, e.login_name, e.email, e.gender, e.wecom_user_id,
                   e.employment_status, e.is_fee_earner,
                   a.org_unit_id, a.position_id, a.reports_to_employee_id
              from organization_workforce.employees e
              join organization_workforce.assignments a on a.employee_id = e.id
            """;

    /**
     * The staff row as of {@code :day}. A person with any employee version is read from the versions in force that day (status and fee-earner flag from the
     * employee version, org unit, position and reporting line from the assignment version) and is absent when either is missing; everyone else from the
     * snapshot. Name, gender, email, WeCom account and code stay on the root row.
     */
    static final String SELECT_STAFF_ON_DAY = """
            select s.* from (
              select e.id, e.employee_code, e.display_name, e.login_name, e.email, e.gender, e.wecom_user_id, e.updated_at,
                     case when v.versioned then ev.employment_status else e.employment_status end as employment_status,
                     case when v.versioned then ev.is_fee_earner else e.is_fee_earner end as is_fee_earner,
                     case when v.versioned then av.org_unit_id else a.org_unit_id end as org_unit_id,
                     case when v.versioned then av.position_id else a.position_id end as position_id,
                     case when v.versioned then av.reports_to_employee_id else a.reports_to_employee_id end as reports_to_employee_id
                from organization_workforce.employees e
                cross join lateral (select exists (select 1 from organization_workforce.employee_versions x where x.employee_id = e.id) as versioned) v
                left join organization_workforce.employee_versions ev on v.versioned and ev.employee_id = e.id and ev.superseded_at is null
                     and ev.effective_from_date <= :day and (ev.effective_to_date is null or :day < ev.effective_to_date)
                left join organization_workforce.assignment_versions av on v.versioned and av.employee_id = e.id and av.assignment_kind = 'PRIMARY' and av.superseded_at is null
                     and av.effective_from_date <= :day and (av.effective_to_date is null or :day < av.effective_to_date)
                left join organization_workforce.assignments a on not v.versioned and a.employee_id = e.id
            ) s
            where s.org_unit_id is not null and s.employment_status is not null
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
