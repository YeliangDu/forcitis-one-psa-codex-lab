package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
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
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.organization.workforce.domain.StaffMember;
import com.forcitis.one.organization.workforce.domain.SyncRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL touches only the organization_workforce schema. Writes are upserts of exactly the changed rows. */
@Repository
class JdbcOrgSnapshotRepository implements OrgSnapshotRepository {

    /** Fixed key of the advisory lock that serialises snapshot writers. */
    private static final long WRITER_LOCK_KEY = 20261014131000L;

    private static final String SELECT_ORG_UNITS = """
            select id, code, name, parent_org_unit_id, sort_order, status, head_employee_id,
                   regional_receivables_manager_id, region_category, is_delivery_unit, short_name
              from organization_workforce.org_units
            """;

    private static final String SELECT_STAFF = """
            select e.id, e.employee_code, e.display_name, e.login_name, e.email, e.gender, e.wecom_user_id,
                   e.employment_status, e.is_fee_earner,
                   a.org_unit_id, a.position_id, a.reports_to_employee_id
              from organization_workforce.employees e
              join organization_workforce.assignments a on a.employee_id = e.id
            """;

    private final JdbcClient jdbc;

    JdbcOrgSnapshotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockForWrite() {
        jdbc.sql("select pg_advisory_xact_lock(:key)").param("key", WRITER_LOCK_KEY).query().singleRow();
    }

    @Override
    public OrgSnapshot loadAll() {
        List<OrgUnit> units = jdbc.sql(SELECT_ORG_UNITS).query((rs, n) -> orgUnit(rs)).list();
        List<Position> positions = jdbc.sql("select id, code, name, sort_order, status from organization_workforce.positions")
                .query((rs, n) -> new Position(new PositionId(rs.getLong("id")), rs.getString("code"), rs.getString("name"),
                        integer(rs, "sort_order"), PositionStatus.valueOf(rs.getString("status")))).list();
        List<StaffMember> staff = jdbc.sql(SELECT_STAFF).query((rs, n) -> staff(rs)).list();
        return new OrgSnapshot(units, positions, staff);
    }

    @Override
    public void save(Reconciliation changes, SyncRunId syncRunId, Instant now) {
        Long run = syncRunId == null ? null : syncRunId.value();
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
        // The unique WeCom key covers employed people only; clearing the batch first makes the write order irrelevant.
        List<Long> staffIds = changes.staff().stream().map(s -> s.userId().value()).toList();
        if (!staffIds.isEmpty()) {
            jdbc.sql("update organization_workforce.employees set wecom_user_id = null where id in (:ids) and wecom_user_id is not null")
                    .param("ids", staffIds).update();
        }
        for (StaffMember s : changes.staff()) {
            upsertStaff(s, run, at);
        }
    }

    @Override
    public void saveStaffMember(StaffMember member, SyncRunId syncRunId, Instant now) {
        upsertStaff(member, syncRunId == null ? null : syncRunId.value(), Timestamp.from(now));
    }

