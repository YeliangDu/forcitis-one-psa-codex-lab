package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.Gender;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.domain.AssignmentKind;
import com.forcitis.one.organization.workforce.domain.AssignmentRoot;
import com.forcitis.one.organization.workforce.domain.CommandEffects;
import com.forcitis.one.organization.workforce.domain.HoldStatus;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.NewEmployee;
import com.forcitis.one.organization.workforce.domain.PersonalProfile;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.ReleaseChannelKind;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandQueries;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.RelationshipRow;
import com.forcitis.one.organization.workforce.domain.ChangeSource;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import com.forcitis.one.organization.workforce.domain.VersionTable;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.forcitis.one.kernel.event.ActorType;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Personnel commands and their version rows in the workforce schema. Rows are only inserted, superseded or closed (W2 design section 2); the triggers hold the rest. */
@Repository
class JdbcPersonnelCommandRepository implements PersonnelCommandRepository, PersonnelCommandQueries {
    private final JdbcClient jdbc;
    private final IdGenerator ids;
    private final BusinessZone zone;

    JdbcPersonnelCommandRepository(JdbcClient jdbc, IdGenerator ids, BusinessZone zone) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.zone = zone;
    }

    @Override
    public boolean lockEmployee(EmployeeId employee) {
        return jdbc.sql("select 1 from organization_workforce.employees where id = :id for update").param("id", employee.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public void lockSeparationDecisions() {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", SeparationReview.SEPARATION_LOCK_KEY).query().singleRow();
    }

    @Override
    public Set<EmployeeId> excludedEmployees() {
        return new HashSet<>(jdbc.sql("""
                select h.employee_id from organization_workforce.separation_holds h
                join organization_workforce.personnel_commands c on c.personnel_command_id = h.personnel_command_id
                where h.status = 'HELD' or (h.status = 'RELEASED' and c.status = 'PENDING')
                union
                select employee_id from organization_workforce.personnel_commands
                where command_kind = 'UNPAID_LEAVE_START' and status = 'PENDING'""")
                .query((rs, n) -> new EmployeeId(rs.getObject("employee_id", UUID.class))).list());
    }

    @Override
    public Set<EmployeeId> heldEmployees() {
        return new HashSet<>(jdbc.sql("select employee_id from organization_workforce.separation_holds where status = 'HELD'")
                .query((rs, n) -> new EmployeeId(rs.getObject("employee_id", UUID.class))).list());
    }

    @Override
    public boolean isHeld(EmployeeId employee) {
        return jdbc.sql("select exists (select 1 from organization_workforce.separation_holds where employee_id = :employee and status = 'HELD')")
                .param("employee", employee.value()).query(Boolean.class).single();
    }

    /**
     * Leaving needs every relationship in service on the day to be gone (leaving, or on unpaid leave) and at least one to be leaving; a relationship is leaving
     * with a held separation, a released one waiting for its date, or an unpaid-leave start entered and not yet started (ADR-0086 design 6.2).
     */
    @Override
    public boolean isLeaving(EmployeeId employee, LocalDate day) {
        return jdbc.sql("""
                with in_service as (
                    select v.employment_relationship_id, v.is_on_unpaid_leave,
                           exists (select 1 from organization_workforce.personnel_commands c
                                    left join organization_workforce.separation_holds h on h.personnel_command_id = c.personnel_command_id
                                   where c.employment_relationship_id = v.employment_relationship_id
                                     and ((h.status = 'HELD' or (h.status = 'RELEASED' and c.status = 'PENDING'))
                                          or (c.command_kind = 'UNPAID_LEAVE_START' and c.status = 'PENDING'))) as leaving
                      from organization_workforce.employment_relationship_versions v
                     where v.employee_id = :employee and v.superseded_at is null and v.employment_status = 'EMPLOYED'
                       and v.effective_from_date <= :day and (v.effective_to_date is null or :day < v.effective_to_date))
                select exists (select 1 from in_service where leaving)
                   and not exists (select 1 from in_service where not leaving and not is_on_unpaid_leave)
                """).param("employee", employee.value()).param("day", day).query(Boolean.class).single();
    }

    @Override
    public List<PersonnelCommandId> pendingDue(LocalDate day) {
        return jdbc.sql("""
                select c.personnel_command_id from organization_workforce.personnel_commands c
                where c.status = 'PENDING' and c.effective_date <= :day
                order by c.effective_date, c.personnel_command_id""")
                .param("day", day).query((rs, n) -> new PersonnelCommandId(rs.getObject("personnel_command_id", UUID.class))).list();
    }

    @Override
    public void lockOrgUnitForEntry(OrgUnitId orgUnit) {
        jdbc.sql("select 1 from organization_workforce.org_units where id = :id for share").param("id", orgUnit.value()).query(Integer.class).optional();
    }

    @Override
    public boolean hasOpenMergeFor(OrgUnitId orgUnit, LocalDate day) {
        return jdbc.sql("select 1 from organization_workforce.org_structure_commands where target_org_unit_id = :id and command_kind = 'MERGE'"
                + " and (status = 'IN_PROGRESS' or (status = 'PENDING' and effective_date <= :day)) limit 1")
                .param("id", orgUnit.value()).param("day", day).query(Integer.class).optional().isPresent();
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
    public boolean legalEntityIsActive(LegalEntityId legalEntity) {
        return jdbc.sql("select 1 from organization_workforce.legal_entities where id = :id and status = 'ACTIVE'").param("id", legalEntity.value()).query(Integer.class)
                .optional().isPresent();
    }

    @Override
    public void insertEmployee(NewEmployee employee, Instant at) {
        PersonalProfile profile = employee.profile();
        jdbc.sql("""
                insert into organization_workforce.employees (id, employee_code, display_name, email, gender, wecom_user_id, employment_status, is_fee_earner,
                       created_at, updated_at)
                values (:id, :code, :name, :email, :gender, :wecom, :status, :feeEarner, :at, :at)
                """)
                .param("id", employee.employeeId().value()).param("code", employee.employeeCode()).param("name", profile.displayName())
                .param("email", profile.email(), Types.VARCHAR).param("gender", profile.gender() == null ? null : profile.gender().name(), Types.VARCHAR)
                .param("wecom", profile.wecomUserId() == null ? null : profile.wecomUserId().value(), Types.VARCHAR)
                .param("status", employee.employmentStatus().name()).param("feeEarner", employee.feeEarner()).param("at", at(at)).update();
    }

    @Override
    public boolean employeeCodeTaken(String employeeCode) {
        return jdbc.sql("select 1 from organization_workforce.employees where employee_code = :code").param("code", employeeCode).query(Integer.class).optional().isPresent();
    }

    @Override
    public boolean wecomUserIdHeld(WecomUserId wecomUserId, @Nullable EmployeeId except) {
        return jdbc.sql("select 1 from organization_workforce.employees where wecom_user_id = :wecom and (cast(:except as uuid) is null or id <> :except) limit 1")
                .param("wecom", wecomUserId.value()).param("except", except == null ? null : except.value(), Types.OTHER).query(Integer.class).optional().isPresent();
    }

    @Override
    public Optional<PersonalProfile> profileOf(EmployeeId employee) {
        return jdbc.sql("select display_name, gender, email, wecom_user_id from organization_workforce.employees where id = :id").param("id", employee.value())
                .query((rs, n) -> {
                    String gender = rs.getString("gender");
                    String wecom = rs.getString("wecom_user_id");
                    return new PersonalProfile(rs.getString("display_name"), gender == null ? null : Gender.valueOf(gender), rs.getString("email"),
                            wecom == null ? null : new WecomUserId(wecom));
                }).optional();
    }

    @Override
    public void updateProfile(EmployeeId employee, PersonalProfile profile, Instant at) {
        jdbc.sql("""
                update organization_workforce.employees set display_name = :name, gender = :gender, email = :email, wecom_user_id = :wecom,
                       updated_at = :at, version = version + 1
                 where id = :id
                """)
                .param("name", profile.displayName()).param("gender", profile.gender() == null ? null : profile.gender().name(), Types.VARCHAR)
                .param("email", profile.email(), Types.VARCHAR).param("wecom", profile.wecomUserId() == null ? null : profile.wecomUserId().value(), Types.VARCHAR)
                .param("at", at(at)).param("id", employee.value()).update();
    }

    @Override
    public PersonnelHistory load(EmployeeId employee) {
        List<VersionRow> employeeRows = jdbc.sql("""
                select employment_relationship_version_id as id, employment_relationship_id, effective_from_date, effective_to_date, employment_status, employment_phase_kind, is_on_unpaid_leave,
                       is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, change_command_id, version_audit_entry_id,
                       corrected_from_version_id, superseded_at is not null as superseded
                  from organization_workforce.employment_relationship_versions where employee_id = :id order by effective_from_date, revision_number
                """).param("id", employee.value()).query((rs, n) -> row(rs, VersionTable.EMPLOYEE)).list();
        List<VersionRow> assignmentRows = jdbc.sql("""
                select assignment_version_id as id, employment_relationship_id, assignment_id, effective_from_date, effective_to_date, org_unit_id, position_id, reports_to_employee_id,
                       change_channel_kind, change_command_id, version_audit_entry_id, corrected_from_version_id, superseded_at is not null as superseded
                  from organization_workforce.assignment_versions where employee_id = :id order by effective_from_date, revision_number
                """).param("id", employee.value()).query((rs, n) -> row(rs, VersionTable.ASSIGNMENT)).list();
        List<AssignmentRoot> roots = jdbc.sql("select assignment_id, assignment_kind from organization_workforce.employee_assignments where employee_id = :id order by created_at, assignment_id")
                .param("id", employee.value()).query((rs, n) -> new AssignmentRoot(new AssignmentId(rs.getObject("assignment_id", UUID.class)),
                        AssignmentKind.valueOf(rs.getString("assignment_kind")))).list();
        List<VersionRow> legalEntityRows = jdbc.sql("""
                select employee_legal_entity_id as id, effective_from_date, effective_to_date, legal_entity_id, change_channel_kind, change_command_id,
                       version_audit_entry_id, corrected_from_version_id, superseded_at is not null as superseded
                  from organization_workforce.employee_legal_entities where employee_id = :id order by effective_from_date, created_at
                """).param("id", employee.value()).query((rs, n) -> row(rs, VersionTable.LEGAL_ENTITY)).list();
        List<PersonnelCommand> all = jdbc.sql(COMMAND_COLUMNS + " where c.employee_id = :id order by c.effective_date, c.created_at")
                .param("id", employee.value()).query((rs, n) -> command(rs)).list();
        Map<UUID, PersonnelCommand> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.id().value(), c));
        List<RelationshipRow> relationships = jdbc.sql("""
                select employment_relationship_id, employer_legal_entity_id, predecessor_relationship_id, employer_source_kind = 'DECLARED' or employer_confirmed_at is not null as confirmed
                  from organization_workforce.employment_relationships where employee_id = :id order by created_at, employment_relationship_id
                """).param("id", employee.value()).query((rs, n) -> new RelationshipRow(new EmploymentRelationshipId(rs.getObject("employment_relationship_id", UUID.class)),
                        new LegalEntityId(rs.getObject("employer_legal_entity_id", UUID.class)),
                        rs.getObject("predecessor_relationship_id", UUID.class) == null ? null : new EmploymentRelationshipId(rs.getObject("predecessor_relationship_id", UUID.class)),
                        rs.getBoolean("confirmed"))).list();
        return new PersonnelHistory(employeeRows, assignmentRows, roots, legalEntityRows, all.stream().filter(c -> c.status() != CommandStatus.WITHDRAWN).toList(), byId, relationships);
    }

    private static VersionRow row(ResultSet rs, VersionTable table) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        if (table == VersionTable.EMPLOYEE) {
            values.put(CommandEffects.EMPLOYMENT, rs.getString("employment_status"));
            values.put(CommandEffects.PHASE, rs.getString("employment_phase_kind"));
            values.put(CommandEffects.ON_LEAVE, rs.getBoolean("is_on_unpaid_leave"));
            values.put(CommandEffects.FEE_EARNER, rs.getBoolean("is_fee_earner"));
            values.put(CommandEffects.START_DATE, rs.getObject("employment_start_date", LocalDate.class));
            values.put(CommandEffects.START_BASIS, rs.getString("employment_start_basis_kind"));
        } else if (table == VersionTable.LEGAL_ENTITY) {
            values.put(CommandEffects.LEGAL_ENTITY, rs.getObject("legal_entity_id", UUID.class));
        } else {
            values.put(CommandEffects.ORG_UNIT, rs.getObject("org_unit_id", UUID.class));
            values.put(CommandEffects.POSITION, rs.getObject("position_id", UUID.class));
            values.put(CommandEffects.REPORTS_TO, rs.getObject("reports_to_employee_id", UUID.class));
        }
        ChangeSource source = "COMMAND".equals(rs.getString("change_channel_kind"))
                ? ChangeSource.ofCommand(new PersonnelCommandId(rs.getObject("change_command_id", UUID.class)),
                new AuditEntryId(rs.getObject("version_audit_entry_id", UUID.class))) : ChangeSource.MIGRATION;
        return new VersionRow(rs.getObject("id", UUID.class), rs.getObject("effective_from_date", LocalDate.class), rs.getObject("effective_to_date", LocalDate.class),
                values, source, rs.getObject("corrected_from_version_id", UUID.class), rs.getBoolean("superseded"),
                table == VersionTable.ASSIGNMENT ? rs.getObject("assignment_id", UUID.class) : null,
                table == VersionTable.LEGAL_ENTITY ? null : new EmploymentRelationshipId(rs.getObject("employment_relationship_id", UUID.class)));
    }

    private static final String COMMAND_COLUMNS = """
            select c.personnel_command_id, c.employee_id, c.command_kind, c.effective_date, c.status, c.target_org_unit_id, c.target_position_id,
                   c.target_reports_to_employee_id, c.is_target_fee_earner, c.target_employment_phase_kind, c.target_legal_entity_id,
                   c.countersign_previous_legal_entity_id, c.countersign_new_legal_entity_id, c.target_assignment_id, c.target_assignment_kind, c.employment_relationship_id, c.created_by, c.created_at, c.applied_at, c.version,
                   e.event_count
              from organization_workforce.personnel_commands c join organization_workforce.employees e on e.id = c.employee_id
            """;

    private static PersonnelCommand command(ResultSet rs) throws SQLException {
        UUID unit = rs.getObject("target_org_unit_id", UUID.class);
        UUID position = rs.getObject("target_position_id", UUID.class);
        UUID reportsTo = rs.getObject("target_reports_to_employee_id", UUID.class);
        String phase = rs.getString("target_employment_phase_kind");
        UUID post = rs.getObject("target_assignment_id", UUID.class);
        String postKind = rs.getString("target_assignment_kind");
        OffsetDateTime applied = rs.getObject("applied_at", OffsetDateTime.class);
        UUID target = rs.getObject("target_legal_entity_id", UUID.class);
        UUID previous = rs.getObject("countersign_previous_legal_entity_id", UUID.class);
        UUID next = rs.getObject("countersign_new_legal_entity_id", UUID.class);
        UUID relationship = rs.getObject("employment_relationship_id", UUID.class);
        PersonnelCommand restored = PersonnelCommand.restore(new PersonnelCommandId(rs.getObject("personnel_command_id", UUID.class)),
                new EmployeeId(rs.getObject("employee_id", UUID.class)), PersonnelCommandKind.valueOf(rs.getString("command_kind")),
                rs.getObject("effective_date", LocalDate.class), CommandStatus.valueOf(rs.getString("status")),
                unit == null ? null : new OrgUnitId(unit), position == null ? null : new PositionId(position),
                reportsTo == null ? null : new EmployeeId(reportsTo), (Boolean) rs.getObject("is_target_fee_earner"),
                phase == null ? null : EmploymentPhaseKind.valueOf(phase), target == null ? null : new LegalEntityId(target),
                previous == null ? null : new LegalEntityId(previous), next == null ? null : new LegalEntityId(next), post == null ? null : new AssignmentId(post),
                postKind == null ? null : AssignmentKind.valueOf(postKind), new UserId(rs.getObject("created_by", UUID.class)),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), applied == null ? null : applied.toInstant(), rs.getLong("version"),
                rs.getLong("event_count"));
        if (relationship != null) {
            restored.bindTo(new EmploymentRelationshipId(relationship));
        }
        return restored;
    }

    @Override
    public Optional<PersonnelCommand> find(PersonnelCommandId id) {
        return jdbc.sql(COMMAND_COLUMNS + " where c.personnel_command_id = :id").param("id", id.value()).query((rs, n) -> command(rs)).optional();
    }

    @Override
    public void insert(PersonnelCommand c) {
        Boolean onLeave = c.kind() == PersonnelCommandKind.UNPAID_LEAVE_START ? Boolean.TRUE : c.kind() == PersonnelCommandKind.UNPAID_LEAVE_END ? Boolean.FALSE : null;
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status,
                       target_org_unit_id, target_position_id, target_reports_to_employee_id, is_target_fee_earner, target_employment_phase_kind,
                       is_target_on_unpaid_leave, target_legal_entity_id, countersign_previous_legal_entity_id, countersign_new_legal_entity_id,
                       target_assignment_id, target_assignment_kind, org_structure_command_id, employment_relationship_id, applied_at, created_at, created_by, updated_at, version)
                values (:id, :employee, :kind, :day, :status, :unit, :position, :reportsTo, :feeEarner, :phase, :onLeave, :targetEntity, :previousEntity, :newEntity,
                        :root, :rootKind, :orgCommand, :relationship, :appliedAt, :createdAt, :createdBy, :createdAt, :version)
                """)
                .param("id", c.id().value()).param("employee", c.employeeId().value()).param("kind", c.kind().name()).param("day", c.effectiveDate())
                .param("status", c.status().name()).param("unit", c.targetOrgUnitId() == null ? null : c.targetOrgUnitId().value(), Types.OTHER)
                .param("position", c.targetPositionId() == null ? null : c.targetPositionId().value(), Types.OTHER)
                .param("reportsTo", c.targetReportsToEmployeeId() == null ? null : c.targetReportsToEmployeeId().value(), Types.OTHER)
                .param("feeEarner", c.isTargetFeeEarner(), Types.BOOLEAN)
                .param("phase", c.targetEmploymentPhaseKind() == null ? null : c.targetEmploymentPhaseKind().name(), Types.VARCHAR)
                .param("onLeave", onLeave, Types.BOOLEAN)
                .param("targetEntity", c.targetLegalEntityId() == null ? null : c.targetLegalEntityId().value(), Types.OTHER)
                .param("previousEntity", c.countersignPreviousLegalEntityId() == null ? null : c.countersignPreviousLegalEntityId().value(), Types.OTHER)
                .param("newEntity", c.countersignNewLegalEntityId() == null ? null : c.countersignNewLegalEntityId().value(), Types.OTHER)
                .param("root", c.targetAssignmentId() == null ? null : c.targetAssignmentId().value(), Types.OTHER)
                .param("rootKind", c.targetAssignmentKind() == null ? null : c.targetAssignmentKind().name(), Types.VARCHAR)
                .param("orgCommand", c.orgStructureCommandId() == null ? null : c.orgStructureCommandId().value(), Types.OTHER)
                .param("relationship", c.employmentRelationshipId() == null ? null : c.employmentRelationshipId().value(), Types.OTHER).param("appliedAt", at(c.appliedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("createdAt", at(c.createdAt())).param("createdBy", c.createdBy().value()).param("version", c.version()).update();
    }

    @Override
    public void insertRelationship(EmploymentRelationshipId id, EmployeeId employee, LegalEntityId employer, @Nullable EmploymentRelationshipId predecessor, Instant at) {
        jdbc.sql("""
                insert into organization_workforce.employment_relationships (employment_relationship_id, employee_id, employer_legal_entity_id, predecessor_relationship_id,
                       employer_source_kind, created_at, updated_at)
                values (:id, :employee, :employer, :predecessor, 'DECLARED', :at, :at)
                """)
                .param("id", id.value()).param("employee", employee.value()).param("employer", employer.value())
                .param("predecessor", predecessor == null ? null : predecessor.value(), Types.OTHER).param("at", at(at)).update();
    }

    @Override
    public boolean relationshipExists(EmployeeId employee, LegalEntityId employer) {
        return jdbc.sql("select exists (select 1 from organization_workforce.employment_relationships where employee_id = :employee and employer_legal_entity_id = :employer)")
                .param("employee", employee.value()).param("employer", employer.value()).query(Boolean.class).single();
    }

    @Override
    public AssignmentRoot primaryRoot(EmployeeId employee, Instant at) {
        return new AssignmentRoot(new AssignmentId(PrimaryAssignmentRoot.of(jdbc, ids, employee.value(), at)), AssignmentKind.PRIMARY);
    }

    @Override
    public void insertConcurrentRoot(EmployeeId employee, AssignmentId root, Instant at) {
        ConcurrentAssignmentRoot.insert(jdbc, root.value(), employee.value(), at);
    }

    private static final String CLOSE_EMPLOYEE = "update organization_workforce.employment_relationship_versions set effective_to_date = :to, closed_at = :at, updated_at = :at"
            + " where employment_relationship_version_id = :id";
    private static final String CLOSE_ASSIGNMENT = "update organization_workforce.assignment_versions set effective_to_date = :to, closed_at = :at, updated_at = :at"
            + " where assignment_version_id = :id";
    private static final String SUPERSEDE_EMPLOYEE = "update organization_workforce.employment_relationship_versions set superseded_at = :at, superseding_command_id = :command,"
            + " updated_at = :at where employment_relationship_version_id = :id";
    private static final String SUPERSEDE_ASSIGNMENT = "update organization_workforce.assignment_versions set superseded_at = :at, superseding_command_id = :command,"
            + " updated_at = :at where assignment_version_id = :id";
    private static final String REVISION_EMPLOYEE = "select coalesce(max(revision_number) + 1, 0) from organization_workforce.employment_relationship_versions"
            + " where employment_relationship_id = (select employment_relationship_id from organization_workforce.personnel_commands where personnel_command_id = :commandOwner)"
            + " and effective_from_date = :from";
    private static final String REVISION_ASSIGNMENT = "select coalesce(max(revision_number) + 1, 0) from organization_workforce.assignment_versions"
            + " where employee_id = :employee and effective_from_date = :from";

    @Override
    public void insertVersions(EmployeeId employee, VersionTable table, VersionPlan plan, PersonnelCommandId command, Instant at) {
        if (table == VersionTable.LEGAL_ENTITY) {
            insertLegalEntityVersions(employee, plan, command, at);
            return;
        }
        boolean employeeTable = table == VersionTable.EMPLOYEE;
        for (VersionPlan.Close close : plan.closes()) {
            jdbc.sql(employeeTable ? CLOSE_EMPLOYEE : CLOSE_ASSIGNMENT).param("to", close.to()).param("at", at(at)).param("id", close.id()).update();
        }
        for (UUID id : plan.supersede()) {
            jdbc.sql(employeeTable ? SUPERSEDE_EMPLOYEE : SUPERSEDE_ASSIGNMENT).param("at", at(at)).param("command", command.value()).param("id", id).update();
        }
        for (VersionPlan.NewRow row : plan.inserts()) {
            int revision = employeeTable
                    ? jdbc.sql(REVISION_EMPLOYEE).param("commandOwner", command.value()).param("from", row.from()).query(Integer.class).single()
                    : jdbc.sql(REVISION_ASSIGNMENT).param("employee", employee.value()).param("from", row.from()).query(Integer.class).single();
            if (employeeTable) {
                insertEmployeeVersion(employee, row, revision, at, command);
            } else {
                insertAssignmentVersion(employee, row, revision, at, command);
            }
        }
        if (employeeTable) {
            refreshSnapshot(employee, at);
        }
    }

    /** The derived snapshot columns of the root row follow the relationships (ADR-0086 design 6.2): employed while any relationship is in service on the day, else separated on the latest end. */
    private void refreshSnapshot(EmployeeId employee, Instant at) {
        LocalDate day = zone.dateOf(at);
        boolean inService = jdbc.sql("""
                select exists (select 1 from organization_workforce.employment_relationship_versions
                                where employee_id = :employee and superseded_at is null and employment_status = 'EMPLOYED'
                                  and effective_from_date <= :day and (effective_to_date is null or :day < effective_to_date))
                """).param("employee", employee.value()).param("day", day).query(Boolean.class).single();
        LocalDate lastEnd = inService ? null : jdbc.sql("""
                select max(effective_from_date) from organization_workforce.employment_relationship_versions
                 where employee_id = :employee and superseded_at is null and employment_status = 'SEPARATED' and effective_from_date <= :day
                """).param("employee", employee.value()).param("day", day).query(LocalDate.class).optional().orElse(null);
        jdbc.sql("update organization_workforce.employees set employment_status = :status, separated_at = :separatedAt where id = :employee")
                .param("status", inService ? "EMPLOYED" : "SEPARATED")
                .param("separatedAt", lastEnd == null ? null : lastEnd.atStartOfDay(zone.zone()).toOffsetDateTime(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("employee", employee.value()).update();
    }

    /** The employee's own attribution: its root row is made on the first one; a row has no revision number, a superseded row gives up its day. */
    private void insertLegalEntityVersions(EmployeeId employee, VersionPlan plan, PersonnelCommandId command, Instant at) {
        for (VersionPlan.Close close : plan.closes()) {
            jdbc.sql("update organization_workforce.employee_legal_entities set effective_to_date = :to, closed_at = :at, updated_at = :at where employee_legal_entity_id = :id")
                    .param("to", close.to()).param("at", at(at)).param("id", close.id()).update();
        }
        for (UUID id : plan.supersede()) {
            jdbc.sql("update organization_workforce.employee_legal_entities set superseded_at = :at, superseding_command_id = :command, updated_at = :at"
                    + " where employee_legal_entity_id = :id").param("at", at(at)).param("command", command.value()).param("id", id).update();
        }
        if (!plan.inserts().isEmpty()) {
            jdbc.sql("""
                    insert into organization_workforce.legal_entity_attributions (attribution_id, employee_id)
                    select :id, :employee where not exists (select 1 from organization_workforce.legal_entity_attributions where employee_id = :employee)
                    """).param("id", ids.next()).param("employee", employee.value()).update();
        }
        for (VersionPlan.NewRow row : plan.inserts()) {
            jdbc.sql("""
                    insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, effective_to_date, legal_entity_id,
                           change_channel_kind, change_command_id, version_audit_entry_id, closed_at, corrected_from_version_id, created_at, updated_at)
                    values (:id, :employee, :from, :to, :legalEntity, :channel, :command, :audit, :closedAt, :correctedFrom, :at, :at)
                    """)
                    .param("id", row.id()).param("employee", employee.value()).param("from", row.from()).param("to", row.to(), Types.DATE)
                    .param("legalEntity", row.values().get(CommandEffects.LEGAL_ENTITY))
                    .param("channel", row.source().command() ? "COMMAND" : "MIGRATION").param("command", commandKey(row), Types.OTHER)
                    .param("audit", auditKey(row), Types.OTHER).param("closedAt", row.to() == null ? null : at(at), Types.TIMESTAMP_WITH_TIMEZONE)
                    .param("correctedFrom", row.correctedFrom(), Types.OTHER).param("at", at(at)).update();
        }
    }

    private void insertEmployeeVersion(EmployeeId employee, VersionPlan.NewRow row, int revision, Instant at, PersonnelCommandId command) {
        Map<String, Object> v = row.values();
        jdbc.sql("""
                insert into organization_workforce.employment_relationship_versions (employment_relationship_version_id, employee_id, employment_relationship_id, effective_from_date, effective_to_date, revision_number,
                       employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                       change_channel_kind, change_command_id, version_audit_entry_id, closed_at, corrected_from_version_id, created_at, updated_at)
                values (:id, :employee, (select employment_relationship_id from organization_workforce.personnel_commands where personnel_command_id = :commandOwner), :from, :to, :revision, :status, :phase, :onLeave, :feeEarner, :startDate, :startBasis, :channel, :command, :audit,
                        :closedAt, :correctedFrom, :at, :at)
                """)
                .param("id", row.id()).param("employee", employee.value()).param("commandOwner", command.value()).param("from", row.from()).param("to", row.to(), Types.DATE).param("revision", revision)
                .param("status", v.get(CommandEffects.EMPLOYMENT)).param("phase", v.get(CommandEffects.PHASE), Types.VARCHAR)
                .param("onLeave", v.get(CommandEffects.ON_LEAVE)).param("feeEarner", v.get(CommandEffects.FEE_EARNER))
                .param("startDate", v.get(CommandEffects.START_DATE), Types.DATE).param("startBasis", v.get(CommandEffects.START_BASIS), Types.VARCHAR)
                .param("channel", row.source().command() ? "COMMAND" : "MIGRATION").param("command", commandKey(row), Types.OTHER)
                .param("audit", auditKey(row), Types.OTHER).param("closedAt", row.to() == null ? null : at(at), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("correctedFrom", row.correctedFrom(), Types.OTHER).param("at", at(at)).update();
    }

    private void insertAssignmentVersion(EmployeeId employee, VersionPlan.NewRow row, int revision, Instant at, PersonnelCommandId command) {
        Map<String, Object> v = row.values();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, employment_relationship_id, assignment_id, assignment_kind, effective_from_date,
                       effective_to_date, org_unit_id, position_id, reports_to_employee_id, revision_number, change_channel_kind, change_command_id,
                       version_audit_entry_id, closed_at, corrected_from_version_id, created_at, updated_at)
                values (:id, :employee, coalesce((select v.employment_relationship_id from organization_workforce.assignment_versions v where v.assignment_id = :root and :keepOwner and v.effective_from_date <= :from order by v.effective_from_date desc, v.revision_number desc limit 1), (select employment_relationship_id from organization_workforce.personnel_commands where personnel_command_id = :commandOwner)), :root, (select assignment_kind from organization_workforce.employee_assignments where assignment_id = :root), :from, :to, :unit, :position, :reportsTo, :revision, :channel, :command, :audit, :closedAt, :correctedFrom, :at, :at)
                """)
                .param("id", row.id()).param("employee", employee.value()).param("commandOwner", command.value())
                .param("keepOwner", keepsRelationship(command))
                .param("root", row.root() != null ? row.root() : PrimaryAssignmentRoot.of(jdbc, ids, employee.value(), at))
                .param("from", row.from()).param("to", row.to(), Types.DATE)
                .param("unit", v.get(CommandEffects.ORG_UNIT)).param("position", v.get(CommandEffects.POSITION), Types.OTHER)
                .param("reportsTo", v.get(CommandEffects.REPORTS_TO), Types.OTHER).param("revision", revision)
                .param("channel", row.source().command() ? "COMMAND" : "MIGRATION").param("command", commandKey(row), Types.OTHER)
                .param("audit", auditKey(row), Types.OTHER).param("closedAt", row.to() == null ? null : at(at), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("correctedFrom", row.correctedFrom(), Types.OTHER).param("at", at(at)).update();
    }

    /** A hire, a reinstatement or a start of a concurrent post puts rows into its own relationship; any other command edits the rows in force and stays in theirs. */
    private boolean keepsRelationship(PersonnelCommandId command) {
        String kind = jdbc.sql("select command_kind from organization_workforce.personnel_commands where personnel_command_id = :id").param("id", command.value()).query(String.class).single();
        return !Set.of("HIRE", "REINSTATE", "START_CONCURRENT").contains(kind);
    }

    @Override
    public void update(PersonnelCommand c, Instant at, long loadedVersion) {
        int updated = jdbc.sql("""
                update organization_workforce.personnel_commands set status = :status, applied_at = :appliedAt, withdrawn_at = :at, withdrawn_by = :by, withdrawal_reason = :reason,
                       updated_at = :now, version = :version
                 where personnel_command_id = :id and version = :loaded
                """)
                .param("status", c.status().name()).param("appliedAt", at(c.appliedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("at", at(c.withdrawnAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("by", c.withdrawnBy() == null ? null : c.withdrawnBy().value(), Types.OTHER).param("reason", c.withdrawalReason(), Types.VARCHAR)
                .param("now", at(at)).param("version", loadedVersion + 1).param("id", c.id().value()).param("loaded", loadedVersion).update();
        if (updated != 1) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT, "the command was changed by someone else");
        }
    }

    private static final String HOLD_COLUMNS = """
            select separation_hold_id, personnel_command_id, employee_id, status, version, released_at, released_by, release_reason, release_channel_kind
              from organization_workforce.separation_holds
            """;

    private static SeparationHold hold(ResultSet rs) throws SQLException {
        OffsetDateTime released = rs.getObject("released_at", OffsetDateTime.class);
        UUID by = rs.getObject("released_by", UUID.class);
        String channel = rs.getString("release_channel_kind");
        return SeparationHold.restore(new SeparationHoldId(rs.getObject("separation_hold_id", UUID.class)),
                new PersonnelCommandId(rs.getObject("personnel_command_id", UUID.class)), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                HoldStatus.valueOf(rs.getString("status")), rs.getLong("version"), released == null ? null : released.toInstant(), by == null ? null : new UserId(by),
                rs.getString("release_reason"), channel == null ? null : ReleaseChannelKind.valueOf(channel));
    }

    @Override
    public Optional<SeparationHold> holdOf(PersonnelCommandId command) {
        return jdbc.sql(HOLD_COLUMNS + " where personnel_command_id = :id").param("id", command.value()).query((rs, n) -> hold(rs)).optional();
    }

    @Override
    public Optional<SeparationHold> findHold(SeparationHoldId id) {
        return jdbc.sql(HOLD_COLUMNS + " where separation_hold_id = :id").param("id", id.value()).query((rs, n) -> hold(rs)).optional();
    }

    @Override
    public void insertHold(SeparationHold hold, Instant at) {
        jdbc.sql("""
                insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status, created_at, updated_at, version)
                values (:id, :command, :employee, :status, :at, :at, :version)
                """)
                .param("id", hold.id().value()).param("command", hold.commandId().value()).param("employee", hold.employeeId().value())
                .param("status", hold.status().name()).param("at", at(at)).param("version", hold.version()).update();
    }

    @Override
    public void updateHold(SeparationHold hold, Instant at, long loadedVersion) {
        int updated = jdbc.sql("""
                update organization_workforce.separation_holds set status = :status, released_at = :releasedAt, released_by = :releasedBy,
                       release_reason = :reason, release_channel_kind = :channel, updated_at = :at, version = :version
                 where separation_hold_id = :id and version = :loaded
                """)
                .param("status", hold.status().name()).param("releasedAt", at(hold.releasedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("releasedBy", hold.releasedBy() == null ? null : hold.releasedBy().value(), Types.OTHER).param("reason", hold.releaseReason(), Types.VARCHAR)
                .param("channel", hold.releaseChannel() == null ? null : hold.releaseChannel().name(), Types.VARCHAR).param("at", at(at))
                .param("version", hold.version()).param("id", hold.id().value()).param("loaded", loadedVersion).update();
        if (updated != 1) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT, "the hold was changed by someone else");
        }
    }

    @Override
    public void insertTransition(String aggregateType, UUID aggregateId, long sequence, @Nullable String from, String to, ActorType actorType, UUID actorId, LocalDate day, Instant at,
                                 PersonnelCommandId command, UUID correlation) {
        jdbc.sql("""
                insert into organization_workforce.state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status,
                       occurred_on_date, recorded_at, actor_type, actor_id, cause_kind, command_id, correlation_id, created_at, updated_at)
                values (:id, :type, :aggregate, :sequence, :from, :to, :day, :at, :actorType, :actor, 'COMMAND', :command, :correlation, :at, :at)
                """)
                .param("id", ids.next()).param("type", aggregateType).param("aggregate", aggregateId).param("sequence", sequence)
                .param("from", from, Types.VARCHAR).param("to", to).param("day", day).param("at", at(at)).param("actorType", actorType.name()).param("actor", actorId)
                .param("command", command.value()).param("correlation", correlation).update();
    }

    @Override
    public long eventSequence(EmployeeId employee) {
        return jdbc.sql("select event_count from organization_workforce.employees where id = :id").param("id", employee.value()).query(Long.class).single();
    }

    @Override
    public void saveEventSequence(EmployeeId employee, long sequence) {
        jdbc.sql("update organization_workforce.employees set event_count = :sequence where id = :id").param("sequence", sequence).param("id", employee.value()).update();
    }

    private static @Nullable UUID commandKey(VersionPlan.NewRow row) {
        return row.source().commandId() == null ? null : row.source().commandId().value();
    }

    private static @Nullable UUID auditKey(VersionPlan.NewRow row) {
        return row.source().auditEntryId() == null ? null : row.source().auditEntryId().value();
    }

    private static @Nullable OffsetDateTime at(@Nullable Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
