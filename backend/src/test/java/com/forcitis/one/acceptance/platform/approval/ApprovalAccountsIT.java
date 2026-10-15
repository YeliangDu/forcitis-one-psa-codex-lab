package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P2 (ADR-0033 section 3 item 3, 审批): the initiator and the people the form names are employees; only when the engine is given a
 * candidate or an assignee does the employee become an account. Self-approval is excluded by comparing employees before that step; an
 * employee who has no account cannot be assigned and the start fails with an alert instead of skipping them; acting on a task is allowed
 * to its candidates and holder only, compared as accounts (I-7). The account ids are UUIDs and the employee ids numbers.
 * The approval is started through the approval api as the timesheet module does after a submission (no HTTP start, I-5).
 */
@Testcontainers
class ApprovalAccountsIT extends AccountsAcceptanceSupport {

    static final String TASKS = "/api/v1/platform/approval-tasks/";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    @Autowired
    ApprovalProcesses approvals;

    LegalEntityId legalEntity;
    long unit;
    long supervisor;
    long initiator;
    long projectManager;
    SignedIn supervisorAccount;
    SignedIn initiatorAccount;
    SignedIn projectManagerAccount;

    @BeforeEach
    void anInitiatorTheirSupervisorAndAProjectManager() throws Exception {
        maintainWorkCalendar();
        legalEntity = new LegalEntityId(createLegalEntity(newLegalEntityCode("AP"), "Synthetic approval accounts"));
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
        supervisor = newEmployeeId();
        initiator = newEmployeeId();
        projectManager = newEmployeeId();
        employee(supervisor, unit, null);
        employee(initiator, unit, supervisor);
        employee(projectManager, unit, null);
        supervisorAccount = signIn(supervisor);
        initiatorAccount = signIn(initiator);
        projectManagerAccount = signIn(projectManager);
    }

