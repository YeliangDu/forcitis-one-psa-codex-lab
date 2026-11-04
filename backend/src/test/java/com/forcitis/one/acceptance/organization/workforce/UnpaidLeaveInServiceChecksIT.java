package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #873 IMP-1030-PAR-B4 (ADR-0068 section 3, lines 104 to 110): unpaid leave is a flag on the employee version, not a third employment
 * status. The person keeps account, sessions and explicit grants (login admission and session principal, the first two of the five
 * in-service checks), is skipped when approval assignees are resolved (the third), and a holder of a two-person or an ADR-0009 sensitive role
 * gets a 「敏感角色复核」. The timesheet submission check (the fourth) follows the user decision of 2026-10-30 (Q2 = 丙): during unpaid leave a
 * timesheet may be submitted only for days before the leave starts. The timesheet module has no HTTP entry, so its submit use case is
 * called in the session of the person, as the other timesheet acceptance tests do.
 */
class UnpaidLeaveInServiceChecksIT extends SeparationHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(UnpaidLeaveInServiceChecksIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    static final String TASKS = "/api/v1/platform/approval-tasks/";
    static final String SENSITIVE_ROLE_REVIEW = "SENSITIVE_ROLE_REVIEW";
    /** Before TODAY, so that the leave is in force today. */
    static final LocalDate LEAVE_FROM = TODAY.minusDays(3);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ApprovalProcesses approvals;

    @Autowired
    SubmitTimesheet submitTimesheet;

    private void startLeave(Staff who) throws Exception {
        actIn(hr);
        UUID command = enter(who.employeeId(), UNPAID_LEAVE_START, LEAVE_FROM);
        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
    }

    private List<String> permissionCodesOf(Staff who) throws Exception {
        MockHttpServletResponse current = mvc.perform(get("/api/v1/platform/sessions:current").with(session(who.session()))).andReturn().getResponse();
        assertThat(current.getStatus()).as(current.getContentAsString()).isEqualTo(200);
        return JsonPath.read(current.getContentAsString(), "$.permissionCodes");
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 106、107）：停薪留职开始后在职状态仍是 EMPLOYED，版本上 is_on_unpaid_leave 为真（此前为假），账号唯一且不变，已登录的会话仍有效、权限码不变（显式授予不自动关闭），重新登录通过且仍是同一账号")
    void accountSessionsAndGrantsSurviveTheLeave() throws Exception {
        Staff person = account("org_data_admin");
        List<String> before = permissionCodesOf(person);

        startLeave(person);

        assertThat(employmentOn(person.employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(onUnpaidLeaveOn(person.employeeId(), TODAY)).isTrue();
        assertThat(onUnpaidLeaveOn(person.employeeId(), LEAVE_FROM.minusDays(1))).isFalse();
        assertContiguous(currentVersions(EMPLOYEE_VERSIONS, person.employeeId()));
        assertThat(profileStatus(person)).as("login session principal: the session of the person stays valid").isEqualTo(200);
        assertThat(permissionCodesOf(person)).as("explicit grants stay").containsExactlyInAnyOrderElementsOf(before);
        assertThat(signIn(person.employeeId()).userId()).as("login admission: the person can sign in, on the same account").isEqualTo(person.userId());
        assertThat(accountsOfEmployee(person.employeeId())).containsExactly(person.userId());
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 106、109）、第 1 条第 3、5 项边界：停薪留职结束命令把此人带回非留职：结束日起版本为假，此前仍为真，版本前后衔接；会话与账号从头到尾不受影响")
    void endingTheLeaveRestoresTheFlagFromTheEndDay() throws Exception {
        Staff person = account();
        startLeave(person);
        LocalDate end = TODAY.minusDays(1);
        actIn(hr);

        UUID ended = enter(person.employeeId(), UNPAID_LEAVE_END, end);

        assertThat(commandStatus(ended)).isEqualTo(EFFECTIVE);
        assertThat(onUnpaidLeaveOn(person.employeeId(), end.minusDays(1))).isTrue();
        assertThat(onUnpaidLeaveOn(person.employeeId(), end)).isFalse();
        assertThat(onUnpaidLeaveOn(person.employeeId(), TODAY)).isFalse();
        assertContiguous(currentVersions(EMPLOYEE_VERSIONS, person.employeeId()));
        assertThat(profileStatus(person)).isEqualTo(200);
    }

    // ---- approval assignee resolution ----

    private DocumentReference startApproval(Staff initiator, Staff... managers) {
        DocumentReference document = new DocumentReference(new DocumentTypeCode("TIMESHEET"), newDocumentId());
        List<String> ids = java.util.Arrays.stream(managers).map(manager -> text(manager.employeeId())).toList();
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "acceptance.timesheet-submission")) {
            approvals.start(new ApprovalStartRequest(TimesheetApprovalProcess.KEY, document, new LegalEntityId(legalEntity), employeeIdOf(initiator.employeeId()),
                    new RoundNumber(1), UUID.randomUUID(), Set.of("projectHours"), List.of(new ApprovalField("projectManagerId", ids)),
                    new ContentHash("synthetic-content"), new ApprovalSummary("Synthetic timesheet " + document.documentId())));
        }
        return document;
    }

    /** How many of the document's waiting tasks the person can open: each selected manager gets a task of their own, and only a candidate sees one. */
    private long tasksOpenTo(DocumentReference document, Staff who) throws Exception {
        long visible = 0;
        for (String task : pendingTaskIdsOf(document)) {
            MockHttpServletResponse seen = mvc.perform(get(TASKS + task).with(session(who.session()))).andReturn().getResponse();
            assertThat(seen.getStatus()).as(seen.getContentAsString()).isIn(200, 404);
            if (seen.getStatus() == 200) {
                assertThat(JsonPath.<List<String>>read(seen.getContentAsString(), "$.candidates")).containsExactly(who.userId().toString());
                visible++;
            }
        }
        return visible;
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 107、110 的 AssigneeResolver）：审批办理人解析跳过停薪留职的人：表单选了 PM1、PM2，PM1 停薪留职前两人各得一个任务（共 2 个），停薪留职后只有 PM2 得到任务（共 1 个），PM1 打不开任何任务")
    void anApproverOnLeaveIsSkippedAsAssignee() throws Exception {
        maintainWorkCalendar(account("org_data_admin").session());
        Staff initiator = account();
        Staff pm1 = account();
        Staff pm2 = account();
        DocumentReference control = startApproval(initiator, pm1, pm2);
        assertThat(pendingTaskIdsOf(control)).as("before the leave").hasSize(2);
        assertThat(tasksOpenTo(control, pm1)).isEqualTo(1);
        assertThat(tasksOpenTo(control, pm2)).isEqualTo(1);
        startLeave(pm1);

        DocumentReference skipped = startApproval(initiator, pm1, pm2);

        assertThat(pendingTaskIdsOf(skipped)).as("PM1 is on leave: no task for PM1").hasSize(1);
        assertThat(tasksOpenTo(skipped, pm2)).isEqualTo(1);
        assertThat(tasksOpenTo(skipped, pm1)).as("the person on leave is no candidate").isZero();
    }

    // ---- sensitive role review ----

    /** The 「敏感角色复核」 entries (F-1 table of the identity schema, kind SENSITIVE_ROLE_REVIEW) about the account. */
    private List<Map<String, Object>> reviewsAbout(UUID userId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            assertThat(query(identity, "select 1 from information_schema.tables where table_schema = 'platform_identity' and table_name = 'permission_adjustments'"))
                    .as("F-1 gives the identity schema the table permission_adjustments (the 「待调整权限」 entries)").isNotEmpty();
            return query(identity, "select * from permission_adjustments where kind = ? and user_id = ?", SENSITIVE_ROLE_REVIEW, userId);
        }
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 108）、ADR-0009 敏感权限：持有审计查询与导出（安全审计员）或编号模板确认（业务管理员）权限的人停薪留职开始时各生成「敏感角色复核」；只有默认角色的员工不生成")
    void aHolderOfASensitiveRoleGetsAReviewWhenTheLeaveStarts() throws Exception {
        maintainWorkCalendar(account("org_data_admin").session()); // the review's escalation due fails loud on an unmaintained calendar
        Staff auditor = account(SECURITY_AUDITOR);
        Staff numbering = account("business_admin");
        Staff plain = account();
        administrator(); // the super_admin floor of #943 (ADR-0068 2.2/2.3) needs an available one before anybody goes on leave
        assertThat(reviewsAbout(auditor.userId())).as("no review before the leave").isEmpty();

        startLeave(auditor);
        startLeave(numbering);
        startLeave(plain);

        assertThat(reviewsAbout(auditor.userId())).as("audit read and export (ADR-0009)").isNotEmpty();
        assertThat(reviewsAbout(numbering.userId())).as("numbering rule confirmation (ADR-0009)").isNotEmpty();
        assertThat(reviewsAbout(plain.userId())).as("nothing sensitive to review").isEmpty();
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 108）、ADR-0032 第 3 项：持有双人逐人级角色（超级管理员是保留角色，授予要第二人）的人停薪留职开始时生成「敏感角色复核」")
    void aTwoPersonRoleHolderGetsAReviewWhenTheLeaveStarts() throws Exception {
        maintainWorkCalendar(account("org_data_admin").session()); // the review's escalation due fails loud on an unmaintained calendar
        Staff administrator = administrator();
        administrator(); // the super_admin floor of #943 (ADR-0068 2.2/2.3): another one stays available while this one is on leave

        startLeave(administrator);

        assertThat(reviewsAbout(administrator.userId())).isNotEmpty();
    }

    // ---- timesheet submission (user decision 2026-10-30, Q2 = 丙) ----

    /** The days of the timesheet cases (all within the days a timesheet may still be entered): leave from L, ending on E. */
    static final LocalDate BEFORE_LEAVE = TODAY.minusDays(6);
    static final LocalDate L = TODAY.minusDays(5);
    static final LocalDate DURING_LEAVE = TODAY.minusDays(4);
    static final LocalDate LAST_LEAVE_DAY = TODAY.minusDays(3);
    static final LocalDate E = TODAY.minusDays(2);

    private Staff timesheetProjectManager;

    /** A worker, a project manager, the calendar and the confirmed timesheet numbering rule. */
    private Staff workerForTimesheets() throws Exception {
        Staff maintainer = account("org_data_admin", "business_admin");
        maintainWorkCalendar(maintainer.session());
        confirmSeededNumberingRule("TIMESHEET", maintainer.session());
        timesheetProjectManager = account();
        return account();
    }

    private void leave(String kind, Staff who, LocalDate effective) throws Exception {
        actIn(hr);
        assertThat(commandStatus(enter(who.employeeId(), kind, effective))).isEqualTo(EFFECTIVE);
    }

    private void submit(Staff worker, LocalDate day) {
        actInSession(worker.session());
        TimesheetStatus status = submitTimesheet.execute(new SubmitTimesheetCommand(new TimesheetId(UUID.randomUUID()), day, Hours.of("8"),
                employeeIdOf(timesheetProjectManager.employeeId()))).status();
        assertThat(status).isEqualTo(TimesheetStatus.SUBMITTED);
    }

    private void assertRefused(Staff worker, LocalDate day) throws Exception {
        assertThatThrownBy(() -> submit(worker, day)).as("submission for " + day + " during the unpaid leave").isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(isPermissionRefusal(failure)).as("a refusal about the leave, not about rights: " + failure).isFalse());
        try (java.sql.Connection timesheets = schemaConnection("cost_timesheet")) {
            assertThat(query(timesheets, "select 1 from timesheets where employee_id = ? and work_date = ?", stored(worker.employeeId()), day))
                    .as("no timesheet row for " + day).isEmpty();
        }
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 110 的 SubmitTimesheet）、用户决定 2026-10-30 Q2=丙：停薪留职自 L 起：可为 L 之前的日期提交工时单；为 L 当天、休假期间的某天、最后一个休假日提交都被拒（不是权限拒绝），不留工时单行")
    void duringTheLeaveOnlyDaysBeforeItsStartCanBeSubmitted() throws Exception {
        Staff worker = workerForTimesheets();
        leave(UNPAID_LEAVE_START, worker, L);

        submit(worker, BEFORE_LEAVE);

        assertRefused(worker, L);
        assertRefused(worker, DURING_LEAVE);
        assertRefused(worker, LAST_LEAVE_DAY);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 3 条（行 110）、用户决定 2026-10-30 Q2=丙 边界：停薪留职自 L 起、E 日结束：结束日 E 起可再提交（E 当天可以），E 之前的最后一个休假日仍被拒")
    void afterTheLeaveEndsTimesheetsCanBeSubmittedAgain() throws Exception {
        Staff worker = workerForTimesheets();
        leave(UNPAID_LEAVE_START, worker, L);
        leave(UNPAID_LEAVE_END, worker, E);

        assertRefused(worker, LAST_LEAVE_DAY);
        submit(worker, E);
        submit(worker, E.plusDays(1));
        submit(worker, BEFORE_LEAVE);
    }
}
