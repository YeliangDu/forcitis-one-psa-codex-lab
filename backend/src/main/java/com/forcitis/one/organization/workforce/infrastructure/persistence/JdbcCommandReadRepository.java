package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import com.forcitis.one.organization.workforce.domain.HoldStatus;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCommandReadRepository implements CommandReadQueries {
    // Snapshot-only people still use assignments; once versioned, a gap must never revive the snapshot post.
    private static final String PRIMARY_ASSIGNMENTS = """
            with current_primary as (
                select av.employee_id, av.org_unit_id
                  from organization_workforce.assignment_versions av
                 where av.assignment_kind = 'PRIMARY' and av.superseded_at is null
                   and av.effective_from_date <= :day and (av.effective_to_date is null or :day < av.effective_to_date)
                union all
                select a.employee_id, a.org_unit_id from organization_workforce.assignments a
                 where not exists (select 1 from organization_workforce.employee_versions ev where ev.employee_id = a.employee_id)
                   and not exists (select 1 from organization_workforce.assignment_versions av where av.employee_id = a.employee_id and av.assignment_kind = 'PRIMARY')
            )
            """;
    private static final String PERSONNEL = """
            select c.personnel_command_id, c.command_kind, c.employee_id, e.display_name, e.employee_code, c.effective_date,
                   c.status, c.created_by, c.created_at, c.withdrawn_by, c.withdrawn_at, c.withdrawal_reason, h.separation_hold_id, h.status as hold_status
              from organization_workforce.personnel_commands c
              join organization_workforce.employees e on e.id = c.employee_id
              left join organization_workforce.separation_holds h on h.personnel_command_id = c.personnel_command_id
             where (:everything or exists (select 1 from current_primary a where a.employee_id = c.employee_id and a.org_unit_id = any(:units)))
            """;
    private static final String STRUCTURE = """
            select org_structure_command_id, command_kind, target_kind, target_org_unit_id, target_position_id, effective_date, status,
                   affected_employee_count, transferred_employee_count, created_at
              from organization_workforce.org_structure_commands
            """;
    private static final String HOLDS = """
            select h.separation_hold_id, h.personnel_command_id, h.employee_id, e.display_name, e.employee_code, a.org_unit_id,
                   case when uv.org_unit_version_id is not null then uv.name else u.name end as org_unit_name,
                   c.effective_date, h.status, h.created_at
              from organization_workforce.separation_holds h
              join organization_workforce.personnel_commands c on c.personnel_command_id = h.personnel_command_id
              join organization_workforce.employees e on e.id = h.employee_id
              left join current_primary a on a.employee_id = h.employee_id
              left join organization_workforce.org_units u on u.id = a.org_unit_id
              left join organization_workforce.org_unit_versions uv on uv.org_unit_id = u.id and uv.superseded_at is null
                   and uv.effective_from_date <= :day and (uv.effective_to_date is null or :day < uv.effective_to_date)
            """;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final BusinessZone zone;

    JdbcCommandReadRepository(JdbcClient jdbc, Clock clock, BusinessZone zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public List<Personnel> personnel(Scope scope, @Nullable CommandStatus status, @Nullable PersonnelCommandKind kind, @Nullable EmployeeId employee,
                                    @Nullable Position after, int limit) {
        return position(scoped(jdbc.sql(PRIMARY_ASSIGNMENTS + PERSONNEL + """
                 and (cast(:status as text) is null or c.status = :status)
                 and (cast(:kind as text) is null or c.command_kind = :kind)
                 and (cast(:employee as uuid) is null or c.employee_id = :employee)
                 and (cast(:afterAt as timestamptz) is null or (c.created_at, c.personnel_command_id) > (:afterAt, :afterKey))
                 order by c.created_at, c.personnel_command_id limit :limit
                """), scope), after).param("limit", limit)
                .param("status", status == null ? null : status.name(), Types.VARCHAR)
                .param("kind", kind == null ? null : kind.name(), Types.VARCHAR)
                .param("employee", employee == null ? null : employee.value(), Types.OTHER)
                .query((rs, n) -> personnel(rs)).list();
    }

    @Override
    public Optional<Personnel> personnel(Scope scope, PersonnelCommandId id) {
        return scoped(jdbc.sql(PRIMARY_ASSIGNMENTS + PERSONNEL + """
                 and c.personnel_command_id = :id
                """), scope).param("id", id.value()).query((rs, n) -> personnel(rs)).optional();
    }

    @Override
    public Optional<EmployeeId> employeeOf(PersonnelCommandId id) {
        return jdbc.sql("""
                select employee_id from organization_workforce.personnel_commands where personnel_command_id = :id
                """).param("id", id.value()).query((rs, n) -> new EmployeeId(rs.getObject("employee_id", UUID.class))).optional();
    }

    @Override
    public List<Countersign> countersigns(OrgStructureCommandId id) {
        return jdbc.sql("""
                select attribution_countersign_id, request_kind, legal_entity_id, decision_kind
                  from organization_workforce.attribution_countersigns where org_structure_command_id = :id
                 order by request_kind, request_sequence_number, legal_entity_id, attribution_countersign_id
                """).param("id", id.value()).query((rs, n) -> new Countersign(new AttributionCountersignId(rs.getObject("attribution_countersign_id", UUID.class)),
                        CountersignRequestKind.valueOf(rs.getString("request_kind")), new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)),
                        CountersignStatus.ofDecision(rs.getString("decision_kind")))).list();
    }

    @Override
    public List<Structure> structures(@Nullable Position after, int limit) {
        return position(jdbc.sql(STRUCTURE + """
                 where (cast(:afterAt as timestamptz) is null or (created_at, org_structure_command_id) > (:afterAt, :afterKey))
                 order by created_at, org_structure_command_id limit :limit
                """), after).param("limit", limit).query((rs, n) -> structure(rs)).list();
    }

    @Override
    public Optional<Structure> structure(OrgStructureCommandId id) {
        return jdbc.sql(STRUCTURE + """
                 where org_structure_command_id = :id
                """).param("id", id.value()).query((rs, n) -> structure(rs)).optional();
    }

    @Override
    public List<Hold> holds(@Nullable Position after, int limit) {
        return position(jdbc.sql(PRIMARY_ASSIGNMENTS + HOLDS + """
                 where h.status = 'HELD'
                   and (cast(:afterAt as timestamptz) is null or (h.created_at, h.separation_hold_id) > (:afterAt, :afterKey))
                 order by h.created_at, h.separation_hold_id limit :limit
                """), after).param("day", zone.today(clock)).param("limit", limit).query((rs, n) -> hold(rs)).list();
    }

    @Override
    public Optional<Hold> hold(SeparationHoldId id) {
        return jdbc.sql(PRIMARY_ASSIGNMENTS + HOLDS + """
                 where h.separation_hold_id = :id
                """).param("day", zone.today(clock)).param("id", id.value()).query((rs, n) -> hold(rs)).optional();
    }

    private JdbcClient.StatementSpec scoped(JdbcClient.StatementSpec sql, Scope scope) {
        return sql.param("day", zone.today(clock)).param("everything", scope.everything())
                .param("units", scope.units().stream().map(OrgUnitId::value).toArray(UUID[]::new));
    }

    private static JdbcClient.StatementSpec position(JdbcClient.StatementSpec sql, @Nullable Position after) {
        return sql.param("afterAt", after == null ? null : OffsetDateTime.ofInstant(after.createdAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("afterKey", after == null ? null : after.key(), Types.OTHER);
    }

    private static Personnel personnel(ResultSet rs) throws SQLException {
        UUID withdrawnBy = rs.getObject("withdrawn_by", UUID.class);
        UUID hold = rs.getObject("separation_hold_id", UUID.class);
        String holdStatus = rs.getString("hold_status");
        return new Personnel(new PersonnelCommandId(rs.getObject("personnel_command_id", UUID.class)), PersonnelCommandKind.valueOf(rs.getString("command_kind")),
                new EmployeeId(rs.getObject("employee_id", UUID.class)), rs.getString("display_name"), rs.getString("employee_code"), rs.getObject("effective_date", LocalDate.class),
                CommandStatus.valueOf(rs.getString("status")), new UserId(rs.getObject("created_by", UUID.class)), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                withdrawnBy == null ? null : new UserId(withdrawnBy), moment(rs, "withdrawn_at"), rs.getString("withdrawal_reason"),
                hold == null ? null : new SeparationHoldId(hold), holdStatus == null ? null : HoldStatus.valueOf(rs.getString("hold_status")));
    }

    private static Structure structure(ResultSet rs) throws SQLException {
        UUID unit = rs.getObject("target_org_unit_id", UUID.class);
        UUID position = rs.getObject("target_position_id", UUID.class);
        return new Structure(new OrgStructureCommandId(rs.getObject("org_structure_command_id", UUID.class)), OrgStructureCommandKind.valueOf(rs.getString("command_kind")),
                OrgStructureTargetKind.valueOf(rs.getString("target_kind")), unit == null ? null : new OrgUnitId(unit), position == null ? null : new PositionId(position),
                rs.getObject("effective_date", LocalDate.class), OrgCommandStatus.valueOf(rs.getString("status")),
                rs.getObject("affected_employee_count", Integer.class), rs.getObject("transferred_employee_count", Integer.class), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static Hold hold(ResultSet rs) throws SQLException {
        UUID unit = rs.getObject("org_unit_id", UUID.class);
        return new Hold(new SeparationHoldId(rs.getObject("separation_hold_id", UUID.class)), new PersonnelCommandId(rs.getObject("personnel_command_id", UUID.class)),
                new EmployeeId(rs.getObject("employee_id", UUID.class)), rs.getString("display_name"), rs.getString("employee_code"),
                unit == null ? null : new OrgUnitId(unit), rs.getString("org_unit_name"), rs.getObject("effective_date", LocalDate.class), HoldStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static @Nullable Instant moment(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
