package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.BlockingArrival;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.RegionKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AssignmentKind;
import com.forcitis.one.organization.workforce.domain.ChangeSource;
import com.forcitis.one.organization.workforce.domain.HeldPost;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgCountersignBasis;
import com.forcitis.one.organization.workforce.domain.OrgEffects;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.OrgTimeline;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Org-structure commands and the version rows they write in the workforce schema. Rows are only inserted, superseded or closed (W2 design section 2); the triggers hold the rest. */
@Repository
class JdbcOrgStructureCommandRepository implements OrgStructureCommandRepository {
    private static final String COMMAND_COLUMNS = """
            select org_structure_command_id, command_kind, target_kind, target_org_unit_id, target_position_id, effective_date, status, target_code, target_name,
                   target_short_name, target_region_kind, is_target_delivery_unit, target_parent_org_unit_id, target_head_employee_id,
                   target_receivables_manager_employee_id, merge_into_org_unit_id, target_legal_entity_id, affected_employee_count, transferred_employee_count,
                   applied_at, created_at, created_by, version
              from organization_workforce.org_structure_commands
            """;
    private static final String OPEN_HOLDERS = """
            from organization_workforce.assignment_versions a join organization_workforce.employee_versions e on e.employee_id = a.employee_id
             where a.%s = :target and a.superseded_at is null and e.superseded_at is null and e.employment_status = 'EMPLOYED'
               and (a.effective_to_date is null or a.effective_to_date > :day) and (e.effective_to_date is null or e.effective_to_date > :day)
               and daterange(greatest(a.effective_from_date, :day), a.effective_to_date) && daterange(greatest(e.effective_from_date, :day), e.effective_to_date)
            """;

    private final JdbcClient jdbc;
    private final IdGenerator ids;

