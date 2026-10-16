package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.application.TimesheetView;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * TS-UNIQ acceptance item 11 (card TS-UNIQ section 4, ADR-0033 item 1 point 5): a timesheet created through the use case keeps the id of
 * the numbering rule version its number was issued under. Rows migrated from the old system hold no id.
 */
@Testcontainers
class TimesheetNumberingRuleVersionIT extends TimesheetWorkerSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    @Autowired
    DocumentNumbers numbers;

    @Autowired
    TransactionTemplate transaction;

    private UUID confirmedVersionOn(LocalDate day) {
        return jdbc.sql("""
                SELECT v.numbering_rule_version_id
                  FROM platform_numbering.numbering_rule_versions v
                  JOIN platform_numbering.numbering_rules r ON r.numbering_rule_id = v.numbering_rule_id
                 WHERE r.document_type = 'TIMESHEET' AND v.status = 'CONFIRMED' AND v.effective_from_date <= :day
                 ORDER BY v.effective_from_date DESC, v.version_number DESC LIMIT 1
                """).param("day", day).query(UUID.class).single();
    }

    private UUID storedVersionOf(TimesheetId timesheet) {
        return jdbc.sql("SELECT numbering_rule_version_id FROM cost_timesheet.timesheets WHERE id = :id").param("id", timesheet.value())
                .query((rs, n) -> rs.getObject(1, UUID.class)).list().getFirst();
    }

    @Test
    @DisplayName("TS-UNIQ 新建的工时单记录发号时所用的编号规则版本 ID，且等于发号时生效的已确认版本；新建路径取不到版本 ID 时失败并告警，不写空值（库内该列可空，为迁入行保留）")
    void aNewTimesheetKeepsTheRuleVersionItsNumberWasIssuedUnder() throws Exception {
        SignedIn worker = newWorker();
        UUID inForce = confirmedVersionOn(TODAY);
        IssuedNumber probe = transaction.execute(status -> numbers.next(DocumentType.TIMESHEET, new LegalEntityId(legalEntity), TODAY));
        assertThat(probe.ruleVersionId().value()).as("the numbering service names the confirmed version in force").isEqualTo(inForce);

        TimesheetId created = newTimesheetId();
        TimesheetView view = submit(worker, created, TODAY.minusDays(3), "8");

        assertThat(view.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(storedVersionOf(created)).as("numbering_rule_version_id of the new row").isEqualTo(inForce);
    }

    @Test
    @DisplayName("TS-UNIQ 迁入行（编号规则版本 ID 为空）仍可读取：该列为空表示未经本系统发号")
    void aMigratedRowWithoutARuleVersionCanStillBeRead() throws Exception {
        SignedIn worker = newWorker();
        TimesheetId migrated = newTimesheetId();
        jdbc.sql("""
                INSERT INTO cost_timesheet.timesheets (id, employee_id, legal_entity_id, work_date, submitted_hours, project_manager_id, status,
                                                      timesheet_number, org_unit_id)
                VALUES (:id, :employee, :legalEntity, :day, 8, :manager, 'APPROVED', :number, :unit)
                """).param("id", migrated.value()).param("employee", stored(worker.employeeId())).param("legalEntity", legalEntity)
                .param("day", TODAY.minusDays(20)).param("manager", stored(projectManager)).param("number", "TSUNIQ-MIGRATED-" + migrated.value())
                .param("unit", unit).update();

        actInSession(worker);
        TimesheetView view = getTimesheet.execute(migrated);

        assertThat(view.status()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(storedVersionOf(migrated)).isNull();
    }
}
