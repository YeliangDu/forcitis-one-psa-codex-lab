package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.TestIds;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.application.CancelTimesheet;
import com.forcitis.one.cost.timesheet.application.GetTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import com.forcitis.one.cost.timesheet.application.TimesheetView;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TimesheetRoundSubmittedCheckIT extends ApprovalIntegrationSupport {

    static final String PAIR = "ck_timesheets_round_submitted";
    static final String AFTER_SUBMIT = "ck_timesheets_round_after_submit";
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final AtomicLong TIMESHEETS = new AtomicLong(800_000);
    static final LocalDate STALE = LocalDate.of(2026, 1, 5);
    static final LocalDate DIRECT_DAY = LocalDate.of(2026, 3, 2);

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

    /** The real Shanghai day at this moment (the application runs on the real clock here), read through the one sanctioned reader. */
    static LocalDate shanghaiDayNow() {
        return BusinessDay.instant().atZone(ZONE).toLocalDate();
    }

    static LocalDate workDay(int daysBack) {
        return BusinessDay.today().minusDays(daysBack);
    }

    static DocumentReference documentOf(TimesheetId id) {
        return new DocumentReference(new DocumentTypeCode("TIMESHEET"), new DocumentId(id.value()));
    }

    /** Submits and returns the Shanghai days just before and just after the call, so a midnight crossing cannot fail the test. */
    LocalDate[] submit(TimesheetId id, LocalDate day, String hours) {
        signInAs(employee);
        LocalDate before = shanghaiDayNow();
        submitTimesheet.execute(new SubmitTimesheetCommand(id, day, Hours.of(hours), TestAccounts.employee(manager)));
        return new LocalDate[] {before, shanghaiDayNow()};
    }

    TimesheetView view(TimesheetId id) {
        signInAs(employee);
        return getTimesheet.execute(id);
    }

    LocalDate submittedDateOf(TimesheetId id) {
        return jdbc.queryForObject("select submitted_date from cost_timesheet.timesheets where id = ?", LocalDate.class, id.value());
    }

    int roundOf(TimesheetId id) {
        return jdbc.queryForObject("select round_number from cost_timesheet.timesheets where id = ?", Integer.class, id.value());
    }

    void backdateSubmittedDate(TimesheetId id) {
        assertThat(jdbc.update("update cost_timesheet.timesheets set submitted_date = ? where id = ?", STALE, id.value())).isEqualTo(1);
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

    void decide(TimesheetId id, DecisionOutcome outcome) {
        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(taskOf(id), outcome, "synthetic")), OptionalLong.empty());
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 首次提交后轮次为 1，提交日为当天（上海业务日）")
    void firstSubmissionIsRoundOneSubmittedToday() {
        TimesheetId id = newTimesheet();

        LocalDate[] days = submit(id, workDay(1), "8");

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(roundOf(id)).isEqualTo(1);
        assertThat(submittedDateOf(id)).isBetween(days[0], days[1]);
        awaitApprovalRound(id, 1);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 退回后重新提交，轮次为 2，提交日更新为重提当天")
    void resubmissionAfterReturnIsRoundTwoWithANewSubmittedDate() {
        TimesheetId id = newTimesheet();
        submit(id, workDay(2), "8");
        awaitApprovalRound(id, 1);
        decide(id, DecisionOutcome.REJECT);
        awaitStatus(id, TimesheetStatus.RETURNED);
        backdateSubmittedDate(id);
        assertThat(roundOf(id)).isEqualTo(1);

        LocalDate[] days = submit(id, workDay(2), "7");

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(roundOf(id)).isEqualTo(2);
        assertThat(submittedDateOf(id)).isBetween(days[0], days[1]);
        awaitApprovalRound(id, 2);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 被驳回后修改原单再提交，轮次加一，提交日更新")
    void resubmissionAfterRejectionIsTheNextRoundWithANewSubmittedDate() {
        deployVariant("cutoff");
        TimesheetId id = newTimesheet();
        submit(id, workDay(3), "8");
        awaitApprovalRound(id, 1);
        decide(id, DecisionOutcome.REJECT);
        awaitStatus(id, TimesheetStatus.REJECTED);
        backdateSubmittedDate(id);
        assertThat(roundOf(id)).isEqualTo(1);

        LocalDate[] days = submit(id, workDay(3), "6");

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(roundOf(id)).isEqualTo(2);
        assertThat(submittedDateOf(id)).isBetween(days[0], days[1]);
        awaitApprovalRound(id, 2);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 撤销审批回草稿时保留轮次与提交日，再次提交轮次加一")
    void withdrawingToDraftKeepsRoundAndSubmittedDateAndResubmissionAddsOne() {
        TimesheetId id = newTimesheet();
        submit(id, workDay(4), "8");
        awaitApprovalRound(id, 1);
        backdateSubmittedDate(id);

        signInAs(employee);
        cancelTimesheet.execute(id, "synthetic: wrong hours", OptionalLong.empty());
        awaitStatus(id, TimesheetStatus.DRAFT);

        assertThat(roundOf(id)).as("round kept on the draft").isEqualTo(1);
        assertThat(submittedDateOf(id)).as("submitted date kept on the draft").isEqualTo(STALE);

        LocalDate[] days = submit(id, workDay(4), "5");

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(roundOf(id)).isEqualTo(2);
        assertThat(submittedDateOf(id)).isBetween(days[0], days[1]);
        awaitApprovalRound(id, 2);
    }

    // RULE-476
    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 审批通过、审批驳回的回写不改轮次与提交日")
    void approvalResultsDoNotTouchRoundOrSubmittedDate() {
        TimesheetId approved = newTimesheet();
        submit(approved, workDay(5), "8");
        awaitApprovalRound(approved, 1);
        backdateSubmittedDate(approved);
        decide(approved, DecisionOutcome.APPROVE);
        awaitStatus(approved, TimesheetStatus.APPROVED);
        assertThat(roundOf(approved)).isEqualTo(1);
        assertThat(submittedDateOf(approved)).isEqualTo(STALE);

        TimesheetId returned = newTimesheet();
        submit(returned, workDay(6), "8");
        awaitApprovalRound(returned, 1);
        backdateSubmittedDate(returned);
        decide(returned, DecisionOutcome.REJECT);
        awaitStatus(returned, TimesheetStatus.RETURNED);
        assertThat(roundOf(returned)).isEqualTo(1);
        assertThat(submittedDateOf(returned)).isEqualTo(STALE);

        deployVariant("cutoff");
        TimesheetId rejected = newTimesheet();
        submit(rejected, workDay(7), "8");
        awaitApprovalRound(rejected, 1);
        backdateSubmittedDate(rejected);
        decide(rejected, DecisionOutcome.REJECT);
        awaitStatus(rejected, TimesheetStatus.REJECTED);
        assertThat(roundOf(rejected)).isEqualTo(1);
        assertThat(submittedDateOf(rejected)).isEqualTo(STALE);
    }

    private void insert(String status, int round, LocalDate submittedDate, UUID ruleVersion, UUID employeeId, LocalDate day) throws SQLException {
        try (Connection db = jdbc.getDataSource().getConnection();
                PreparedStatement insert = db.prepareStatement("""
                        INSERT INTO cost_timesheet.timesheets (id, employee_id, legal_entity_id, work_date, submitted_hours, project_manager_id, status,
                                                              round_number, timesheet_number, numbering_rule_version_id, org_unit_id, submitted_date)
                        VALUES (?, ?, ?, ?, 8, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            UUID id = UUID.randomUUID();
            insert.setObject(1, id);
            insert.setObject(2, employeeId);
            insert.setObject(3, UUID.randomUUID());
            insert.setObject(4, day);
            insert.setObject(5, UUID.randomUUID());
            insert.setString(6, status);
            insert.setInt(7, round);
            insert.setString(8, "TA01-" + id);
            insert.setObject(9, ruleVersion);
            insert.setObject(10, UUID.randomUUID());
            insert.setObject(11, submittedDate);
            insert.executeUpdate();
        }
    }

    private void insert(String status, int round, LocalDate submittedDate, UUID ruleVersion) throws SQLException {
        insert(status, round, submittedDate, ruleVersion, UUID.randomUUID(), DIRECT_DAY);
    }

    private static void assertRefusedBy(String constraint, SqlAction action) {
        try {
            action.run();
        } catch (PSQLException refused) {
            assertThat(refused.getSQLState()).as("check violation: " + refused.getMessage()).isEqualTo("23514");
            assertThat(refused.getServerErrorMessage()).isNotNull();
            assertThat(refused.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
            return;
        } catch (SQLException other) {
            throw new AssertionError(other);
        }
        throw new AssertionError("the row was accepted, expected a refusal by " + constraint);
    }

    @FunctionalInterface
    interface SqlAction {
        void run() throws SQLException;
    }

    private long rowsOf(UUID employeeId) {
        return jdbc.queryForObject("select count(*) from cost_timesheet.timesheets where employee_id = ?", Long.class, employeeId);
    }

    // RULE-475
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"SUBMITTED", "RETURNED", "APPROVED", "REJECTED"})
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 直接插入 SUBMITTED、RETURNED、APPROVED、REJECTED 且轮次为 0、提交日为空、版本 ID 非空，被拒，SQLState 23514，约束名为 ck_timesheets_round_after_submit")
    void aNewNonDraftTimesheetWithRoundZeroIsRefused(String status) {
        UUID who = UUID.randomUUID();

        assertRefusedBy(AFTER_SUBMIT, () -> insert(status, 0, null, UUID.randomUUID(), who, DIRECT_DAY));

        assertThat(rowsOf(who)).isZero();
    }

    // RULE-475
    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 轮次 ≥ 1 而提交日为空被拒，轮次为 0 而提交日非空被拒，约束名为 ck_timesheets_round_submitted")
    void roundAndSubmittedDateMustBeZeroAndEmptyTogether() {
        UUID who = UUID.randomUUID();
        UUID version = UUID.randomUUID();

        assertRefusedBy(PAIR, () -> insert("SUBMITTED", 1, null, version, who, DIRECT_DAY));
        assertRefusedBy(PAIR, () -> insert("DRAFT", 1, null, version, who, DIRECT_DAY.plusDays(1)));
        assertRefusedBy(PAIR, () -> insert("APPROVED", 3, null, null, who, DIRECT_DAY.plusDays(2)));
        assertRefusedBy(PAIR, () -> insert("DRAFT", 0, STALE, version, who, DIRECT_DAY.plusDays(3)));
        assertRefusedBy(PAIR, () -> insert("APPROVED", 0, STALE, null, who, DIRECT_DAY.plusDays(5)));

        assertThat(rowsOf(who)).isZero();
    }

    // RULE-475
    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 草稿且轮次 0、提交日空可以插入；草稿且轮次 ≥ 1、提交日非空（撤销审批后的形态）可以插入")
    void draftsInBothShapesCanBeInserted() throws SQLException {
        UUID who = UUID.randomUUID();

        insert("DRAFT", 0, null, UUID.randomUUID(), who, DIRECT_DAY);
        insert("DRAFT", 2, STALE, UUID.randomUUID(), who, DIRECT_DAY.plusDays(1));

        assertThat(rowsOf(who)).isEqualTo(2);
    }

    // RULE-475
    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 迁入单（APPROVED、轮次 0、提交日空、版本 ID 空）可以插入；同一形态但版本 ID 非空被拒")
    void aMigratedTimesheetWithoutARuleVersionCanBeInsertedButOneWithAVersionCannot() throws SQLException {
        UUID who = UUID.randomUUID();

        insert("APPROVED", 0, null, null, who, DIRECT_DAY);
        assertThat(rowsOf(who)).isEqualTo(1);

        assertRefusedBy(AFTER_SUBMIT, () -> insert("APPROVED", 0, null, UUID.randomUUID(), who, DIRECT_DAY.plusDays(1)));
        assertThat(rowsOf(who)).isEqualTo(1);
    }

    private String definitionOf(String constraint) {
        List<String> found = jdbc.queryForList("""
                SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c
                WHERE c.conrelid = 'cost_timesheet.timesheets'::regclass AND c.conname = ? AND c.contype = 'c' AND c.convalidated
                """, String.class, constraint);
        assertThat(found).as("validated CHECK constraint %s on cost_timesheet.timesheets", constraint).hasSize(1);
        return found.getFirst();
    }

    /** Spacing, parentheses and type casts are PostgreSQL's choice of spelling, not meaning. */
    private static String plain(String definition) {
        return definition.replaceAll("::[a-z_ ]+?(?=[ )]|$)", "").replace("(", " ").replace(")", " ").replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-01 读 pg_constraint 核对两条约束定义与名字")
    void theTwoConstraintsExistWithTheAgreedDefinitions() {
        assertThat(plain(definitionOf(PAIR))).isEqualTo("CHECK round_number = 0 = submitted_date IS NULL");

        String afterSubmit = plain(definitionOf(AFTER_SUBMIT));
        assertThat(afterSubmit).startsWith("CHECK ");
        assertThat(Arrays.asList(afterSubmit.substring("CHECK ".length()).split(" OR "))).containsExactlyInAnyOrder(
                "status = 'DRAFT'", "round_number >= 1", "numbering_rule_version_id IS NULL");
    }

    private String commentOn(String column) {
        List<String> found = new ArrayList<>(jdbc.queryForList("""
                SELECT col_description(a.attrelid, a.attnum) FROM pg_attribute a
                WHERE a.attrelid = 'cost_timesheet.timesheets'::regclass AND a.attname = ? AND NOT a.attisdropped
                """, String.class, column));
        assertThat(found).as("column %s of cost_timesheet.timesheets", column).hasSize(1);
        assertThat(found.getFirst()).as("comment on %s", column).isNotNull();
        return found.getFirst();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit T-8 legal_entity_id 列注释与实现一致")
    void columnCommentsMatchTheImplementation() {
        assertThat(commentOn("legal_entity_id")).contains("工作日期").doesNotContain("传入");
        assertThat(commentOn("round_number")).contains("迁入单");
        assertThat(commentOn("submitted_date")).contains("迁入单");
    }
}
