package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.ActingDuty;
import com.forcitis.one.organization.workforce.domain.ActingDutyRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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

/** Dated coverage facts in the workforce schema; database exclusions remain the final integrity check. */
@Repository
class JdbcActingDutyRepository implements ActingDutyRepository {
    private final JdbcClient jdbc;

    JdbcActingDutyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLUMNS = """
            select acting_duty_id, employee_id, employment_relationship_id, covered_org_unit_id, covered_position_id,
                   covered_employee_id, starts_on_date, ends_before_date, ended_on_date, reason, source_personnel_command_id, created_by, created_at, version
              from organization_workforce.acting_duties
            """;

    @Override
    public void insert(ActingDuty duty) {
        jdbc.sql("""
                insert into organization_workforce.acting_duties (acting_duty_id, employee_id, employment_relationship_id, covered_org_unit_id,
                       covered_position_id, covered_employee_id, starts_on_date, ends_before_date, reason, source_personnel_command_id, created_by,
                       created_at, updated_at, version)
                values (:id, :employee, :relationship, :unit, :position, :coveredEmployee, :from, :to, :reason, :command, :by, :at, :at, 0)
                """)
                .param("id", duty.id().value()).param("employee", duty.employeeId().value()).param("relationship", duty.employmentRelationshipId().value())
                .param("unit", duty.coveredOrgUnitId().value()).param("position", duty.coveredPositionId() == null ? null : duty.coveredPositionId().value(), Types.OTHER)
                .param("coveredEmployee", duty.coveredEmployeeId() == null ? null : duty.coveredEmployeeId().value(), Types.OTHER)
                .param("from", duty.startsOn()).param("to", duty.endsBefore()).param("reason", duty.reason())
                .param("command", duty.sourcePersonnelCommandId().value()).param("by", duty.createdBy().value())
                .param("at", OffsetDateTime.ofInstant(duty.createdAt(), ZoneOffset.UTC)).update();
    }

    @Override
    public List<ActingDuty> findOverlap(EmployeeId employee, OrgUnitId unit, @Nullable PositionId position, LocalDate from, LocalDate to) {
        return jdbc.sql(COLUMNS + """
                 where employee_id = :employee and covered_org_unit_id = :unit and covered_position_id is not distinct from cast(:position as uuid)
                   and starts_on_date < :to and :from < coalesce(ended_on_date, ends_before_date)
                 order by starts_on_date, acting_duty_id
                """)
                .param("employee", employee.value()).param("unit", unit.value()).param("position", position == null ? null : position.value(), Types.OTHER)
                .param("from", from).param("to", to).query((rs, n) -> row(rs)).list();
    }

    @Override
    public List<ActingDuty> listOpen(EmployeeId employee) {
        return jdbc.sql(COLUMNS + " where employee_id = :employee and ended_on_date is null order by starts_on_date, acting_duty_id")
                .param("employee", employee.value()).query((rs, n) -> row(rs)).list();
    }

    @Override
    public boolean lockEmployee(EmployeeId employee) {
        return jdbc.sql("select 1 from organization_workforce.employees where id = :id for update")
                .param("id", employee.value()).query(Integer.class).optional().isPresent();
    }

    @Override
    public void saveEventSequence(EmployeeId employee, long sequence) {
        int changed = jdbc.sql("""
                update organization_workforce.employees set event_count = :sequence
                 where id = :id and event_count = :previous
                """).param("id", employee.value()).param("sequence", sequence).param("previous", sequence - 1).update();
        requireUpdated(changed);
    }

    @Override
    public Optional<ActingDuty> find(ActingDutyId id) {
        return jdbc.sql(COLUMNS + " where acting_duty_id = :id").param("id", id.value()).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public List<ActingDutyId> openDue(LocalDate today, int limit) {
        return jdbc.sql("""
                select acting_duty_id from organization_workforce.acting_duties
                 where ended_on_date is null and ends_before_date <= :today
                 order by ends_before_date, acting_duty_id limit :limit
                """).param("today", today).param("limit", limit)
                .query((rs, n) -> new ActingDutyId(rs.getObject("acting_duty_id", UUID.class))).list();
    }

    @Override
    public void updateEnd(ActingDuty loaded, LocalDate endedOn, ActingDutyEndCause cause, Instant at) {
        int changed = jdbc.sql("""
                update organization_workforce.acting_duties
                   set ended_on_date = :end, end_cause_kind = :cause, updated_at = :at, version = version + 1
                 where acting_duty_id = :id and version = :version and ended_on_date is null
                """).param("end", endedOn).param("cause", cause.name()).param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                .param("id", loaded.id().value()).param("version", loaded.version()).update();
        requireUpdated(changed);
    }

    @Override
    public void updateEndsBefore(ActingDuty loaded, LocalDate endsBefore, Instant at) {
        int changed = jdbc.sql("""
                update organization_workforce.acting_duties
                   set ends_before_date = :end, updated_at = :at, version = version + 1
                 where acting_duty_id = :id and version = :version and ended_on_date is null and ends_before_date < :end
                """).param("end", endsBefore).param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                .param("id", loaded.id().value()).param("version", loaded.version()).update();
        requireUpdated(changed);
    }

    private static void requireUpdated(int changed) {
        if (changed != 1) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                    "the acting duty was changed by someone else");
        }
    }

    private static ActingDuty row(ResultSet rs) throws SQLException {
        UUID position = rs.getObject("covered_position_id", UUID.class);
        UUID covered = rs.getObject("covered_employee_id", UUID.class);
        return new ActingDuty(new ActingDutyId(rs.getObject("acting_duty_id", UUID.class)), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                new EmploymentRelationshipId(rs.getObject("employment_relationship_id", UUID.class)), new OrgUnitId(rs.getObject("covered_org_unit_id", UUID.class)),
                position == null ? null : new PositionId(position), covered == null ? null : new EmployeeId(covered),
                rs.getObject("starts_on_date", LocalDate.class), rs.getObject("ends_before_date", LocalDate.class), rs.getObject("ended_on_date", LocalDate.class),
                rs.getString("reason"), new PersonnelCommandId(rs.getObject("source_personnel_command_id", UUID.class)), new UserId(rs.getObject("created_by", UUID.class)),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getLong("version"));
    }
}
