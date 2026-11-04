package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.Employee;
import com.forcitis.one.organization.workforce.api.Gender;
import com.forcitis.one.organization.workforce.domain.OrgSnapshot;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.domain.Position;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.PositionStatus;
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrgSnapshotRepository implements OrgSnapshotRepository {
    private static final long WRITER_LOCK_KEY = 20261014131000L;

    private final JdbcClient jdbc;

    JdbcOrgSnapshotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockForWrite() {
        jdbc.sql("""
                select pg_advisory_xact_lock(:key)
                """).param("key", WRITER_LOCK_KEY).query().singleRow();
    }

    @Override
    public OrgSnapshot loadAll() {
        List<OrgUnit> units = jdbc.sql(OrgSnapshotRows.SELECT_ORG_UNITS).query((rs, n) -> OrgSnapshotRows.orgUnit(rs)).list();
        List<Position> positions = jdbc.sql("""
                select id, code, name, sort_order, status from organization_workforce.positions
                """)
                .query((rs, n) -> new Position(new PositionId(rs.getObject("id", UUID.class)), rs.getString("code"), rs.getString("name"),
                        OrgSnapshotRows.integer(rs, "sort_order"), PositionStatus.valueOf(rs.getString("status")))).list();
        List<StaffEmployee> staff = jdbc.sql(OrgSnapshotRows.SELECT_STAFF).query((rs, n) -> OrgSnapshotRows.staff(rs)).list();
        return new OrgSnapshot(units, positions, staff);
    }

    @Override
    public Set<OrgUnitId> findOrgUnitIds() {
        return jdbc.sql("""
                select id from organization_workforce.org_units
                """).query(UUID.class).list().stream()
                .map(OrgUnitId::new).collect(Collectors.toSet());
    }

    @Override
    public Set<PositionId> findPositionIds() {
        return jdbc.sql("""
                select id from organization_workforce.positions
                """).query(UUID.class).list().stream()
                .map(PositionId::new).collect(Collectors.toSet());
    }
}
