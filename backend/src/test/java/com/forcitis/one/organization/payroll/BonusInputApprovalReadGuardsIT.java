package com.forcitis.one.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoundNumber;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1436 (a), the implementer's own tests of two guards the acceptance test does not reach. The approver read answers only for tasks of the bonus input process:
 * a candidate of an open task of another process (a timesheet's project manager) who asks for that task's id is told the same as for an unknown task. And it
 * answers only an ordinary session: a candidate who is also a super_admin and has entered the administrator mode gets 404, not the data (I-11).
 */
class BonusInputApprovalReadGuardsIT extends P5Support {

    static final TestDatabase postgres = TestPostgres.database(BonusInputApprovalReadGuardsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final String PATH = "/api/v1/organization/bonus-input-approvals/";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#1436 (a) 奖金输入审批人读取只认奖金输入流程的任务：别的流程（工时）任务的候选人拿该任务 id 来读，得到与未知任务相同的 404")
    void aCandidateOfATaskOfAnotherProcessGetsNotFound() throws Exception {
        UUID manager = newEmployeeId();
        employee(manager, unit, null);
        SignedIn managerAccount = signIn(manager);
        DocumentReference document = startProjectHours(manager);
        List<UUID> open = openTaskIds(document);
        assertThat(open).as("the project manager holds an open task of the timesheet process").hasSize(1);

        MockHttpServletResponse response = mvc.perform(get(PATH + open.getFirst()).with(session(managerAccount))).andReturn()
                .getResponse();

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(404);
    }

    @Test
    @DisplayName("#1436 (a) 管理员模式不处理审批（I-11）：既是一级任务候选人又是 super_admin 的人进入管理员模式后读这个接口得 404，退出管理员模式的普通会话得 200")
    void aCandidateInTheAdministratorModeGetsNotFound() throws Exception {
        UUID lineManager = newEmployeeId();
        employee(lineManager, unit, null);
        grantByOperations(lineManager, "super_admin");
        UUID initiator = newEmployeeId();
        employee(initiator, unit, lineManager);
        SignedIn initiatorAccount = signIn(initiator);
        SignedIn ordinary = signIn(lineManager);
        SignedIn administratorMode = administratorMode(signIn(lineManager));
        UUID requestId = UUID.randomUUID();
        DocumentReference document = new DocumentReference(new DocumentTypeCode("BONUS_INPUT"), documentIdOf(requestId));
        List<ApprovalField> fields = List.of(new ApprovalField("employmentRelationshipId", List.of(UUID.randomUUID().toString())),
                new ApprovalField("payrollMonthStartDate", List.of("2026-10-01")), new ApprovalField("bonusAmount", List.of("100.00")),
                new ApprovalField("bonusCurrency", List.of("CNY")), new ApprovalField("reasonKey", List.of("YEAR_END")));
        actInSession(initiatorAccount);
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "test.bonus-input-read-guards")) {
            approvals.start(new ApprovalStartRequest(ProcessKey.of("organization_payroll_bonus_input"), document, legalEntity, employeeIdOf(initiator), new RoundNumber(1),
                    UUID.randomUUID(), Set.of(), fields, new ContentHash("synthetic-bonus-" + requestId), new ApprovalSummary("Synthetic bonus input " + requestId)));
        }
        UUID task = openTaskIds(document).getFirst();

        MockHttpServletResponse inAdministratorMode = mvc.perform(get(PATH + task).with(session(administratorMode))).andReturn().getResponse();
        MockHttpServletResponse inOrdinarySession = mvc.perform(get(PATH + task).with(session(ordinary))).andReturn().getResponse();

        assertThat(inAdministratorMode.getStatus()).as(inAdministratorMode.getContentAsString()).isEqualTo(404);
        assertThat(inAdministratorMode.getContentAsString()).doesNotContain("YEAR_END").doesNotContain("100.00");
        assertThat(inOrdinarySession.getStatus()).as(inOrdinarySession.getContentAsString()).isEqualTo(200);
    }
}
