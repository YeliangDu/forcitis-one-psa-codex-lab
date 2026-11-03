package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignQueries;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAttributionCountersignQueries implements AttributionCountersignQueries {
    /**
     * A personnel command carries the employee and the legal entities the countersign is about; an org-structure command carries neither (they differ per employee), so
     * those columns stay empty. The previous org unit is the one the assignment holds the day before the command takes effect.
     */
    private static final String SELECT = """
            select c.attribution_countersign_id as id, c.personnel_command_id,
                   coalesce(pc.command_kind, oc.command_kind) as command_kind, coalesce(pc.status, oc.status) as command_status,
                   coalesce(pc.effective_date, oc.effective_date) as effective_date,
                   c.employee_id, emp.display_name as employee_display_name, emp.employee_code as employee_number,
                   c.request_kind, c.request_sequence_number, c.decision_kind,
                   c.legal_entity_id, le.name as legal_entity_name,
                   pc.countersign_previous_legal_entity_id as previous_legal_entity_id, ple.name as previous_legal_entity_name,
                   pc.countersign_new_legal_entity_id as new_legal_entity_id, nle.name as new_legal_entity_name,
                   c.requested_at, c.requested_by, c.due_on_date, c.escalation_level_number,
                   previous_unit.id as previous_org_unit_id, previous_unit.name as previous_org_unit_name,
                   new_unit.id as new_org_unit_id, new_unit.name as new_org_unit_name,
                   c.request_reason, c.decided_at, c.decided_by, c.decision_reason
              from organization_workforce.attribution_countersigns c
              join organization_workforce.legal_entities le on le.id = c.legal_entity_id
              left join organization_workforce.personnel_commands pc on pc.personnel_command_id = c.personnel_command_id
              left join organization_workforce.org_structure_commands oc on oc.org_structure_command_id = c.org_structure_command_id
              left join organization_workforce.employees emp on emp.id = c.employee_id
              left join organization_workforce.legal_entities ple on ple.id = pc.countersign_previous_legal_entity_id
              left join organization_workforce.legal_entities nle on nle.id = pc.countersign_new_legal_entity_id
              left join organization_workforce.org_units new_unit on new_unit.id = pc.target_org_unit_id
              left join lateral (select u.id, u.name
                                   from organization_workforce.assignment_versions av join organization_workforce.org_units u on u.id = av.org_unit_id
                                  where pc.target_org_unit_id is not null and av.assignment_id = pc.target_assignment_id
                                    and av.effective_from_date < pc.effective_date and (av.effective_to_date is null or pc.effective_date - 1 < av.effective_to_date)
                                  order by av.effective_from_date desc, av.revision_number desc limit 1) previous_unit on true
            """;

    /** The alias is one of ours, never input; the same filter guards the row a cursor points at, so a cursor cannot probe a row the caller may not see. */
    private static final String VISIBLE = """
            (%1$s.legal_entity_id in (select v.legal_entity_id from organization_workforce.legal_entity_versions v
                                       where v.finance_head_employee_id = :head and v.effective_from_date <= :today and (v.effective_to_date is null or :today < v.effective_to_date))
             or (:escalated and %1$s.escalation_level_number >= 2))
            """;

    private final JdbcClient jdbc;

    JdbcAttributionCountersignQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Row> list(Visible visible, CountersignStatus status, @Nullable UUID after, int limit) {
        return jdbc.sql(SELECT + " where " + VISIBLE.formatted("c") + """
                   and ((cast(:decision as text) is null and c.decision_kind is null) or c.decision_kind = cast(:decision as text))
                   and (cast(:after as uuid) is null or (c.due_on_date, c.attribution_countersign_id) > (select k.due_on_date, k.attribution_countersign_id
                                                                                              from organization_workforce.attribution_countersigns k
                                                                                             where k.attribution_countersign_id = cast(:after as uuid) and
                """ + VISIBLE.formatted("k") + """
                   ))
                 order by c.due_on_date, c.attribution_countersign_id
                 limit :limit
                """)
                .param("head", visible.head().value()).param("today", visible.today()).param("escalated", visible.escalated()).param("decision", status == CountersignStatus.OPEN ? null : status.name(), java.sql.Types.VARCHAR)
                .param("after", after, java.sql.Types.OTHER).param("limit", limit)
                .query((rs, n) -> row(rs)).list();
    }

    @Override
    public Optional<Row> find(Visible visible, AttributionCountersignId id) {
        return jdbc.sql(SELECT + " where c.attribution_countersign_id = :id and " + VISIBLE.formatted("c"))
                .param("id", id.value()).param("head", visible.head().value()).param("today", visible.today()).param("escalated", visible.escalated())
                .query((rs, n) -> row(rs)).optional();
    }

    @Override
    public List<Row> sameCommand(AttributionCountersignId id) {
        return jdbc.sql(SELECT + """
                  join organization_workforce.attribution_countersigns s on s.attribution_countersign_id = :id
                   and c.personnel_command_id is not distinct from s.personnel_command_id and c.org_structure_command_id is not distinct from s.org_structure_command_id
                 order by c.requested_at, c.request_kind, c.request_sequence_number, c.attribution_countersign_id
                """)
                .param("id", id.value()).query((rs, n) -> row(rs)).list();
    }

    @Override
    public Map<UUID, String> employeeNames(Collection<UUID> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        jdbc.sql("select id, display_name from organization_workforce.employees where id in (:ids)").param("ids", employeeIds)
                .query((rs, n) -> names.put(rs.getObject("id", UUID.class), rs.getString("display_name"))).list();
        return names;
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), typed(rs, "personnel_command_id", PersonnelCommandId::new), rs.getString("command_kind"), rs.getString("command_status"),
                rs.getObject("effective_date", LocalDate.class), typed(rs, "employee_id", EmployeeId::new), rs.getString("employee_display_name"),
                rs.getString("employee_number"), rs.getString("request_kind"), rs.getInt("request_sequence_number"),
                CountersignStatus.ofDecision(rs.getString("decision_kind")), new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)), rs.getString("legal_entity_name"),
                typed(rs, "previous_legal_entity_id", LegalEntityId::new), rs.getString("previous_legal_entity_name"), typed(rs, "new_legal_entity_id", LegalEntityId::new),
                rs.getString("new_legal_entity_name"), instant(rs, "requested_at"), rs.getObject("requested_by", UUID.class), rs.getObject("due_on_date", LocalDate.class),
                rs.getInt("escalation_level_number"), typed(rs, "previous_org_unit_id", OrgUnitId::new), rs.getString("previous_org_unit_name"),
                typed(rs, "new_org_unit_id", OrgUnitId::new), rs.getString("new_org_unit_name"), rs.getString("request_reason"), nullableInstant(rs, "decided_at"),
                rs.getObject("decided_by", UUID.class), rs.getString("decision_reason"));
    }

    private static <T> @Nullable T typed(ResultSet rs, String column, Function<UUID, T> wrap) throws SQLException {
        UUID value = rs.getObject(column, UUID.class);
        return value == null ? null : wrap.apply(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static @Nullable Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime at = rs.getObject(column, OffsetDateTime.class);
        return at == null ? null : at.toInstant();
    }
}
