package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
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
import com.forcitis.one.kernel.CurrentUser;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P2 review P2-M2 (NEXT_BATCH_PLAN 7.2 C4, ADR-0033 section 3 item 3 「本人」判定清单补入): a timesheet belongs to an employee, the
 * caller is an account. Submitting, reading, cancelling and withdrawing compare the timesheet's employee with the employee behind the
 * signed-in account; the legal entity of the current user is that employee's own attribution. Two people sign in through WeCom, so their
 * account ids are UUIDs that equal neither employee id. The timesheet module has no HTTP entry yet: its use cases run with the security
 * context that the login stored in the person's session, read back from the Spring Session repository (RULING-P2-8), as a controller
 * behind the session filter would; everything else is done over HTTP in the sessions of the people.
 */
class TimesheetOwnerAccountsIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(TimesheetOwnerAccountsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

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

    UUID projectManager;
    SignedIn owner;
    SignedIn other;
    UUID ownerLegalEntity;
    UUID otherLegalEntity;

    @BeforeEach
    void twoPeopleWithAccountsAndLegalEntities() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID maintainer = newEmployeeId();
        UUID ownerEmployee = newEmployeeId();
        UUID otherEmployee = newEmployeeId();
        projectManager = newEmployeeId();
        for (UUID person : new UUID[] {maintainer, ownerEmployee, otherEmployee, projectManager}) {
            employee(person, unit, null);
        }
        grantByOperations(maintainer, "org_data_admin");
        grantByOperations(maintainer, "business_admin");
        SignedIn maintainerSession = signIn(maintainer);
        owner = signIn(ownerEmployee);
        other = signIn(otherEmployee);
        signIn(projectManager);
        maintainWorkCalendar(maintainerSession);
        ownerLegalEntity = createLegalEntity(maintainerSession, newLegalEntityCode("TO"), "Synthetic owner entity");
        otherLegalEntity = createLegalEntity(maintainerSession, newLegalEntityCode("TX"), "Synthetic other entity");
        attributeEmployee(maintainerSession, ownerEmployee, ownerLegalEntity);
        attributeEmployee(maintainerSession, otherEmployee, otherLegalEntity);
        confirmSeededNumberingRule("TIMESHEET", maintainerSession);
    }

    private static LocalDate workDay() {
        return TODAY.minusDays(3);
    }

    private TimesheetView submit(SignedIn as, TimesheetId timesheet) {
        return submit(as, timesheet, workDay());
    }

    private TimesheetView submit(SignedIn as, TimesheetId timesheet, LocalDate day) {
        actInSession(as);
        return submitTimesheet.execute(new SubmitTimesheetCommand(timesheet, day, Hours.of("8"), employeeIdOf(projectManager)));
    }

    /** The approval starts from the submitted event, after the commit: waits until withdrawing is offered to the owner. */
    private void awaitApprovalOf(SignedIn as, TimesheetId timesheet) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> {
            actInSession(as);
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
        submit(other, cancelled, workDay().minusDays(1));
        awaitApprovalOf(other, withdrawn);
        awaitApprovalOf(other, cancelled);

        actInSession(owner);
        refusedAsNotFound(() -> submitTimesheet.execute(new SubmitTimesheetCommand(withdrawn, workDay(), Hours.of("4"), employeeIdOf(projectManager))));
        refusedAsNotFound(() -> getTimesheet.execute(withdrawn));
        refusedAsNotFound(() -> cancelTimesheet.execute(cancelled, "synthetic: not mine", OptionalLong.empty()));
        refusedAsNotFound(() -> withdrawTimesheet.execute(withdrawn, "synthetic: not mine", OptionalLong.empty()));

        actInSession(other);
        TimesheetView untouched = getTimesheet.execute(withdrawn);
        assertThat(untouched.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(untouched.version()).isEqualTo(submitted.version());
        assertThat(submit(other, withdrawn).status()).as("submitting again one's own timesheet in approval answers it as it is").isEqualTo(TimesheetStatus.SUBMITTED);
        actInSession(other);
        withdrawTimesheet.execute(withdrawn, "synthetic: wrong hours", OptionalLong.empty());
        cancelTimesheet.execute(cancelled, "synthetic: not needed", OptionalLong.empty());
    }

    @Test
    @DisplayName("P2-M2 C4 工时单本人检查（真实账号）：A 提交的新工时单记在 A 名下：A 自己能读到，B 读答「没有该工时单」")
    void aNewTimesheetIsFiledUnderTheSignedInEmployee() {
        TimesheetId own = new TimesheetId(UUID.randomUUID());

        TimesheetView submitted = submit(owner, own);

        assertThat(submitted.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        actInSession(owner);
        assertThat(getTimesheet.execute(own).timesheetId()).isEqualTo(own);
        actInSession(other);
        refusedAsNotFound(() -> getTimesheet.execute(own));
    }

    @Test
    @DisplayName("P2-M2 C4 SessionCurrentUser 查法人（真实账号、登录所得的生产会话）：当前用户的员工 ID 是账号背后的员工，法人取自本人员工归属，而不是拿账号 uuid 去查")
    void theCurrentUsersLegalEntityIsTheirEmployeesAttribution() {
        actInSession(owner);
        assertThat(currentUser.userId().value()).isEqualTo(owner.userId());
        assertThat(currentUser.employeeId()).isEqualTo(employeeIdOf(owner.employeeId()));
        assertThat(currentUser.legalEntityId()).isEqualTo(new LegalEntityId(ownerLegalEntity));

        actInSession(other);
        assertThat(currentUser.employeeId()).isEqualTo(employeeIdOf(other.employeeId()));
        assertThat(currentUser.legalEntityId()).isEqualTo(new LegalEntityId(otherLegalEntity));
    }
}