    private DocumentReference start(List<Long> projectManagers) {
        DocumentReference document = new DocumentReference(DocumentType.TIMESHEET, new DocumentId("acceptance-" + UUID.randomUUID()));
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "acceptance.timesheet-submission")) {
            approvals.start(new ApprovalStartRequest(ProcessKey.COST_TIMESHEET_APPROVAL, document, legalEntity, new EmployeeId(initiator), new RoundNumber(1),
                    UUID.randomUUID(), Set.of("projectHours"), List.of(new ApprovalField("projectManagerId", projectManagers.stream().map(String::valueOf).toList())),
                    new ContentHash("synthetic-content"), new ApprovalSummary("Synthetic timesheet " + document.documentId().value())));
        }
        return document;
    }

    private List<String> openTasks(DocumentReference document) throws Exception {
        List<String> tasks = new ArrayList<>();
        try (Connection approval = schemaConnection("platform_approval");
             PreparedStatement select = approval.prepareStatement("select task_id from approval_tasks where document_key = ? and status = 'PENDING'")) {
            select.setString(1, document.documentId().value());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    tasks.add(rs.getString(1));
                }
            }
        }
        return tasks;
    }

    private MockHttpServletResponse detail(String taskId, UUID caller) throws Exception {
        return mvc.perform(get(TASKS + taskId).with(as(caller))).andReturn().getResponse();
    }

    private List<String> candidatesSeenBy(String taskId, UUID caller) throws Exception {
        MockHttpServletResponse seen = detail(taskId, caller);
        assertThat(seen.getStatus()).as(seen.getContentAsString()).isEqualTo(200);
        return JsonPath.read(seen.getContentAsString(), "$.candidates");
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 3 条（审批自批排除）：发起人不得自批——在转为用户 ID 之前按员工 ID 排除发起人；只剩发起人时升到其直属领导；候选人是用户 ID")
    void theInitiatorNeverApprovesTheirOwnTimesheet() throws Exception {
        DocumentReference withAnother = start(List.of(initiator, projectManager));

        List<String> tasks = openTasks(withAnother);
        assertThat(tasks).hasSize(1);
        assertThat(candidatesSeenBy(tasks.getFirst(), projectManagerAccount.userId())).containsExactly(projectManagerAccount.userId().toString());
        assertThat(detail(tasks.getFirst(), supervisorAccount.userId()).getStatus()).as("someone who is no candidate").isEqualTo(404);

        DocumentReference onlyTheInitiator = start(List.of(initiator));

        List<String> escalated = openTasks(onlyTheInitiator);
        assertThat(escalated).hasSize(1);
        assertThat(candidatesSeenBy(escalated.getFirst(), supervisorAccount.userId())).containsExactly(supervisorAccount.userId().toString());
        assertThat(detail(escalated.getFirst(), projectManagerAccount.userId()).getStatus()).as("someone who is no candidate").isEqualTo(404);
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 3 条（审批选人）：表单选中的员工没有账号时指派失败并告警，不静默跳过：发起失败、不留下审批；有账号的员工照常指派")
    void anEmployeeWithoutAnAccountCannotBeAssigned() throws Exception {
        long withoutAccount = newEmployeeId();
        employee(withoutAccount, unit, null);
        assertThat(accountsOfEmployee(withoutAccount)).isEmpty();
        int mark = logMark();

        List<DocumentReference> started = new ArrayList<>();
        assertThatThrownBy(() -> started.add(start(List.of(withoutAccount)))).as("starting an approval whose approver has no account").isNotNull();

        assertThat(alertsSince(mark)).as("alerts raised for the approver without an account").isNotEmpty();
        assertThat(started).isEmpty();

        DocumentReference control = start(List.of(projectManager));
        assertThat(openTasks(control)).hasSize(1);
        assertThat(candidatesSeenBy(openTasks(control).getFirst(), projectManagerAccount.userId())).containsExactly(projectManagerAccount.userId().toString());
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 3 条 / I-7（TaskGuard）：审批办理只认处理人，按用户 ID 比较：发起人办理答 404，与此单无关的人读也答 404；候选人可以办理，办理记录的操作人是其用户 ID")
    void onlyTheCandidateActsOnTheTask() throws Exception {
        String task = openTasks(start(List.of(projectManager))).getFirst();
        MockHttpServletResponse seen = detail(task, projectManagerAccount.userId());
        assertThat(seen.getStatus()).as(seen.getContentAsString()).isEqualTo(200);

        MockHttpServletResponse byTheInitiator = mvc.perform(post(TASKS + task + ":decide").header("If-Match", seen.getHeader("ETag")).with(csrf()).with(as(initiatorAccount.userId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}")).andReturn().getResponse();

        assertThat(byTheInitiator.getStatus()).as(byTheInitiator.getContentAsString()).isEqualTo(404);
        assertThat((String) JsonPath.read(byTheInitiator.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_APPROVAL_TASK_NOT_FOUND");

        assertThat(detail(task, supervisorAccount.userId()).getStatus()).as("someone who is neither candidate nor initiator").isEqualTo(404);
        MockHttpServletResponse decided = mvc.perform(post(TASKS + task + ":decide").header("If-Match", seen.getHeader("ETag")).with(csrf())
                .with(as(projectManagerAccount.userId())).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}"))
                .andReturn().getResponse();

        assertThat(decided.getStatus()).as(decided.getContentAsString()).isEqualTo(200);
        MockHttpServletResponse handled = mvc.perform(get("/api/v1/platform/approval-task-records").with(as(projectManagerAccount.userId()))).andReturn().getResponse();
        assertThat(handled.getStatus()).as(handled.getContentAsString()).isEqualTo(200);
        List<String> actors = JsonPath.read(handled.getContentAsString(), "$.items[?(@.taskId == '" + task + "')].actorId");
        assertThat(actors).isNotEmpty().containsOnly(projectManagerAccount.userId().toString());
    }
}
