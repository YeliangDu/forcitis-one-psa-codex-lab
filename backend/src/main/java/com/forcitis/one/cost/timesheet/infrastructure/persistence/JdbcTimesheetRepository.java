package com.forcitis.one.cost.timesheet.infrastructure.persistence;

import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTimesheetRepository implements TimesheetRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTimesheetRepository.class);
    private static final String SELECT = """
            select id, timesheet_number, numbering_rule_version_id, employee_id, legal_entity_id, org_unit_id, work_date,
                   submitted_hours, project_manager_id, status, round_number, submitted_date, event_count, version
              from cost_timesheet.timesheets
            """;

    private static final String CLASH_OF_EMPLOYEE_DAY = "uk_timesheets_employee_id_work_date";
    private static final String CLASH_OF_NUMBER = "uk_timesheets_timesheet_number";
    private static final String CLASH_OF_ID = "pk_timesheets";

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcTimesheetRepository(JdbcClient jdbc, Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Timesheet> find(TimesheetId timesheetId) {
        return jdbc.sql(SELECT + """
                 where id = :id
                """)
                .param("id", timesheetId.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public boolean existsFor(EmployeeId employee, LocalDate workDate) {
        return jdbc.sql("""
                select exists (select 1 from cost_timesheet.timesheets where employee_id = :employee and work_date = :workDate)
                """)
                .param("employee", employee.value()).param("workDate", workDate).query(Boolean.class).single();
    }

    @Override
    public Optional<Timesheet> findForUpdate(TimesheetId timesheetId) {
        return jdbc.sql(SELECT + """
                 where id = :id
                   for update
                """)
                .param("id", timesheetId.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public void lockForCreate(TimesheetId timesheetId) {
        jdbc.sql("""
                select pg_advisory_xact_lock(hashtextextended(:key, 0))
                """).param("key", "timesheet-create:" + timesheetId.value())
                .query().singleValue();
    }

    @Override
    public void save(Timesheet timesheet) {
        if (timesheet.isNew()) {
            try {
                jdbc.sql("""
                        insert into cost_timesheet.timesheets (id, timesheet_number, numbering_rule_version_id, employee_id, legal_entity_id, org_unit_id,
                               work_date, submitted_hours, project_manager_id, status, round_number, submitted_date, event_count, version)
                        values (:id, :number, :ruleVersion, :employee, :legalEntity, :orgUnit, :workDate, :hours, :manager, :status, :round,
                                :submittedDate, :sequence, :next)
                        """)
                        .param("id", timesheet.timesheetId().value()).param("number", timesheet.number().value())
                        .param("ruleVersion", timesheet.numberingRuleVersionId().map(NumberingRuleVersionId::value).orElseThrow())
                        .param("employee", timesheet.employee().value()).param("legalEntity", timesheet.legalEntityId().value())
                        .param("orgUnit", timesheet.orgUnitId().value())
                        .param("workDate", timesheet.workDate()).param("hours", timesheet.submittedHours().value())
                        .param("manager", timesheet.projectManager().value()).param("status", timesheet.status().name())
                        .param("round", timesheet.round()).param("submittedDate", timesheet.submittedOn().orElse(null))
                        .param("sequence", timesheet.eventSequenceAfterSave()).param("next", timesheet.versionAfterSave()).update();
            } catch (DuplicateKeyException exists) {
                throw clashOf(exists, timesheet);
            }
            return;
        }
        int updated = jdbc.sql("""
                update cost_timesheet.timesheets
                   set submitted_hours = :hours, project_manager_id = :manager, status = :status,
                       round_number = :round, submitted_date = :submittedDate, event_count = :sequence, updated_at = :updatedAt, version = :next
                 where id = :id and version = :version and status = :previous
                """)
                .param("hours", timesheet.submittedHours().value())
                .param("manager", timesheet.projectManager().value()).param("status", timesheet.status().name())
                .param("round", timesheet.round()).param("submittedDate", timesheet.submittedOn().orElse(null))
                .param("sequence", timesheet.eventSequenceAfterSave()).param("next", timesheet.versionAfterSave())
                .param("id", timesheet.timesheetId().value()).param("version", timesheet.version())
                .param("previous", timesheet.loadedStatus().name()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        if (updated != 1) {
            throw changedByOthers(timesheet);
        }
    }

    private static DomainException clashOf(DuplicateKeyException exists, Timesheet timesheet) {
        String constraint = ConstraintNames.of(exists);
        if (CLASH_OF_EMPLOYEE_DAY.equals(constraint)) {
            return TimesheetError.COST_TIMESHEET_WORK_DATE_DUPLICATE.refusal("The employee already has a timesheet for " + timesheet.workDate());
        }
        if (CLASH_OF_NUMBER.equals(constraint) || CLASH_OF_ID.equals(constraint)) {
            return changedByOthers(timesheet);
        }
        log.error("ALERT P2 cost.timesheet a timesheet was refused by the unique constraint {}: {}", constraint, exists.getClass().getName());
        throw exists;
    }

    private static DomainException changedByOthers(Timesheet timesheet) {
        return KernelError.KERNEL_CONCURRENT_CHANGE.refusal("Timesheet " + timesheet.timesheetId().value()
                + " was changed by somebody else");
    }

    private static Timesheet read(ResultSet rs) throws SQLException {
        return Timesheet.restore(new TimesheetId(rs.getObject("id", UUID.class)), new DocumentNumber(rs.getString("timesheet_number")),
                Optional.ofNullable(rs.getObject("numbering_rule_version_id", UUID.class)).map(NumberingRuleVersionId::new).orElse(null),
                new EmployeeId(rs.getObject("employee_id", UUID.class)), new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)),
                new OrgUnitId(rs.getObject("org_unit_id", UUID.class)),
                rs.getObject("work_date", LocalDate.class), new Hours(rs.getBigDecimal("submitted_hours")),
                new EmployeeId(rs.getObject("project_manager_id", UUID.class)), TimesheetStatus.valueOf(rs.getString("status")), rs.getInt("round_number"),
                rs.getObject("submitted_date", LocalDate.class), rs.getLong("version"), rs.getLong("event_count"));
    }
}
