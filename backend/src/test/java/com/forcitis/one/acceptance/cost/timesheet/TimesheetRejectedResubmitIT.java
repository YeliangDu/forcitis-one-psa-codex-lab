package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestIds;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.application.CancelTimesheet;
import com.forcitis.one.cost.timesheet.application.GetTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import com.forcitis.one.cost.timesheet.application.TimesheetView;
import com.forcitis.one.cost.timesheet.events.TimesheetApprovalWithdrawn;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.InboxItem;
import com.forcitis.one.platform.approval.application.ListInbox;
import com.forcitis.one.platform.approval.application.ListPage;
import com.forcitis.one.platform.approval.application.TaskDecision;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * TS-UNIQ acceptance items 4 and 6 (card TS-UNIQ section 4): a rejected timesheet is revised and submitted again as the same timesheet,
 * and the approval takes it as a new round; an approved one is final. The rejection is the top-level one of the cut-off process variant:
 * in the production process every node sits inside the review sub-process, where a rejection is a return.
 */
@EnableScenarios
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.modulith.test.PublishedEventsExtension.class)
class TimesheetRejectedResubmitIT extends ApprovalIntegrationSupport {

    static final AtomicLong TIMESHEETS = new AtomicLong(700_000);
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    SubmitTimesheet submitTimesheet;

    @Autowired
    GetTimesheet getTimesheet;

    @Autowired
    CancelTimesheet cancelTimesheet;

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ListInbox listInbox;

    @Autowired
    ApprovalProcesses approvalProcesses;

    long employee;
    long manager;

    @BeforeEach
    void people() {
        employee = newId();
        manager = newId();
        organisation.person(employee, 1, null).person(manager, 1, null);
    }

    @AfterEach
    void signOutAgain() {
        SecurityContextHolder.clearContext();
    }

    void signInAs(long user) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(TestAccounts.account(user).value().toString(), "x", "ROLE_TEST"));
    }

    static TimesheetId newTimesheet() {
        return new TimesheetId(TestIds.uuid(TIMESHEETS.incrementAndGet()));
    }

    static LocalDate workDay() {
        return LocalDate.now(ZONE).minusDays(1);
    }

    static DocumentReference documentOf(TimesheetId id) {
        return new DocumentReference(new DocumentTypeCode("TIMESHEET"), new DocumentId(id.value()));
    }

    TimesheetView submit(TimesheetId id, LocalDate day, String hours) {
        signInAs(employee);
        return submitTimesheet.execute(new SubmitTimesheetCommand(id, day, Hours.of(hours), TestAccounts.employee(manager)));
    }

    TimesheetView view(TimesheetId id) {
        signInAs(employee);
        return getTimesheet.execute(id);
    }

    void awaitApprovalRound(TimesheetId id, int round) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                .until(() -> approvalProcesses.progress(TimesheetApprovalProcess.KEY, documentOf(id))
                        .filter(progress -> progress.round().equals(new RoundNumber(round))).isPresent() && !inboxOf(id).isEmpty());
    }

    void awaitStatus(TimesheetId id, TimesheetStatus status) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50)).until(() -> view(id).status() == status);
    }

    List<InboxItem> inboxOf(TimesheetId id) {
        return listInbox.execute(TestAccounts.account(manager), ListPage.first()).items().stream()
                .filter(item -> item.documentId().filter(id.value()::equals).isPresent()).toList();
    }

    ApprovalTaskId taskOf(TimesheetId id) {
        return new ApprovalTaskId(UUID.fromString(inboxOf(id).getFirst().taskId()));
    }

    void decide(ApprovalTaskId task, DecisionOutcome outcome, String reason) {
        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(task, outcome, reason)), OptionalLong.empty());
    }

    @Test
    @DisplayName("TS-UNIQ 工时单被驳回后，填报人可修改原单并再次提交：状态 REJECTED→SUBMITTED，仍是同一张单，轮次加一，审批发起新一轮；已通过的工时单不能再提交")
    void aRejectedTimesheetIsRevisedAndSubmittedAgainAsTheNextRound() {
        deployVariant("cutoff");
        TimesheetId id = newTimesheet();
        LocalDate day = workDay();
        TimesheetView first = submit(id, day, "9");
        awaitApprovalRound(id, 1);
        ApprovalTaskId firstTask = taskOf(id);
        decide(firstTask, DecisionOutcome.REJECT, "synthetic: hours not credible");
        awaitStatus(id, TimesheetStatus.REJECTED);
        assertThat(view(id).round()).isEqualTo(1);

        LocalDate dayBefore = LocalDate.now(ZONE);
        Throwable refused = catchThrowable(() -> submit(id, day, "7.5"));
        LocalDate dayAfter = LocalDate.now(ZONE);

        assertThat(refused).as("submitting the rejected timesheet again").isNull();
        TimesheetView again = view(id);
        assertThat(again.timesheetId()).isEqualTo(id);
        assertThat(again.number()).as("the same timesheet keeps its number").isEqualTo(first.number());
        assertThat(again.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(again.round()).isEqualTo(2);
        assertThat(again.workDate()).isEqualTo(day);
        assertThat(jdbc.queryForObject("select submitted_hours from cost_timesheet.timesheets where id = ?", java.math.BigDecimal.class, id.value()))
                .as("the revised hours are stored").isEqualByComparingTo("7.5");
        assertThat(jdbc.queryForObject("select submitted_date from cost_timesheet.timesheets where id = ?", LocalDate.class, id.value()))
                .as("submittedOn is the Shanghai day of the new submission").isBetween(dayBefore, dayAfter);
        awaitApprovalRound(id, 2);
        assertThat(taskOf(id)).as("the approval opened a task of its own for the new round").isNotEqualTo(firstTask);
        assertThat(jdbc.queryForObject("select count(*) from cost_timesheet.timesheets where employee_id = ? and work_date = ?", Integer.class,
                TestAccounts.employee(employee).value(), day)).as("still one timesheet for that day").isEqualTo(1);
    }

    @Test
    @DisplayName("TS-UNIQ 已通过的工时单不能再提交，状态与轮次不变")
    void anApprovedTimesheetCannotBeSubmittedAgain() {
        TimesheetId id = newTimesheet();
        LocalDate day = workDay();
        submit(id, day, "8");
        awaitApprovalRound(id, 1);
        decide(taskOf(id), DecisionOutcome.APPROVE, "ok");
        awaitStatus(id, TimesheetStatus.APPROVED);

        Throwable refused = catchThrowable(() -> submit(id, day, "6"));

        assertThat(refused).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode().code()).isEqualTo("KERNEL_TRANSITION_ILLEGAL"));
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(view(id).round()).isEqualTo(1);
    }

    @Test
    @DisplayName("TS-UNIQ 撤销审批使 SUBMITTED→DRAFT，发布的事件类型为 cost.timesheet.TimesheetApprovalWithdrawn，旧名不存在")
    void cancellingTheApprovalTurnsASubmittedTimesheetBackIntoADraft(Scenario scenario) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        submit(id, workDay(), "3");
        awaitApprovalRound(id, 1);
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);

        scenario.stimulate(() -> {
            signInAs(employee);
            cancelTimesheet.execute(id, "synthetic: duplicate", OptionalLong.empty());
        }).andWaitForEventOfType(TimesheetApprovalWithdrawn.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
            assertThat(e.envelope().eventType()).isEqualTo("cost.timesheet.TimesheetApprovalWithdrawn");
            assertThat(e.envelope().eventType()).doesNotContain("TimesheetCancelled");
        });

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.DRAFT);
    }
}
