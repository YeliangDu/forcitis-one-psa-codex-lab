package com.forcitis.one.cost.timesheet.infrastructure.persistence;

import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTimesheetRepository implements TimesheetRepository {

    private static final String COLUMNS = "id, timesheet_number, employee_id, legal_entity_id, org_unit_id, work_date,"
            + " submitted_hours, project_manager_id, status, round_number, submitted_date, event_count, version";

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcTimesheetRepository(JdbcClient jdbc, Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Timesheet> find(TimesheetId timesheetId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM cost_timesheet.timesheets WHERE id = :id")
                .param("id", timesheetId.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public Optional<Timesheet> findForUpdate(TimesheetId timesheetId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM cost_timesheet.timesheets WHERE id = :id FOR UPDATE")
                .param("id", timesheetId.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public void lockForCreate(TimesheetId timesheetId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", "timesheet-create:" + timesheetId.value())
                .query().singleValue();
    }

    @Override
    public void save(Timesheet timesheet) {
        if (timesheet.isNew()) {
            try {
                jdbc.sql("""
                        INSERT INTO cost_timesheet.timesheets (id, timesheet_number, employee_id, legal_entity_id, org_unit_id,
                               work_date, submitted_hours, project_manager_id, status, round_number, submitted_date, event_count, version)
                        VALUES (:id, :number, :employee, :legalEntity, :orgUnit, :workDate, :hours, :manager, :status, :round,
                                :submittedDate, :sequence, :next)
                        """)
                        .param("id", timesheet.timesheetId().value()).param("number", timesheet.number().value())
                        .param("employee", timesheet.employee().value()).param("legalEntity", timesheet.legalEntityId().value())
                        .param("orgUnit", timesheet.orgUnitId().value())
                        .param("workDate", timesheet.workDate()).param("hours", timesheet.submittedHours().value())
                        .param("manager", timesheet.projectManager().value()).param("status", timesheet.status().name())
                        .param("round", timesheet.round()).param("submittedDate", timesheet.submittedOn().orElse(null))
                        .param("sequence", timesheet.eventSequenceAfterSave()).param("next", timesheet.versionAfterSave()).update();
            } catch (DuplicateKeyException exists) {
                throw changedByOthers(timesheet);
            }
            return;
        }
        int updated = jdbc.sql("""
                UPDATE cost_timesheet.timesheets
                   SET submitted_hours = :hours, project_manager_id = :manager, status = :status,
                       round_number = :round, submitted_date = :submittedDate, event_count = :sequence, updated_at = :updatedAt, version = :next
                 WHERE id = :id AND version = :version AND status = :previous
                """)
                .param("hours", timesheet.submittedHours().value())
                .param("manager", timesheet.projectManager().value()).param("status", timesheet.status().name())
                .param("round", timesheet.round()).param("submittedDate", timesheet.submittedOn().orElse(null))
                .param("sequence", timesheet.eventSequenceAfterSave()).param("next", timesheet.versionAfterSave())
                .param("id", timesheet.timesheetId().value()).param("version", timesheet.version())
                .param("previous", timesheet.loadedStatus().name()).param("updatedAt", Timestamp.from(clock.instant()))
                .update();
        if (updated != 1) {
            throw changedByOthers(timesheet);
        }
    }

    private static DomainException changedByOthers(Timesheet timesheet) {
        return TimesheetError.COST_TIMESHEET_CONCURRENT_CHANGE.refusal("Timesheet " + timesheet.timesheetId().value()
                + " was changed by somebody else");
    }

    private static Timesheet read(ResultSet rs) throws SQLException {
        return Timesheet.restore(new TimesheetId(rs.getObject("id", UUID.class)), new DocumentNumber(rs.getString("timesheet_number")),
                new EmployeeId(rs.getObject("employee_id", UUID.class)), new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)),
                new OrgUnitId(rs.getObject("org_unit_id", UUID.class)),
                rs.getObject("work_date", LocalDate.class), new Hours(rs.getBigDecimal("submitted_hours")),
                new EmployeeId(rs.getObject("project_manager_id", UUID.class)), TimesheetStatus.valueOf(rs.getString("status")), rs.getInt("round_number"),
                rs.getObject("submitted_date", LocalDate.class), rs.getLong("version"), rs.getLong("event_count"));
    }
}
