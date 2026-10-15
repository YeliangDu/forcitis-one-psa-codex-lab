package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.application.CancelTimesheet;
import com.forcitis.one.cost.timesheet.application.GetTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import com.forcitis.one.cost.timesheet.application.TimesheetView;
import com.forcitis.one.cost.timesheet.application.WithdrawTimesheet;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.identity.api.CurrentUser;
import java.time.Duration;
import java.time.LocalDate;
import java.util.OptionalLong;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P2 review P2-M2 (NEXT_BATCH_PLAN 7.2 C4, ADR-0033 section 3 item 3 「本人」判定清单补入): a timesheet belongs to an employee, the
 * caller is an account. Submitting, reading, cancelling and withdrawing compare the timesheet's employee with the employee behind the
 * signed-in account; the legal entity of the current user is that employee's own attribution. Two people sign in through WeCom, so their
 * account ids are UUIDs that equal neither employee id. The timesheet module has no HTTP entry yet: its use cases are called with the
 * account in the security context, as a controller would.
 */
@Testcontainers
class TimesheetOwnerAccountsIT extends AccountsAcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    @Autowired
    SubmitTimesheet submitTimesheet;

    @Autowired
    GetTimesheet getTimesheet;

    @Autowired
    CancelTimesheet cancelTimesheet;

    @Autowired
    WithdrawTimesheet withdrawTimesheet;

    @Autowired
    CurrentUser currentUser;

    long projectManager;
    SignedIn owner;
    SignedIn other;
    UUID ownerLegalEntity;
    UUID otherLegalEntity;

    @BeforeEach
    void twoPeopleWithAccountsAndLegalEntities() throws Exception {
        maintainWorkCalendar();
        long unit = newOrgUnitId();
        orgUnit(unit, null, null);
        long maintainer = newEmployeeId();
        long ownerEmployee = newEmployeeId();
        long otherEmployee = newEmployeeId();
        projectManager = newEmployeeId();
        for (long person : new long[] {maintainer, ownerEmployee, otherEmployee, projectManager}) {
            employee(person, unit, null);
        }
        UUID maintainerAccount = signIn(maintainer).userId();
        owner = signIn(ownerEmployee);
        other = signIn(otherEmployee);
        signIn(projectManager);
        ownerLegalEntity = createLegalEntity(newLegalEntityCode("TO"), "Synthetic owner entity");
        otherLegalEntity = createLegalEntity(newLegalEntityCode("TX"), "Synthetic other entity");
        attributeEmployee(maintainerAccount, ownerEmployee, ownerLegalEntity);
        attributeEmployee(maintainerAccount, otherEmployee, otherLegalEntity);
        confirmSeededNumberingRule("TIMESHEET", maintainerAccount);
    }

    private static LocalDate workDay() {
        return TODAY.minusDays(3);
    }

    private TimesheetView submit(SignedIn as, TimesheetId timesheet) {
        actAs(as.userId());
        return submitTimesheet.execute(new SubmitTimesheetCommand(timesheet, workDay(), Hours.of("8"), new EmployeeId(projectManager)));
    }

    /** The approval starts from the submitted event, after the commit: waits until withdrawing is offered to the owner. */
    private void awaitApprovalOf(SignedIn as, TimesheetId timesheet) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> {
            actAs(as.userId());
            return getTimesheet.execute(timesheet).canWithdraw();
        });
    }

    private static void refusedAsNotFound(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                refused -> assertThat(refused.errorCode().code()).isEqualTo("COST_TIMESHEET_NOT_FOUND"));
    }

    @Test
    @DisplayName("P2-M2 C4 工时单本人检查（真实账号，uuid ≠ 员工 ID）：A 对 B 的工时单提交、读取、取消、撤回四个动作都答「没有该工时单」，B 的工时单不变；B 对自己的工时单四个动作都成功")
    void onlyTheOwnerActsOnATimesheet() {
        TimesheetId withdrawn = new TimesheetId(UUID.randomUUID());
        TimesheetId cancelled = new TimesheetId(UUID.randomUUID());
        TimesheetView submitted = submit(other, withdrawn);
        assertThat(submitted.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        submit(other, cancelled);
        awaitApprovalOf(other, withdrawn);
        awaitApprovalOf(other, cancelled);

        actAs(owner.userId());
        refusedAsNotFound(() -> submitTimesheet.execute(new SubmitTimesheetCommand(withdrawn, workDay(), Hours.of("4"), new EmployeeId(projectManager))));
        refusedAsNotFound(() -> getTimesheet.execute(withdrawn));
        refusedAsNotFound(() -> cancelTimesheet.execute(cancelled, "synthetic: not mine", OptionalLong.empty()));
        refusedAsNotFound(() -> withdrawTimesheet.execute(withdrawn, "synthetic: not mine", OptionalLong.empty()));

        actAs(other.userId());
        TimesheetView untouched = getTimesheet.execute(withdrawn);
        assertThat(untouched.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(untouched.version()).isEqualTo(submitted.version());
        assertThat(submit(other, withdrawn).status()).as("submitting again one's own timesheet in approval answers it as it is").isEqualTo(TimesheetStatus.SUBMITTED);
        actAs(other.userId());
        withdrawTimesheet.execute(withdrawn, "synthetic: wrong hours", OptionalLong.empty());
        cancelTimesheet.execute(cancelled, "synthetic: not needed", OptionalLong.empty());
    }

    @Test
    @DisplayName("P2-M2 C4 工时单本人检查（真实账号）：A 提交的新工时单记在 A 的员工名下与 A 的法人下，A 自己能读到")
    void aNewTimesheetIsFiledUnderTheSignedInEmployee() {
        TimesheetId own = new TimesheetId(UUID.randomUUID());

        TimesheetView submitted = submit(owner, own);

        assertThat(submitted.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        actAs(owner.userId());
        assertThat(getTimesheet.execute(own).timesheetId()).isEqualTo(own);
        actAs(other.userId());
        refusedAsNotFound(() -> getTimesheet.execute(own));
    }

    @Test
    @DisplayName("P2-M2 C4 SessionCurrentUser 查法人（真实账号）：当前用户的员工 ID 是账号背后的员工，法人取自本人员工归属，而不是拿账号 uuid 去查")
    void theCurrentUsersLegalEntityIsTheirEmployeesAttribution() {
        actAs(owner.userId());
        assertThat(currentUser.userId().value()).isEqualTo(owner.userId());
        assertThat(currentUser.employeeId()).isEqualTo(new EmployeeId(owner.employeeId()));
        assertThat(currentUser.legalEntityId()).isEqualTo(new LegalEntityId(ownerLegalEntity));

        actAs(other.userId());
        assertThat(currentUser.employeeId()).isEqualTo(new EmployeeId(other.employeeId()));
        assertThat(currentUser.legalEntityId()).isEqualTo(new LegalEntityId(otherLegalEntity));
    }
}
