package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
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

    JdbcOrgSnapshotQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StaffEmployee> findStaff(EmployeeId employeeId) {
        return jdbc.sql(OrgSnapshotRows.SELECT_STAFF + """
                 where e.id = :id
                """).param("id", employeeId.value()).query((rs, n) -> OrgSnapshotRows.staff(rs)).optional();
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
        return jdbc.sql(OrgSnapshotRows.SELECT_STAFF + """
                 where e.wecom_user_id = :wecom order by (e.employment_status = 'EMPLOYED') desc, e.updated_at desc limit 1
                """)
                .param("wecom", wecomUserId.value()).query((rs, n) -> OrgSnapshotRows.staff(rs)).optional();
    }

    @Override
    public Optional<StaffEmployee> findStaffByLoginName(String loginName) {
        return jdbc.sql(OrgSnapshotRows.SELECT_STAFF + """
                 where e.login_name = :login order by (e.employment_status = 'EMPLOYED') desc, e.updated_at desc limit 1
                """)
                .param("login", loginName).query((rs, n) -> OrgSnapshotRows.staff(rs)).optional();
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
        return jdbc.sql(OrgSnapshotRows.SELECT_ORG_UNITS + """
                 where id = :id
                """).param("id", orgUnitId.value()).query((rs, n) -> OrgSnapshotRows.orgUnit(rs)).optional();
    }

    @Override
    public List<OrgUnit> findAllOrgUnits() {
        return jdbc.sql(OrgSnapshotRows.SELECT_ORG_UNITS).query((rs, n) -> OrgSnapshotRows.orgUnit(rs)).list();
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
