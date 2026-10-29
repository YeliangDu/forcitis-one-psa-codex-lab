package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.application.TimesheetView;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TS-UNIQ acceptance item 11 (card TS-UNIQ section 4, ADR-0033 item 1 point 5): a timesheet created through the use case keeps the id of
 * the numbering rule version its number was issued under. Rows migrated from the old system hold no id.
 */
class TimesheetNumberingRuleVersionIT extends TimesheetWorkerSupport {

    static final TestDatabase postgres = TestPostgres.database(TimesheetNumberingRuleVersionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @MockitoSpyBean
    DocumentNumbers numbers;

    @Autowired
    TransactionTemplate transaction;

    private UUID storedVersionOf(TimesheetId timesheet) {
        return jdbc.sql("SELECT numbering_rule_version_id FROM cost_timesheet.timesheets WHERE id = :id").param("id", timesheet.value())
                .query((rs, n) -> rs.getObject(1, UUID.class)).list().getFirst();
    }

    @Test
    @DisplayName("TS-UNIQ 新建的工时单记录发号时所用的编号规则版本 ID，且等于发号时生效的已确认版本；新建路径取不到版本 ID 时失败并告警，不写空值（库内该列可空，为迁入行保留）")
    void aNewTimesheetKeepsTheRuleVersionItsNumberWasIssuedUnder() throws Exception {
        SignedIn worker = newWorker();
        List<IssuedNumber> issuedForTimesheet = new CopyOnWriteArrayList<>();
        transaction.executeWithoutResult(tx -> doAnswer(call -> {
            IssuedNumber issued = (IssuedNumber) call.callRealMethod();
            issuedForTimesheet.add(issued);
            return issued;
        }).when(numbers).next(any(), any(), any()));

        TimesheetId created = newTimesheetId();
        TimesheetView view = submit(worker, created, TODAY.minusDays(3), "8");

        assertThat(issuedForTimesheet).as("the use case asked the numbering service for exactly one timesheet number").hasSize(1);
        UUID inForce = issuedForTimesheet.getFirst().ruleVersionId().value();

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
