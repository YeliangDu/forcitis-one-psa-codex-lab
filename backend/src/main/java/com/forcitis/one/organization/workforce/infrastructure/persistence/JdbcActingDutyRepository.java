package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.ActingDuty;
import com.forcitis.one.organization.workforce.domain.ActingDutyRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
                   covered_employee_id, starts_on_date, ends_before_date, ended_on_date, reason, source_personnel_command_id, created_by, created_at
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
                   and ended_on_date is null and starts_on_date < :to and :from < ends_before_date
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

    private static ActingDuty row(ResultSet rs) throws SQLException {
        UUID position = rs.getObject("covered_position_id", UUID.class);
        UUID covered = rs.getObject("covered_employee_id", UUID.class);
        return new ActingDuty(new ActingDutyId(rs.getObject("acting_duty_id", UUID.class)), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                new EmploymentRelationshipId(rs.getObject("employment_relationship_id", UUID.class)), new OrgUnitId(rs.getObject("covered_org_unit_id", UUID.class)),
                position == null ? null : new PositionId(position), covered == null ? null : new EmployeeId(covered),
                rs.getObject("starts_on_date", LocalDate.class), rs.getObject("ends_before_date", LocalDate.class), rs.getObject("ended_on_date", LocalDate.class),
                rs.getString("reason"), new PersonnelCommandId(rs.getObject("source_personnel_command_id", UUID.class)), new UserId(rs.getObject("created_by", UUID.class)),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