    private void upsertOrgUnit(OrgUnit u, Long run, Timestamp now) {
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

    private void upsertStaff(StaffMember s, Long run, Timestamp now) {
        Employee e = s.employee();
        Assignment a = s.assignment();
        jdbc.sql("""
                insert into organization_workforce.employees (id, employee_code, display_name, login_name, email, gender,
                       wecom_user_id, employment_status, is_fee_earner, separated_at, last_sync_run_id, created_at, updated_at)
                values (:id, :code, :name, :login, :email, :gender, :wecom, :status, :feeEarner,
                        case when cast(:status as text) = 'SEPARATED' then cast(:now as timestamptz) end, :run, :now, :now)
                on conflict (id) do update set employee_code = excluded.employee_code, display_name = excluded.display_name,
                       login_name = excluded.login_name, email = excluded.email, gender = excluded.gender,
                       wecom_user_id = excluded.wecom_user_id, employment_status = excluded.employment_status,
                       is_fee_earner = excluded.is_fee_earner,
                       separated_at = case when excluded.employment_status = 'SEPARATED'
                                           then coalesce(employees.separated_at, excluded.updated_at) end,
                       last_sync_run_id = excluded.last_sync_run_id, updated_at = excluded.updated_at, version = employees.version + 1
                """)
                .param("id", e.userId().value()).param("code", e.employeeCode()).param("name", e.displayName())
                .param("login", e.loginName()).param("email", e.email()).param("gender", e.gender() == null ? null : e.gender().name())
                .param("wecom", e.wecomUserId() == null ? null : e.wecomUserId().value())
                .param("status", e.employmentStatus().name()).param("feeEarner", e.feeEarner())
                .param("run", run).param("now", now).update();
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
    public Optional<StaffMember> findStaff(UserId userId) {
        return jdbc.sql(SELECT_STAFF + " where e.id = :id").param("id", userId.value()).query((rs, n) -> staff(rs)).optional();
    }

    @Override
    public Optional<StaffMember> findStaffByWecomUserId(WecomUserId wecomUserId) {
        return jdbc.sql(SELECT_STAFF + " where e.wecom_user_id = :wecom order by (e.employment_status = 'EMPLOYED') desc, e.updated_at desc limit 1")
                .param("wecom", wecomUserId.value()).query((rs, n) -> staff(rs)).optional();
    }

    @Override
    public Optional<StaffMember> findStaffByLoginName(String loginName) {
        return jdbc.sql(SELECT_STAFF + " where e.login_name = :login order by (e.employment_status = 'EMPLOYED') desc, e.updated_at desc limit 1")
                .param("login", loginName).query((rs, n) -> staff(rs)).optional();
    }

    @Override
    public Optional<OrgUnit> findOrgUnit(OrgUnitId orgUnitId) {
        return jdbc.sql(SELECT_ORG_UNITS + " where id = :id").param("id", orgUnitId.value()).query((rs, n) -> orgUnit(rs)).optional();
    }

    @Override
    public List<OrgUnit> findAllOrgUnits() {
        return jdbc.sql(SELECT_ORG_UNITS).query((rs, n) -> orgUnit(rs)).list();
    }

    @Override
    public List<OrgUnitId> findAncestors(OrgUnitId orgUnitId) {
        List<Long> chain = jdbc.sql("""
                with recursive chain(id, depth) as (
                    select parent_org_unit_id, 1 from organization_workforce.org_units
                     where id = :id and parent_org_unit_id is not null
                    union all
                    select u.parent_org_unit_id, c.depth + 1 from chain c
                      join organization_workforce.org_units u on u.id = c.id
                     where u.parent_org_unit_id is not null and c.depth < 64
                )
                select id from chain order by depth
                """).param("id", orgUnitId.value()).query(Long.class).list();
        Set<Long> unique = new LinkedHashSet<>(chain);
        unique.remove(orgUnitId.value());
        return unique.stream().map(OrgUnitId::new).toList();
    }

    @Override
    public Set<OrgUnitId> findOrgUnitIds() {
        return jdbc.sql("select id from organization_workforce.org_units").query(Long.class).list().stream()
                .map(OrgUnitId::new).collect(Collectors.toSet());
    }

    @Override
    public Set<PositionId> findPositionIds() {
        return jdbc.sql("select id from organization_workforce.positions").query(Long.class).list().stream()
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

    // ---------------------------------------------------------------- row mapping

    private static OrgUnit orgUnit(ResultSet rs) throws SQLException {
        return new OrgUnit(new OrgUnitId(rs.getLong("id")), rs.getString("code"), rs.getString("name"),
                orgUnitId(rs, "parent_org_unit_id"), integer(rs, "sort_order"),
                OrgUnitStatus.valueOf(rs.getString("status")), userId(rs, "head_employee_id"),
                userId(rs, "regional_receivables_manager_id"),
                rs.getString("region_category") == null ? null : RegionCategory.valueOf(rs.getString("region_category")),
                rs.getBoolean("is_delivery_unit"), rs.getString("short_name"));
    }

    private static StaffMember staff(ResultSet rs) throws SQLException {
        UserId id = new UserId(rs.getLong("id"));
        String wecom = rs.getString("wecom_user_id");
        String gender = rs.getString("gender");
        Employee employee = new Employee(id, rs.getString("employee_code"), rs.getString("display_name"),
                rs.getString("login_name"), rs.getString("email"), gender == null ? null : Gender.valueOf(gender),
                wecom == null ? null : new WecomUserId(wecom), EmploymentStatus.valueOf(rs.getString("employment_status")),
                rs.getBoolean("is_fee_earner"));
        OrgUnitId orgUnit = new OrgUnitId(rs.getLong("org_unit_id"));
        long position = rs.getLong("position_id");
        PositionId positionId = rs.wasNull() ? null : new PositionId(position);
        Assignment assignment = new Assignment(id, orgUnit, positionId, userId(rs, "reports_to_employee_id"));
        return new StaffMember(employee, assignment);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static UserId userId(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : new UserId(value);
    }

    private static OrgUnitId orgUnitId(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : new OrgUnitId(value);
    }
}
