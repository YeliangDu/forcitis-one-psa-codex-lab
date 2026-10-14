package com.forcitis.one.cost.timesheet.infrastructure.persistence;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.StaleTimesheetException;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTimesheetRepository implements TimesheetRepository {

    private static final String COLUMNS = "id, employee_id, legal_entity_id, work_package_id, work_date, submitted_hours, project_manager_id,"
            + " status, round_number, approval_instance_id, event_count, version";

    private final JdbcClient jdbc;

    JdbcTimesheetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Timesheet> find(TimesheetId timesheetId) {
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
            jdbc.sql("""
                    INSERT INTO cost_timesheet.timesheets (id, employee_id, legal_entity_id, work_package_id, work_date, submitted_hours,
                           project_manager_id, status, round_number, approval_instance_id, event_count)
                    VALUES (:id, :employee, :legalEntity, :workPackage, :workDate, :hours, :manager, :status, :round, :instance, :sequence)
                    """)
                    .param("id", timesheet.timesheetId().value()).param("employee", timesheet.employee().value())
                    .param("legalEntity", timesheet.legalEntityId().value()).param("workPackage", timesheet.workPackageId().value())
                    .param("workDate", timesheet.workDate()).param("hours", timesheet.submittedHours().value())
                    .param("manager", timesheet.projectManager().value()).param("status", timesheet.status().name())
                    .param("round", timesheet.round()).param("instance", timesheet.approvalInstanceId().map(ApprovalInstanceId::value).orElse(null))
                    .param("sequence", timesheet.eventSequence()).update();
            return;
        }
        int updated = jdbc.sql("""
                UPDATE cost_timesheet.timesheets
                   SET work_package_id = :workPackage, work_date = :workDate, submitted_hours = :hours, project_manager_id = :manager,
                       status = :status, round_number = :round, approval_instance_id = :instance, event_count = :sequence,
                       updated_at = now(), version = version + 1
                 WHERE id = :id AND version = :version AND status = :previous
                """)
                .param("workPackage", timesheet.workPackageId().value()).param("workDate", timesheet.workDate())
                .param("hours", timesheet.submittedHours().value()).param("manager", timesheet.projectManager().value())
                .param("status", timesheet.status().name()).param("round", timesheet.round())
                .param("instance", timesheet.approvalInstanceId().map(ApprovalInstanceId::value).orElse(null))
                .param("sequence", timesheet.eventSequence()).param("id", timesheet.timesheetId().value())
                .param("version", timesheet.loadedVersion()).param("previous", timesheet.loadedStatus().name()).update();
        if (updated != 1) {
            throw new StaleTimesheetException("Timesheet " + timesheet.timesheetId().value() + " was changed by somebody else");
        }
    }

    private static Timesheet read(ResultSet rs) throws SQLException {
        String instance = rs.getString("approval_instance_id");
        return Timesheet.restore(new TimesheetId(rs.getLong("id")), new UserId(rs.getLong("employee_id")),
                new LegalEntityId(rs.getLong("legal_entity_id")), new WorkPackageId(rs.getLong("work_package_id")),
                rs.getObject("work_date", LocalDate.class), new Hours(rs.getBigDecimal("submitted_hours")),
                new UserId(rs.getLong("project_manager_id")), TimesheetStatus.valueOf(rs.getString("status")), rs.getInt("round_number"),
                instance == null ? null : new ApprovalInstanceId(instance), rs.getLong("event_count"), rs.getLong("version"));
    }
}
