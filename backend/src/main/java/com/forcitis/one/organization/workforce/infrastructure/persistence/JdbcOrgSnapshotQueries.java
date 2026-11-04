package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import com.forcitis.one.kernel.BusinessZone;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrgSnapshotQueries implements OrgSnapshotQueries {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final BusinessZone zone;

    JdbcOrgSnapshotQueries(JdbcClient jdbc, Clock clock, BusinessZone zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public Optional<StaffEmployee> findStaff(EmployeeId employeeId) {
        return jdbc.sql(OrgSnapshotRows.SELECT_STAFF_ON_DAY + """
                 and s.id = :id
                """).param("day", zone.today(clock)).param("id", employeeId.value()).query((rs, n) -> OrgSnapshotRows.staff(rs)).optional();
    }

    @Override
    public boolean employeeExists(EmployeeId employeeId) {
        return Boolean.TRUE.equals(jdbc.sql("""
                select exists(select 1 from organization_workforce.employees where id = :id)
                """)
                .param("id", employeeId.value()).query(Boolean.class).single());
    }

    @Override
    public Optional<StaffEmployee> findStaffByWecomUser(WecomUserId wecomUserId) {
        return jdbc.sql(OrgSnapshotRows.SELECT_STAFF_ON_DAY + """
                 and s.wecom_user_id = :wecom order by (s.employment_status = 'EMPLOYED') desc, s.updated_at desc limit 1
                """)
                .param("day", zone.today(clock)).param("wecom", wecomUserId.value()).query((rs, n) -> OrgSnapshotRows.staff(rs)).optional();
    }

    @Override
    public Optional<StaffEmployee> findStaffByLoginName(String loginName) {
        return jdbc.sql(OrgSnapshotRows.SELECT_STAFF_ON_DAY + """
                 and s.login_name = :login order by (s.employment_status = 'EMPLOYED') desc, s.updated_at desc limit 1
                """)
                .param("day", zone.today(clock)).param("login", loginName).query((rs, n) -> OrgSnapshotRows.staff(rs)).optional();
    }

    @Override
    public List<EmployeeId> findEmployeeIdsByLoginName(String loginName) {
        return jdbc.sql("""
                select id from organization_workforce.employees where login_name = :login order by updated_at desc, id
                """)
                .param("login", loginName).query(UUID.class).list().stream().map(EmployeeId::new).toList();
    }

    @Override
    public List<EmployeeId> findEmployeeIdsByWecomUser(WecomUserId wecomUserId) {
        return jdbc.sql("""
                select id from organization_workforce.employees where wecom_user_id = :wecom order by updated_at desc, id
                """)
                .param("wecom", wecomUserId.value()).query(UUID.class).list().stream().map(EmployeeId::new).toList();
    }

    @Override
    public Optional<OrgUnit> findOrgUnit(OrgUnitId orgUnitId) {
        return jdbc.sql("""
                select u.id, u.code, case when v.org_unit_version_id is not null then v.name else u.name end as name,
                       u.parent_org_unit_id, u.sort_order, u.status,
                       case when exists (select 1 from organization_workforce.org_unit_versions x where x.org_unit_id = u.id) then v.head_employee_id
                            else u.head_employee_id end as head_employee_id,
                       u.regional_receivables_manager_id,
                       case when v.org_unit_version_id is not null then v.region_kind else u.region_category end as region_category,
                       case when v.org_unit_version_id is not null then v.is_delivery_unit else u.is_delivery_unit end as is_delivery_unit,
                       case when v.org_unit_version_id is not null then v.short_name else u.short_name end as short_name
                  from organization_workforce.org_units u
                  left join organization_workforce.org_unit_versions v on v.org_unit_id = u.id and v.superseded_at is null
                       and v.effective_from_date <= :day and (v.effective_to_date is null or :day < v.effective_to_date)
                 where u.id = :id
                """).param("day", zone.today(clock)).param("id", orgUnitId.value()).query((rs, n) -> OrgSnapshotRows.orgUnit(rs)).optional();
    }

    @Override
    public List<OrgUnit> findAllOrgUnits() {
        return jdbc.sql(OrgSnapshotRows.SELECT_ORG_UNITS_ON_DAY).param("day", zone.today(clock)).query((rs, n) -> OrgSnapshotRows.orgUnit(rs)).list();
    }

    @Override
    public List<OrgUnitId> findAncestors(OrgUnitId orgUnitId) {
        List<UUID> chain = jdbc.sql("""
                with recursive chain(id, depth) as (
                    select parent_org_unit_id, 1 from organization_workforce.org_units
                     where id = :id and parent_org_unit_id is not null
                    union all
                    select u.parent_org_unit_id, c.depth + 1 from chain c
                      join organization_workforce.org_units u on u.id = c.id
                     where u.parent_org_unit_id is not null and c.depth < 64
                )
                select id from chain order by depth
                """).param("id", orgUnitId.value()).query(UUID.class).list();
        Set<UUID> unique = new LinkedHashSet<>(chain);
        unique.remove(orgUnitId.value());
        return unique.stream().map(OrgUnitId::new).toList();
    }
}
