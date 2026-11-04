package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.Employee;
import com.forcitis.one.organization.workforce.domain.OrgSnapshot;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.Position;
import com.forcitis.one.organization.workforce.domain.Reconciliation;
import com.forcitis.one.organization.workforce.domain.SnapshotReconciler;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Puts what the source holds into the snapshot tables, the way the retired sync once did, so tests that need a populated snapshot (login lookups,
 * directory reads, reconciliation) have one. Test fixture only: production has no writer of these rows any more (#934).
 */
public final class SnapshotSeeder {
    private SnapshotSeeder() {
    }

    public static void seed(JdbcClient jdbc, SourceOrgSnapshot source) {
        Reconciliation rows = SnapshotReconciler.reconcile(OrgSnapshot.empty(), source);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Position p : rows.positions()) {
            jdbc.sql("""
                    insert into organization_workforce.positions (id, code, name, sort_order, status, created_at, updated_at)
                    values (:id, :code, :name, :sortOrder, :status, :now, :now)
                    on conflict (id) do update set code = excluded.code, name = excluded.name, sort_order = excluded.sort_order, status = excluded.status
                    """)
                    .param("id", p.positionId().value()).param("code", p.code()).param("name", p.name())
                    .param("sortOrder", p.sortOrder()).param("status", p.status().name()).param("now", now).update();
        }
        // Units, people and assignments refer to each other: rows first, then the references, so the order of the source does not matter.
        for (OrgUnit u : rows.orgUnits()) {
            jdbc.sql("""
                    insert into organization_workforce.org_units (id, code, name, parent_org_unit_id, sort_order, status, head_employee_id,
                           regional_receivables_manager_id, region_category, is_delivery_unit, short_name, revoked_at, created_at, updated_at)
                    values (:id, :code, :name, null, :sortOrder, :status, null, null, :category, :delivery, :shortName,
                            case when cast(:status as text) = 'REVOKED' then cast(:now as timestamptz) end, :now, :now)
                    on conflict (id) do update set code = excluded.code, name = excluded.name,
                           sort_order = excluded.sort_order, status = excluded.status, region_category = excluded.region_category,
                           is_delivery_unit = excluded.is_delivery_unit, revoked_at = excluded.revoked_at
                    """)
                    .param("id", u.orgUnitId().value()).param("code", u.code()).param("name", u.name()).param("sortOrder", u.sortOrder())
                    .param("status", u.status().name())
                    .param("category", u.regionCategory() == null ? null : u.regionCategory().name()).param("delivery", u.deliveryUnit())
                    .param("shortName", u.shortName()).param("now", now).update();
        }
        for (StaffEmployee s : rows.staff()) {
            Employee e = s.employee();
            jdbc.sql("""
                    insert into organization_workforce.employees (id, employee_code, display_name, email, gender, wecom_user_id,
                           employment_status, is_fee_earner, separated_at, created_at, updated_at, event_count)
                    values (:id, :code, :name, :email, :gender, :wecom, :status, :feeEarner,
                            case when cast(:status as text) = 'SEPARATED' then cast(:now as timestamptz) end, :now, :now, 0)
                    on conflict (id) do update set display_name = excluded.display_name, email = excluded.email,
                           gender = excluded.gender, wecom_user_id = excluded.wecom_user_id, employment_status = excluded.employment_status,
                           is_fee_earner = excluded.is_fee_earner, separated_at = excluded.separated_at
                    """)
                    .param("id", e.employeeId().value()).param("code", e.employeeCode()).param("name", e.displayName())
                    .param("email", e.email()).param("gender", e.gender() == null ? null : e.gender().name())
                    .param("wecom", e.wecomUserId() == null ? null : e.wecomUserId().value())
                    .param("status", e.employmentStatus().name()).param("feeEarner", e.feeEarner()).param("now", now).update();
        }
        for (StaffEmployee s : rows.staff()) {
            Assignment a = s.assignment();
            jdbc.sql("""
                    insert into organization_workforce.assignments (employee_id, org_unit_id, position_id, reports_to_employee_id, created_at, updated_at)
                    values (:employee, :orgUnit, :position, :reportsTo, :now, :now)
                    on conflict (employee_id) do update set org_unit_id = excluded.org_unit_id, position_id = excluded.position_id,
                           reports_to_employee_id = excluded.reports_to_employee_id
                    """)
                    .param("employee", a.employeeId().value()).param("orgUnit", a.orgUnitId().value())
                    .param("position", a.positionId() == null ? null : a.positionId().value())
                    .param("reportsTo", a.reportsToEmployeeId() == null ? null : a.reportsToEmployeeId().value()).param("now", now).update();
        }
        for (OrgUnit u : rows.orgUnits()) {
            jdbc.sql("""
                    update organization_workforce.org_units set parent_org_unit_id = :parent, head_employee_id = :head, regional_receivables_manager_id = :manager
                     where id = :id
                    """)
                    .param("id", u.orgUnitId().value())
                    .param("parent", u.parentOrgUnitId() == null ? null : u.parentOrgUnitId().value(), java.sql.Types.OTHER)
                    .param("head", u.headEmployeeId() == null ? null : u.headEmployeeId().value(), java.sql.Types.OTHER)
                    .param("manager", u.regionalReceivablesManagerId() == null ? null : u.regionalReceivablesManagerId().value(), java.sql.Types.OTHER).update();
        }
    }
}
