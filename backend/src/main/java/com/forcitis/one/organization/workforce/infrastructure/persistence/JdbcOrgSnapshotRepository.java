package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.Employee;
import com.forcitis.one.organization.workforce.domain.Gender;
import com.forcitis.one.organization.workforce.domain.OrgSnapshot;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.domain.Position;
import com.forcitis.one.organization.workforce.domain.PositionId;
import com.forcitis.one.organization.workforce.domain.PositionStatus;
import com.forcitis.one.organization.workforce.domain.Reconciliation;
import com.forcitis.one.organization.workforce.domain.RegisteredEmployee;
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrgSnapshotRepository implements OrgSnapshotRepository {
    private static final long WRITER_LOCK_KEY = 20261014131000L;

    private final JdbcClient jdbc;
    private final OrgHistory history;
    private final BusinessZone zone;

    JdbcOrgSnapshotRepository(JdbcClient jdbc, BusinessZone zone, IdGenerator ids) {
        this.jdbc = jdbc;
        this.history = new OrgHistory(jdbc, ids);
        this.zone = zone;
    }

    @Override
    public void lockForWrite() {
        jdbc.sql("select pg_advisory_xact_lock(:key)").param("key", WRITER_LOCK_KEY).query().singleRow();
    }

    @Override
    public OrgSnapshot loadAll() {
        List<OrgUnit> units = jdbc.sql(OrgSnapshotRows.SELECT_ORG_UNITS).query((rs, n) -> OrgSnapshotRows.orgUnit(rs)).list();
        List<Position> positions = jdbc.sql("select id, code, name, sort_order, status from organization_workforce.positions")
                .query((rs, n) -> new Position(new PositionId(rs.getObject("id", UUID.class)), rs.getString("code"), rs.getString("name"),
                        OrgSnapshotRows.integer(rs, "sort_order"), PositionStatus.valueOf(rs.getString("status")))).list();
        List<StaffEmployee> staff = jdbc.sql(OrgSnapshotRows.SELECT_STAFF).query((rs, n) -> OrgSnapshotRows.staff(rs)).list();
        return new OrgSnapshot(units, positions, staff);
    }

    @Override
    public void save(Reconciliation changes, SyncRunId syncRunId, Instant now, LocalDate snapshotDate) {
        UUID run = syncRunId == null ? null : syncRunId.value();
        Timestamp at = Timestamp.from(now);
        for (Position p : changes.positions()) {
            jdbc.sql("""
                    insert into organization_workforce.positions (id, code, name, sort_order, status, created_at, updated_at)
                    values (:id, :code, :name, :sortOrder, :status, :now, :now)
                    on conflict (id) do update set code = excluded.code, name = excluded.name, sort_order = excluded.sort_order,
                           status = excluded.status, updated_at = excluded.updated_at, version = positions.version + 1
                    """)
                    .param("id", p.positionId().value()).param("code", p.code()).param("name", p.name())
                    .param("sortOrder", p.sortOrder()).param("status", p.status().name()).param("now", at).update();
        }
        for (OrgUnit u : changes.orgUnits()) {
            upsertOrgUnit(u, run, at);
        }
        history.recordOrgUnits(changes.orgUnits(), snapshotDate);
        // The unique WeCom key covers employed people only; clearing the batch first makes the write order irrelevant.
        List<UUID> staffIds = changes.staff().stream().map(s -> s.employeeId().value()).toList();
        if (!staffIds.isEmpty()) {
            jdbc.sql("update organization_workforce.employees set wecom_user_id = null where id in (:ids) and wecom_user_id is not null")
                    .param("ids", staffIds).update();
        }
        for (StaffEmployee s : changes.staff()) {
            upsertStaff(s, at);
        }
        history.recordAssignments(changes.staff().stream().map(StaffEmployee::assignment).toList(), snapshotDate);
    }

    @Override
    public void saveRegisteredEmployee(RegisteredEmployee registered, Instant now) {
        LocalDate day = zone.dateOf(now);
        upsertStaff(registered.staff(), Timestamp.from(now));
        history.recordAssignments(List.of(registered.staff().assignment()), day);
        jdbc.sql("update organization_workforce.employees set event_count = :sequence where id = :id")
                .param("sequence", registered.eventSequenceAfterSave()).param("id", registered.staff().employeeId().value()).update();
    }

    @Override
    public long findEmployeeEventSequence(EmployeeId employeeId) {
        return jdbc.sql("select event_count from organization_workforce.employees where id = :id").param("id", employeeId.value())
                .query(Long.class).optional().orElse(0L);
    }

    private void upsertOrgUnit(OrgUnit u, UUID run, Timestamp now) {
        jdbc.sql("""
                insert into organization_workforce.org_units (id, code, name, parent_org_unit_id, sort_order, status,
                       head_employee_id, regional_receivables_manager_id, region_category, is_delivery_unit, short_name,
                       revoked_at, last_sync_run_id, created_at, updated_at)
                values (:id, :code, :name, :parent, :sortOrder, :status, :head, :manager, :category, :delivery, :shortName,
                        case when cast(:status as text) = 'REVOKED' then cast(:now as timestamptz) end, :run, :now, :now)
                on conflict (id) do update set code = excluded.code, name = excluded.name,
                       parent_org_unit_id = excluded.parent_org_unit_id, sort_order = excluded.sort_order,
                       status = excluded.status, head_employee_id = excluded.head_employee_id,
                       regional_receivables_manager_id = excluded.regional_receivables_manager_id,
                       region_category = excluded.region_category, is_delivery_unit = excluded.is_delivery_unit,
                       revoked_at = case when excluded.status = 'REVOKED' then coalesce(org_units.revoked_at, excluded.updated_at) end,
                       last_sync_run_id = excluded.last_sync_run_id, updated_at = excluded.updated_at, version = org_units.version + 1
                """)
                .param("id", u.orgUnitId().value()).param("code", u.code()).param("name", u.name())
                .param("parent", u.parentOrgUnitId() == null ? null : u.parentOrgUnitId().value())
                .param("sortOrder", u.sortOrder()).param("status", u.status().name())
                .param("head", u.headEmployeeId() == null ? null : u.headEmployeeId().value())
                .param("manager", u.regionalReceivablesManagerId() == null ? null : u.regionalReceivablesManagerId().value())
                .param("category", u.regionCategory() == null ? null : u.regionCategory().name())
                .param("delivery", u.deliveryUnit()).param("shortName", u.shortName())
                .param("run", run).param("now", now).update();
    }

    private void upsertStaff(StaffEmployee s, Timestamp now) {
        Employee e = s.employee();
        Assignment a = s.assignment();
        jdbc.sql("""
                insert into organization_workforce.employees (id, employee_code, display_name, login_name, email, gender,
                       wecom_user_id, employment_status, is_fee_earner, separated_at, created_at, updated_at, event_count)
                values (:id, :code, :name, :login, :email, :gender, :wecom, :status, :feeEarner,
                        case when cast(:status as text) = 'SEPARATED' then cast(:now as timestamptz) end, :now, :now, 0)
                on conflict (id) do update set employee_code = excluded.employee_code, display_name = excluded.display_name,
                       login_name = excluded.login_name, email = excluded.email, gender = excluded.gender,
                       wecom_user_id = excluded.wecom_user_id, employment_status = excluded.employment_status,
                       is_fee_earner = excluded.is_fee_earner,
                       separated_at = case when excluded.employment_status = 'SEPARATED'
                                           then coalesce(employees.separated_at, excluded.updated_at) end,
                       updated_at = excluded.updated_at, version = employees.version + 1
                """)
                .param("id", e.employeeId().value()).param("code", e.employeeCode()).param("name", e.displayName())
                .param("login", e.loginName()).param("email", e.email()).param("gender", e.gender() == null ? null : e.gender().name())
                .param("wecom", e.wecomUserId() == null ? null : e.wecomUserId().value())
                .param("status", e.employmentStatus().name()).param("feeEarner", e.feeEarner())
                .param("now", now).update();
        jdbc.sql("""
                insert into organization_workforce.assignments (employee_id, org_unit_id, position_id, reports_to_employee_id,
                       created_at, updated_at)
                values (:employee, :orgUnit, :position, :reportsTo, :now, :now)
                on conflict (employee_id) do update set org_unit_id = excluded.org_unit_id, position_id = excluded.position_id,
                       reports_to_employee_id = excluded.reports_to_employee_id, updated_at = excluded.updated_at,
                       version = assignments.version + 1
                """)
                .param("employee", a.employeeId().value()).param("orgUnit", a.orgUnitId().value())
                .param("position", a.positionId() == null ? null : a.positionId().value())
                .param("reportsTo", a.reportsToEmployeeId() == null ? null : a.reportsToEmployeeId().value())
                .param("now", now).update();
    }

    @Override
    public Set<OrgUnitId> findOrgUnitIds() {
        return jdbc.sql("select id from organization_workforce.org_units").query(UUID.class).list().stream()
                .map(OrgUnitId::new).collect(Collectors.toSet());
    }

    @Override
    public Set<PositionId> findPositionIds() {
        return jdbc.sql("select id from organization_workforce.positions").query(UUID.class).list().stream()
                .map(PositionId::new).collect(Collectors.toSet());
    }

    @Override
    public int updateShortNames(Map<OrgUnitId, String> shortNames, Instant now) {
        int changed = 0;
        Timestamp at = Timestamp.from(now);
        for (Map.Entry<OrgUnitId, String> entry : shortNames.entrySet()) {
            changed += jdbc.sql("""
                    update organization_workforce.org_units set short_name = :name, updated_at = :now, version = version + 1
                     where id = :id and short_name is distinct from cast(:name as text)
                    """)
                    .param("name", entry.getValue()).param("now", at).param("id", entry.getKey().value()).update();
        }
        return changed;
    }
}
