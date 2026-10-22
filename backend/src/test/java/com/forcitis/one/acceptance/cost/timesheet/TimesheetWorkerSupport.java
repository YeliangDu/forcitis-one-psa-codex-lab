package com.forcitis.one.acceptance.cost.timesheet;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.application.GetTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import com.forcitis.one.cost.timesheet.application.TimesheetView;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * TS-UNIQ: real accounts, real numbering rule in force, one legal entity, one employed project manager; every test makes its own
 * employees, so (employee, work date) pairs never meet across tests.
 */
abstract class TimesheetWorkerSupport extends AccountsAcceptanceSupport {

    static final String WORK_DATE_DUPLICATE = "COST_TIMESHEET_WORK_DATE_DUPLICATE";
    static final String CONCURRENT_CHANGE = "KERNEL_CONCURRENT_CHANGE";

    @Autowired
    protected SubmitTimesheet submitTimesheet;

    @Autowired
    protected GetTimesheet getTimesheet;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected DocumentNumbers documentNumbers;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    protected UUID unit;
    protected UUID legalEntity;
    protected UUID projectManager;
    protected SignedIn maintainerSession;

    @BeforeEach
    void organisationWithANumberedTimesheetRule() throws Exception {
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID maintainer = newEmployeeId();
        projectManager = newEmployeeId();
        employee(maintainer, unit, null);
        employee(projectManager, unit, null);
        grantByOperations(maintainer, "org_data_admin");
        grantByOperations(maintainer, "business_admin");
        maintainerSession = signIn(maintainer);
        signIn(projectManager);
        maintainWorkCalendar(maintainerSession);
        legalEntity = createLegalEntity(maintainerSession, newLegalEntityCode("TU"), "Synthetic TS-UNIQ entity");
        confirmSeededNumberingRule("TIMESHEET", maintainerSession);
    }

    protected SignedIn newWorker() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        SignedIn signedIn = signIn(person);
        attributeEmployee(maintainerSession, person, legalEntity);
        return signedIn;
    }

    protected TimesheetView submit(SignedIn as, TimesheetId id, LocalDate day, String hours) {
        actInSession(as);
        return submitTimesheet.execute(new SubmitTimesheetCommand(id, day, Hours.of(hours), employeeIdOf(projectManager)));
    }

    protected static TimesheetId newTimesheetId() {
        return new TimesheetId(UUID.randomUUID());
    }

    protected static void refusedWith(String code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                refused -> assertThat(refused.errorCode().code()).isEqualTo(code));
    }

    protected long timesheetRowsOf(UUID employee, LocalDate day) {
        return jdbc.sql("SELECT count(*) FROM cost_timesheet.timesheets WHERE employee_id = :employee AND work_date = :day")
                .param("employee", stored(employee)).param("day", day).query(Long.class).single();
    }

    /** The sequence the next timesheet number would carry, asked through the numbering api inside a transaction that is rolled back, so nothing is consumed. */
    protected long nextTimesheetSequence() {
        String number = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return documentNumbers.next(new DocumentTypeCode("TIMESHEET"), new LegalEntityId(legalEntity), TODAY).value();
        });
        var digits = java.util.regex.Pattern.compile("(\\d+)$").matcher(number);
        assertThat(digits.find()).as("the timesheet number %s ends in its sequence", number).isTrue();
        return Long.parseLong(digits.group(1));
    }
}