    JdbcOrgStructureCommandRepository(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public boolean lockOrgUnit(OrgUnitId orgUnit) {
        return jdbc.sql("select 1 from organization_workforce.org_units where id = :id for no key update").param("id", orgUnit.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean lockEmployee(EmployeeId employee) {
        return jdbc.sql("select 1 from organization_workforce.employees where id = :id for update").param("id", employee.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public void lockOrgTree() {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended('organization.workforce.org-tree', 0))").query().singleRow();
    }

    @Override
    public boolean hasAttributionAfter(OrgUnitId orgUnit, LocalDate day) {
        return jdbc.sql("""
                select 1 from organization_workforce.org_unit_legal_entities
                 where org_unit_id = :unit and superseded_at is null and (effective_from_date >= :day or effective_to_date > :day)
                """).param("unit", orgUnit.value()).param("day", day).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean orgUnitExists(OrgUnitId orgUnit) {
        return jdbc.sql("select 1 from organization_workforce.org_units where id = :id").param("id", orgUnit.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean positionExists(PositionId position) {
        return jdbc.sql("select 1 from organization_workforce.positions where id = :id").param("id", position.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean employeeExists(EmployeeId employee) {
        return jdbc.sql("select 1 from organization_workforce.employees where id = :id").param("id", employee.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean legalEntityExists(LegalEntityId legalEntity) {
        return jdbc.sql("select 1 from organization_workforce.legal_entities where id = :id").param("id", legalEntity.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean positionCodeTaken(String code) {
        return jdbc.sql("""
                select 1 from organization_workforce.positions where code = :code
                union all select 1 from organization_workforce.position_versions where code = :code limit 1
                """).param("code", code).query(Integer.class).optional().isPresent();
    }

    @Override
    public Optional<OrgStructureCommand> find(OrgStructureCommandId id) {
        return jdbc.sql(COMMAND_COLUMNS + " where org_structure_command_id = :id").param("id", id.value()).query((rs, n) -> command(rs)).optional();
    }

    private static OrgStructureCommand command(ResultSet rs) throws SQLException {
        UUID unit = rs.getObject("target_org_unit_id", UUID.class);
        UUID position = rs.getObject("target_position_id", UUID.class);
        String region = rs.getString("target_region_kind");
        UUID parent = rs.getObject("target_parent_org_unit_id", UUID.class);
        UUID head = rs.getObject("target_head_employee_id", UUID.class);
        UUID manager = rs.getObject("target_receivables_manager_employee_id", UUID.class);
        UUID mergeInto = rs.getObject("merge_into_org_unit_id", UUID.class);
        UUID legalEntity = rs.getObject("target_legal_entity_id", UUID.class);
        OffsetDateTime applied = rs.getObject("applied_at", OffsetDateTime.class);
        return OrgStructureCommand.restore(new OrgStructureCommandId(rs.getObject("org_structure_command_id", UUID.class)),
                OrgStructureTargetKind.valueOf(rs.getString("target_kind")), OrgStructureCommandKind.valueOf(rs.getString("command_kind")),
                unit == null ? null : new OrgUnitId(unit), position == null ? null : new PositionId(position), rs.getObject("effective_date", LocalDate.class),
                rs.getString("target_code"), rs.getString("target_name"), rs.getString("target_short_name"), region == null ? null : RegionKind.valueOf(region),
                (Boolean) rs.getObject("is_target_delivery_unit"), parent == null ? null : new OrgUnitId(parent), head == null ? null : new EmployeeId(head),
                manager == null ? null : new EmployeeId(manager), mergeInto == null ? null : new OrgUnitId(mergeInto),
                legalEntity == null ? null : new LegalEntityId(legalEntity), OrgCommandStatus.valueOf(rs.getString("status")),
                (Integer) rs.getObject("affected_employee_count"), (Integer) rs.getObject("transferred_employee_count"),
                new UserId(rs.getObject("created_by", UUID.class)), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                applied == null ? null : applied.toInstant(), rs.getLong("version"));
    }

    @Override
    public void insert(OrgStructureCommand c) {
        jdbc.sql("""
                insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, target_position_id,
                       effective_date, status, target_code, target_name, target_short_name, target_region_kind, is_target_delivery_unit, target_parent_org_unit_id,
                       target_head_employee_id, target_receivables_manager_employee_id, merge_into_org_unit_id, target_legal_entity_id, applied_at, created_at,
                       created_by, updated_at, version)
                values (:id, :kind, :targetKind, :unit, :position, :day, :status, :code, :name, :shortName, :region, :delivery, :parent, :head, :manager, :mergeInto,
                        :legalEntity, :appliedAt, :createdAt, :createdBy, :createdAt, :version)
                """)
                .param("id", c.id().value()).param("kind", c.kind().name()).param("targetKind", c.targetKind().name())
                .param("unit", c.targetOrgUnitId() == null ? null : c.targetOrgUnitId().value(), Types.OTHER)
                .param("position", c.targetPositionId() == null ? null : c.targetPositionId().value(), Types.OTHER).param("day", c.effectiveDate())
                .param("status", c.status().name()).param("code", c.targetCode(), Types.VARCHAR).param("name", c.targetName(), Types.VARCHAR)
                .param("shortName", c.targetShortName(), Types.VARCHAR).param("region", c.targetRegionKind() == null ? null : c.targetRegionKind().name(), Types.VARCHAR)
                .param("delivery", c.targetIsDeliveryUnit(), Types.BOOLEAN)
                .param("parent", c.targetParentOrgUnitId() == null ? null : c.targetParentOrgUnitId().value(), Types.OTHER)
                .param("head", c.targetHeadEmployeeId() == null ? null : c.targetHeadEmployeeId().value(), Types.OTHER)
                .param("manager", c.targetReceivablesManagerEmployeeId() == null ? null : c.targetReceivablesManagerEmployeeId().value(), Types.OTHER)
                .param("mergeInto", c.mergeIntoOrgUnitId() == null ? null : c.mergeIntoOrgUnitId().value(), Types.OTHER)
                .param("legalEntity", c.targetLegalEntityId() == null ? null : c.targetLegalEntityId().value(), Types.OTHER)
                .param("appliedAt", at(c.appliedAt()), Types.TIMESTAMP_WITH_TIMEZONE).param("createdAt", at(c.createdAt())).param("createdBy", c.createdBy().value())
                .param("version", c.version()).update();
    }

    private static final String CLOSE_UNIT = "update organization_workforce.org_unit_versions set effective_to_date = :to, closed_at = :at, updated_at = :at where org_unit_version_id = :id";
    private static final String CLOSE_POSITION = "update organization_workforce.position_versions set effective_to_date = :to, closed_at = :at, updated_at = :at"
            + " where position_version_id = :id";
    private static final String SUPERSEDE_UNIT = "update organization_workforce.org_unit_versions set superseded_at = :at, superseding_org_structure_command_id = :command,"
            + " updated_at = :at where org_unit_version_id = :id";
    private static final String SUPERSEDE_POSITION = "update organization_workforce.position_versions set superseded_at = :at, superseding_org_structure_command_id = :command,"
            + " updated_at = :at where position_version_id = :id";

    @Override
    public void update(OrgStructureCommand c, Instant at, long loadedVersion) {
        int updated = jdbc.sql("""
                update organization_workforce.org_structure_commands set status = :status, affected_employee_count = :affected,
                       transferred_employee_count = :transferred, applied_at = :appliedAt, withdrawn_at = :withdrawnAt, withdrawn_by = :by,
                       withdrawal_reason = :reason, updated_at = :now, version = :version
                 where org_structure_command_id = :id and version = :loaded
                """)
                .param("status", c.status().name()).param("affected", c.affectedEmployeeCount(), Types.INTEGER).param("transferred", c.transferredEmployeeCount(), Types.INTEGER)
                .param("appliedAt", at(c.appliedAt()), Types.TIMESTAMP_WITH_TIMEZONE).param("withdrawnAt", at(c.withdrawnAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("by", c.withdrawnBy() == null ? null : c.withdrawnBy().value(), Types.OTHER).param("reason", c.withdrawalReason(), Types.VARCHAR)
                .param("now", at(at)).param("version", loadedVersion + 1).param("id", c.id().value()).param("loaded", loadedVersion).update();
        if (updated != 1) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT, "the command was changed by someone else");
        }
    }

    @Override
    public void insertTransition(OrgStructureCommandId command, long sequence, @Nullable String from, String to, ActorType actorType, UUID actorId, LocalDate day, Instant at,
                                 UUID correlation) {
        jdbc.sql("""
                insert into organization_workforce.state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status,
                       occurred_on_date, recorded_at, actor_type, actor_id, cause_kind, command_id, correlation_id, created_at, updated_at)
                values (:id, 'OrgStructureCommand', :aggregate, :sequence, :from, :to, :day, :at, :actorType, :actor, 'COMMAND', :aggregate, :correlation, :at, :at)
                """)
                .param("id", ids.next()).param("aggregate", command.value()).param("sequence", sequence).param("from", from, Types.VARCHAR).param("to", to)
                .param("day", day).param("at", at(at)).param("actorType", actorType.name()).param("actor", actorId).param("correlation", correlation).update();
    }

    @Override
    public boolean hasOpenCommand(@Nullable OrgUnitId orgUnit, @Nullable PositionId position, LocalDate day) {
        if (orgUnit != null) {
            return jdbc.sql("select 1 from organization_workforce.org_structure_commands where target_org_unit_id = :id and effective_date = :day"
                    + " and status in ('PENDING', 'IN_PROGRESS', 'AWAITING_ATTRIBUTION_COUNTERSIGN') limit 1")
                    .param("id", orgUnit.value()).param("day", day).query(Integer.class).optional().isPresent();
        }
        return position != null && jdbc.sql("select 1 from organization_workforce.org_structure_commands where target_position_id = :id and effective_date = :day"
                + " and status = 'PENDING' limit 1").param("id", position.value()).param("day", day).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean hasOpenMerge(OrgUnitId orgUnit) {
        return jdbc.sql("select 1 from organization_workforce.org_structure_commands where target_org_unit_id = :id and command_kind = 'MERGE'"
                + " and status in ('PENDING', 'IN_PROGRESS', 'AWAITING_ATTRIBUTION_COUNTERSIGN') limit 1").param("id", orgUnit.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean hasOpenMergeInto(OrgUnitId orgUnit, LocalDate onOrBefore) {
        return jdbc.sql("select 1 from organization_workforce.org_structure_commands where merge_into_org_unit_id = :id and command_kind = 'MERGE'"
                + " and status in ('PENDING', 'IN_PROGRESS', 'AWAITING_ATTRIBUTION_COUNTERSIGN') and effective_date <= :day limit 1")
                .param("id", orgUnit.value()).param("day", onOrBefore).query(Integer.class).optional().isPresent();
    }

    @Override
    public void insertOrgUnit(OrgStructureCommand c, Instant at) {
        jdbc.sql("""
                insert into organization_workforce.org_units (id, code, name, parent_org_unit_id, status, region_category, is_delivery_unit, short_name, created_at, updated_at)
                values (:id, :code, :name, :parent, 'ACTIVE', :region, :delivery, :shortName, :at, :at)
                """)
                .param("id", c.targetOrgUnitId().value()).param("code", c.targetCode(), Types.VARCHAR).param("name", c.targetName())
                .param("parent", c.targetParentOrgUnitId() == null ? null : c.targetParentOrgUnitId().value(), Types.OTHER)
                .param("region", c.targetRegionKind() == null ? null : c.targetRegionKind().name(), Types.VARCHAR).param("delivery", c.targetIsDeliveryUnit())
                .param("shortName", c.targetShortName(), Types.VARCHAR).param("at", at(at)).update();
    }

    @Override
    public void insertPosition(OrgStructureCommand c, Instant at) {
        jdbc.sql("insert into organization_workforce.positions (id, code, name, status, created_at, updated_at) values (:id, :code, :name, 'ACTIVE', :at, :at)")
                .param("id", c.targetPositionId().value()).param("code", c.targetCode(), Types.VARCHAR).param("name", c.targetName()).param("at", at(at)).update();
    }

    @Override
    public void insertInitialAttribution(OrgUnitId orgUnit, LegalEntityId legalEntity, LocalDate from, OrgStructureCommandId command, AuditEntryId audit, Instant at) {
        jdbc.sql("insert into organization_workforce.legal_entity_attributions (attribution_id, org_unit_id, created_at, updated_at) values (:id, :unit, :at, :at)")
                .param("id", ids.next()).param("unit", orgUnit.value()).param("at", at(at)).update();
        jdbc.sql("""
                insert into organization_workforce.org_unit_legal_entities (org_unit_legal_entity_id, org_unit_id, legal_entity_id, effective_from_date,
                       change_channel_kind, change_org_structure_command_id, version_audit_entry_id, created_at, updated_at)
                values (:id, :unit, :legalEntity, :from, 'COMMAND', :command, :audit, :at, :at)
                """)
                .param("id", ids.next()).param("unit", orgUnit.value()).param("legalEntity", legalEntity.value()).param("from", from).param("command", command.value())
                .param("audit", audit.value()).param("at", at(at)).update();
    }

    @Override
    public void attributeUnit(OrgUnitId orgUnit, LegalEntityId legalEntity, LocalDate from, OrgStructureCommandId command, AuditEntryId audit, Instant at) {
        jdbc.sql("""
                insert into organization_workforce.legal_entity_attributions (attribution_id, org_unit_id, created_at, updated_at)
                select :id, :unit, :at, :at where not exists (select 1 from organization_workforce.legal_entity_attributions where org_unit_id = :unit)
                """).param("id", ids.next()).param("unit", orgUnit.value()).param("at", at(at)).update();
        jdbc.sql("""
                update organization_workforce.org_unit_legal_entities set effective_to_date = :from, closed_at = :at, updated_at = :at
                 where org_unit_id = :unit and effective_to_date is null and superseded_at is null and effective_from_date < :from
                """).param("from", from).param("at", at(at)).param("unit", orgUnit.value()).update();
        insertPeriod(ids.next(), orgUnit, legalEntity, from, null, command, audit, null, at);
    }

    @Override
    public void voidAttribution(OrgUnitId orgUnit, OrgStructureCommandId command, AuditEntryId audit, Instant at) {
        record Closed(UUID id, UUID legalEntity, LocalDate from, @Nullable LocalDate commandEnd) {
        }
        List<Closed> closedByCommand = jdbc.sql("""
                select p.org_unit_legal_entity_id, p.legal_entity_id, p.effective_from_date, w.effective_to_date
                  from organization_workforce.org_unit_legal_entities p
                  join organization_workforce.org_unit_legal_entities w on w.org_unit_id = p.org_unit_id and w.effective_from_date = p.effective_to_date
                 where p.org_unit_id = :unit and p.superseded_at is null and w.superseded_at is null and w.change_org_structure_command_id = :command
                   and p.change_org_structure_command_id is distinct from :command
                """).param("unit", orgUnit.value()).param("command", command.value())
                .query((rs, n) -> new Closed(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class))).list();
        jdbc.sql("""
                update organization_workforce.org_unit_legal_entities set superseded_at = :at, superseding_org_structure_command_id = :command, updated_at = :at
                 where org_unit_id = :unit and superseded_at is null and (change_org_structure_command_id = :command or org_unit_legal_entity_id in (:closed))
                """).param("at", at(at)).param("command", command.value()).param("unit", orgUnit.value())
                .param("closed", closedByCommand.isEmpty() ? List.of(command.value()) : closedByCommand.stream().map(Closed::id).toList()).update();
        for (Closed period : closedByCommand) {
            insertPeriod(ids.next(), orgUnit, new LegalEntityId(period.legalEntity()), period.from(), period.commandEnd(), command, audit, period.id(), at);
        }
    }

    private void insertPeriod(UUID id, OrgUnitId orgUnit, LegalEntityId legalEntity, LocalDate from, @Nullable LocalDate to, OrgStructureCommandId command, AuditEntryId audit,
                              @Nullable UUID correctedFrom, Instant at) {
        jdbc.sql("""
                insert into organization_workforce.org_unit_legal_entities (org_unit_legal_entity_id, org_unit_id, legal_entity_id, effective_from_date, effective_to_date,
                       closed_at, change_channel_kind, change_org_structure_command_id, version_audit_entry_id, corrected_from_version_id, created_at, updated_at)
                values (:id, :unit, :legalEntity, :from, :to, :closedAt, 'COMMAND', :command, :audit, :correctedFrom, :at, :at)
                """)
                .param("id", id).param("unit", orgUnit.value()).param("legalEntity", legalEntity.value()).param("from", from).param("to", to, Types.DATE)
                .param("closedAt", to == null ? null : at(at), Types.TIMESTAMP_WITH_TIMEZONE).param("command", command.value()).param("audit", audit.value())
                .param("correctedFrom", correctedFrom, Types.OTHER).param("at", at(at)).update();
    }

    @Override
    public void insertBasis(OrgStructureCommandId command, List<OrgCountersignBasis> basis, Instant at) {
        for (OrgCountersignBasis row : basis) {
            jdbc.sql("""
                    insert into organization_workforce.attribution_countersign_bases (attribution_countersign_basis_id, org_structure_command_id, employee_id,
                           previous_legal_entity_id, new_legal_entity_id, created_at, updated_at)
                    values (:id, :command, :employee, :previous, :next, :at, :at)
                    """)
                    .param("id", ids.next()).param("command", command.value()).param("employee", row.employeeId().value()).param("previous", row.previous().value())
                    .param("next", row.next().value()).param("at", at(at)).update();
        }
    }

    @Override
    public List<OrgCountersignBasis> basisOf(OrgStructureCommandId command) {
        return jdbc.sql("""
                select employee_id, previous_legal_entity_id, new_legal_entity_id from organization_workforce.attribution_countersign_bases
                 where org_structure_command_id = :command order by created_at, employee_id
                """).param("command", command.value())
                .query((rs, n) -> new OrgCountersignBasis(new EmployeeId(rs.getObject("employee_id", UUID.class)),
                        new LegalEntityId(rs.getObject("previous_legal_entity_id", UUID.class)), new LegalEntityId(rs.getObject("new_legal_entity_id", UUID.class)))).list();
    }

    @Override
    public List<OrgStructureCommand> due(LocalDate day) {
        return jdbc.sql(COMMAND_COLUMNS + " where status = 'PENDING' and effective_date <= :day order by effective_date, org_structure_command_id")
                .param("day", day).query((rs, n) -> command(rs)).list();
    }

    @Override
    public List<OrgStructureCommand> awaitingCountersign() {
        return jdbc.sql(COMMAND_COLUMNS + " where status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' order by effective_date, org_structure_command_id")
                .query((rs, n) -> command(rs)).list();
    }

    @Override
    public OrgTimeline unitTimeline(OrgUnitId orgUnit) {
        List<VersionRow> rows = jdbc.sql("""
                select org_unit_version_id as id, effective_from_date, effective_to_date, code, name, short_name, region_kind, is_delivery_unit, parent_org_unit_id,
                       head_employee_id, regional_receivables_manager_id, status, change_channel_kind, change_org_structure_command_id, version_audit_entry_id,
                       corrected_from_version_id, superseded_at is not null as superseded
                  from organization_workforce.org_unit_versions where org_unit_id = :id order by effective_from_date, revision_number
                """).param("id", orgUnit.value()).query((rs, n) -> unitRow(rs)).list();
        return new OrgTimeline(rows, lookup(rows));
    }

    @Override
    public OrgTimeline positionTimeline(PositionId position) {
        List<VersionRow> rows = jdbc.sql("""
                select position_version_id as id, effective_from_date, effective_to_date, code, name, status, change_channel_kind, change_org_structure_command_id,
                       version_audit_entry_id, corrected_from_version_id, superseded_at is not null as superseded
                  from organization_workforce.position_versions where position_id = :id order by effective_from_date, revision_number
                """).param("id", position.value()).query((rs, n) -> positionRow(rs)).list();
        return new OrgTimeline(rows, lookup(rows));
    }

    private Map<UUID, OrgStructureCommand> lookup(List<VersionRow> rows) {
        Map<UUID, OrgStructureCommand> byId = new HashMap<>();
        for (VersionRow row : rows) {
            OrgStructureCommandId source = row.source().orgCommandId();
            if (source != null && !byId.containsKey(source.value())) {
                find(source).ifPresent(command -> byId.put(source.value(), command));
            }
        }
        return byId;
    }

    private static VersionRow unitRow(ResultSet rs) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(OrgEffects.CODE, rs.getString("code"));
        values.put(OrgEffects.NAME, rs.getString("name"));
        values.put(OrgEffects.SHORT_NAME, rs.getString("short_name"));
        values.put(OrgEffects.REGION, rs.getString("region_kind"));
        values.put(OrgEffects.DELIVERY, rs.getBoolean("is_delivery_unit"));
        values.put(OrgEffects.PARENT, rs.getObject("parent_org_unit_id", UUID.class));
        values.put(OrgEffects.HEAD, rs.getObject("head_employee_id", UUID.class));
        values.put(OrgEffects.RECEIVABLES_MANAGER, rs.getObject("regional_receivables_manager_id", UUID.class));
        values.put(OrgEffects.LIFECYCLE, rs.getString("status"));
        return versionRow(rs, values);
    }

    private static VersionRow positionRow(ResultSet rs) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(OrgEffects.CODE, rs.getString("code"));
        values.put(OrgEffects.NAME, rs.getString("name"));
        values.put(OrgEffects.LIFECYCLE, rs.getString("status"));
        return versionRow(rs, values);
    }

    private static VersionRow versionRow(ResultSet rs, Map<String, Object> values) throws SQLException {
        ChangeSource source = "COMMAND".equals(rs.getString("change_channel_kind"))
                ? ChangeSource.ofOrgCommand(new OrgStructureCommandId(rs.getObject("change_org_structure_command_id", UUID.class)),
                new AuditEntryId(rs.getObject("version_audit_entry_id", UUID.class))) : ChangeSource.MIGRATION;
        return new VersionRow(rs.getObject("id", UUID.class), rs.getObject("effective_from_date", LocalDate.class), rs.getObject("effective_to_date", LocalDate.class),
                values, source, rs.getObject("corrected_from_version_id", UUID.class), rs.getBoolean("superseded"));
    }

    @Override
    public Map<UUID, List<VersionRow>> currentUnitRows() {
        Map<UUID, List<VersionRow>> units = new HashMap<>();
        jdbc.sql("""
                select org_unit_version_id, org_unit_id, effective_from_date, effective_to_date, parent_org_unit_id, status
                  from organization_workforce.org_unit_versions where superseded_at is null
                """).query(rs -> {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put(OrgEffects.PARENT, rs.getObject("parent_org_unit_id", UUID.class));
            values.put(OrgEffects.LIFECYCLE, rs.getString("status"));
            units.computeIfAbsent(rs.getObject("org_unit_id", UUID.class), k -> new ArrayList<>()).add(new VersionRow(rs.getObject("org_unit_version_id", UUID.class),
                    rs.getObject("effective_from_date", LocalDate.class), rs.getObject("effective_to_date", LocalDate.class), values, ChangeSource.MIGRATION, null, false));
        });
        return units;
    }

    @Override
    public void insertUnitVersions(OrgUnitId orgUnit, VersionPlan plan, OrgStructureCommandId command, Instant at) {
        apply(plan, command, at, true);
        for (VersionPlan.NewRow row : plan.inserts()) {
            Map<String, Object> v = row.values();
            int revision = jdbc.sql("select coalesce(max(revision_number) + 1, 0) from organization_workforce.org_unit_versions where org_unit_id = :unit"
                    + " and effective_from_date = :from").param("unit", orgUnit.value()).param("from", row.from()).query(Integer.class).single();
            jdbc.sql("""
                    insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, effective_to_date, code, name, short_name,
                           parent_org_unit_id, status, head_employee_id, regional_receivables_manager_id, region_kind, is_delivery_unit, revision_number,
                           change_channel_kind, change_org_structure_command_id, version_audit_entry_id, closed_at, corrected_from_version_id, created_at, updated_at)
                    values (:id, :unit, :from, :to, :code, :name, :shortName, :parent, :status, :head, :manager, :region, :delivery, :revision, :channel, :command,
                            :audit, :closedAt, :correctedFrom, :at, :at)
                    """)
                    .param("id", row.id()).param("unit", orgUnit.value()).param("from", row.from()).param("to", row.to(), Types.DATE)
                    .param("code", v.get(OrgEffects.CODE), Types.VARCHAR).param("name", v.get(OrgEffects.NAME)).param("shortName", v.get(OrgEffects.SHORT_NAME), Types.VARCHAR)
                    .param("parent", v.get(OrgEffects.PARENT), Types.OTHER).param("status", v.get(OrgEffects.LIFECYCLE))
                    .param("head", v.get(OrgEffects.HEAD), Types.OTHER).param("manager", v.get(OrgEffects.RECEIVABLES_MANAGER), Types.OTHER)
                    .param("region", v.get(OrgEffects.REGION), Types.VARCHAR).param("delivery", v.get(OrgEffects.DELIVERY)).param("revision", revision)
                    .param("channel", row.source().command() ? "COMMAND" : "MIGRATION").param("command", commandKey(row), Types.OTHER)
                    .param("audit", auditKey(row), Types.OTHER).param("closedAt", row.to() == null ? null : at(at), Types.TIMESTAMP_WITH_TIMEZONE)
                    .param("correctedFrom", row.correctedFrom(), Types.OTHER).param("at", at(at)).update();
        }
    }

    @Override
    public void insertPositionVersions(PositionId position, VersionPlan plan, OrgStructureCommandId command, Instant at) {
        apply(plan, command, at, false);
        for (VersionPlan.NewRow row : plan.inserts()) {
            Map<String, Object> v = row.values();
            int revision = jdbc.sql("select coalesce(max(revision_number) + 1, 0) from organization_workforce.position_versions where position_id = :position"
                    + " and effective_from_date = :from").param("position", position.value()).param("from", row.from()).query(Integer.class).single();
            jdbc.sql("""
                    insert into organization_workforce.position_versions (position_version_id, position_id, effective_from_date, effective_to_date, revision_number, code,
                           name, status, change_channel_kind, change_org_structure_command_id, version_audit_entry_id, closed_at, corrected_from_version_id, created_at,
                           updated_at)
                    values (:id, :position, :from, :to, :revision, :code, :name, :status, :channel, :command, :audit, :closedAt, :correctedFrom, :at, :at)
                    """)
                    .param("id", row.id()).param("position", position.value()).param("from", row.from()).param("to", row.to(), Types.DATE).param("revision", revision)
                    .param("code", v.get(OrgEffects.CODE), Types.VARCHAR).param("name", v.get(OrgEffects.NAME)).param("status", v.get(OrgEffects.LIFECYCLE))
                    .param("channel", row.source().command() ? "COMMAND" : "MIGRATION").param("command", commandKey(row), Types.OTHER)
                    .param("audit", auditKey(row), Types.OTHER).param("closedAt", row.to() == null ? null : at(at), Types.TIMESTAMP_WITH_TIMEZONE)
                    .param("correctedFrom", row.correctedFrom(), Types.OTHER).param("at", at(at)).update();
        }
    }

    private void apply(VersionPlan plan, OrgStructureCommandId command, Instant at, boolean unit) {
        for (VersionPlan.Close one : plan.closes()) {
            jdbc.sql(unit ? CLOSE_UNIT : CLOSE_POSITION).param("to", one.to()).param("at", at(at)).param("id", one.id()).update();
        }
        for (UUID id : plan.supersede()) {
            jdbc.sql(unit ? SUPERSEDE_UNIT : SUPERSEDE_POSITION).param("at", at(at)).param("command", command.value()).param("id", id).update();
        }
    }

    @Override
    public List<EmployeeId> employedIn(OrgUnitId orgUnit, LocalDate day) {
        return jdbc.sql("""
                select a.employee_id
                  from organization_workforce.assignment_versions a join organization_workforce.employee_versions e on e.employee_id = a.employee_id
                 where a.org_unit_id = :unit and a.assignment_kind = 'PRIMARY' and a.superseded_at is null and e.superseded_at is null and e.employment_status = 'EMPLOYED'
                   and a.effective_from_date <= :day and (a.effective_to_date is null or a.effective_to_date > :day)
                   and e.effective_from_date <= :day and (e.effective_to_date is null or e.effective_to_date > :day)
                 group by a.employee_id order by min(a.effective_from_date), a.employee_id
                """).param("unit", orgUnit.value()).param("day", day).query((rs, n) -> new EmployeeId(rs.getObject(1, UUID.class))).list();
    }

    @Override
    public List<HeldPost> postsIn(OrgUnitId orgUnit, LocalDate day) {
        return jdbc.sql("""
                select a.employee_id, a.assignment_id, a.assignment_kind
                  from organization_workforce.assignment_versions a join organization_workforce.employee_versions e on e.employee_id = a.employee_id
                 where a.org_unit_id = :unit and a.superseded_at is null and e.superseded_at is null and e.employment_status = 'EMPLOYED'
                   and a.effective_from_date <= :day and (a.effective_to_date is null or a.effective_to_date > :day)
                   and e.effective_from_date <= :day and (e.effective_to_date is null or e.effective_to_date > :day)
                 group by a.employee_id, a.assignment_id, a.assignment_kind
                 order by a.employee_id, a.assignment_kind, a.assignment_id
                """).param("unit", orgUnit.value()).param("day", day)
                .query((rs, n) -> new HeldPost(new EmployeeId(rs.getObject("employee_id", UUID.class)), new AssignmentId(rs.getObject("assignment_id", UUID.class)),
                        AssignmentKind.valueOf(rs.getString("assignment_kind")))).list();
    }

    @Override
    public boolean hasEmployedFrom(OrgUnitId orgUnit, LocalDate day) {
        return jdbc.sql("select 1 " + OPEN_HOLDERS.formatted("org_unit_id") + " limit 1").param("target", orgUnit.value()).param("day", day).query(Integer.class).optional().isPresent();
    }

    @Override
    public List<BlockingArrival> arrivalsAfter(OrgUnitId orgUnit, LocalDate day) {
        return jdbc.sql("select a.employee_id, a.effective_from_date, a.change_command_id " + OPEN_HOLDERS.formatted("org_unit_id")
                + " and a.effective_from_date > :day and not exists (select 1 from organization_workforce.assignment_versions p where p.employee_id = a.employee_id"
                + " and p.superseded_at is null and p.org_unit_id = a.org_unit_id and p.effective_to_date = a.effective_from_date)"
                + " order by a.effective_from_date, a.employee_id").param("target", orgUnit.value()).param("day", day)
                .query((rs, n) -> {
                    UUID command = rs.getObject("change_command_id", UUID.class);
                    return new BlockingArrival(new EmployeeId(rs.getObject("employee_id", UUID.class)), rs.getObject("effective_from_date", LocalDate.class),
                            command == null ? null : new PersonnelCommandId(command));
                }).list();
    }

    @Override
    public List<OrgStructureCommandId> mergesInProgressOf(EmployeeId employee) {
        return jdbc.sql("""
                select c.org_structure_command_id from organization_workforce.org_structure_commands c
                 where c.command_kind = 'MERGE' and c.status = 'IN_PROGRESS'
                   and exists (select 1 from organization_workforce.assignment_versions a
                                where a.employee_id = :employee and a.org_unit_id = c.target_org_unit_id and a.superseded_at is null
                                  and a.effective_from_date <= c.effective_date and (a.effective_to_date is null or a.effective_to_date > c.effective_date))
                 order by c.effective_date, c.target_org_unit_id, c.org_structure_command_id
                """).param("employee", employee.value()).query((rs, n) -> new OrgStructureCommandId(rs.getObject(1, UUID.class))).list();
    }

    @Override
    public boolean hasGeneratedTransfer(OrgStructureCommandId merge, AssignmentId assignment) {
        return jdbc.sql("select 1 from organization_workforce.personnel_commands where org_structure_command_id = :merge and target_assignment_id = :assignment limit 1")
                .param("merge", merge.value()).param("assignment", assignment.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean hasHolderFrom(PositionId position, LocalDate day) {
        return jdbc.sql("select 1 " + OPEN_HOLDERS.formatted("position_id") + " limit 1").param("target", position.value()).param("day", day).query(Integer.class).optional().isPresent();
    }

    private static @Nullable UUID commandKey(VersionPlan.NewRow row) {
        return row.source().orgCommandId() == null ? null : row.source().orgCommandId().value();
    }

    private static @Nullable UUID auditKey(VersionPlan.NewRow row) {
        return row.source().auditEntryId() == null ? null : row.source().auditEntryId().value();
    }

    private static @Nullable OffsetDateTime at(@Nullable Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
