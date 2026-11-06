package com.forcitis.one.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1436, the implementer's own test (not an acceptance test of the card): the payroll stage takes no fallback. User decision 「不转交，挂起并告警」
 * (fragment PR #1513): when no holder of the payroll role other than the initiator is available at the second stage, the first-stage approval is refused and
 * rolled back, the request stays pending, and nothing is escalated to the initiator's supervisor or to an administrator. The approval is started through the
 * approval api, not through the payroll request command: the stage routing is what is under test. Its own database (class), because the holders of the role are
 * counted over the whole database.
 */
class BonusInputNoFallbackIT extends P5Support {

    static final TestDatabase postgres = TestPostgres.database(BonusInputNoFallbackIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final String PAYROLL_ROLE = "payroll_specialist";
    private static final ProcessKey PROCESS = ProcessKey.of("organization_payroll_bonus_input");
    private static final String APPROVE = "{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#1436 薪资专员阶段不转交：第二阶段没有发起人以外的在职持有人时，一级审批被拒绝并回滚（一级任务仍开着、没有二级任务），有了持有人后同一审批人再批即进入第二阶段")
    void withNoOtherHolderAtTheSecondStageTheFirstApprovalIsRefusedAndRolledBack() throws Exception {
        UUID lineManager = person(null);
        SignedIn lineManagerAccount = signIn(lineManager);
        UUID initiator = person(lineManager);
        grantByOperations(initiator, PAYROLL_ROLE);
        SignedIn initiatorAccount = signIn(initiator);
        UUID secondHolder = person(null);
        grantByOperations(secondHolder, PAYROLL_ROLE);
        UUID newHolder = person(null);
        DocumentReference document = start(initiatorAccount, initiator);

        UUID first = awaitTask(document, List.of());
        actOk(first, lineManagerAccount, "claim", null);
        separate(secondHolder);

        MockHttpServletResponse refused = act(first, lineManagerAccount, "decide", APPROVE);

        assertThat(refused.getStatus()).as(refused.getContentAsString()).isNotIn(200, 204);
        assertThat(refused.getContentAsString()).contains("NO_ASSIGNEE_FOUND");
        assertThat(openTaskIds(document)).as("the approval was rolled back: the first task is still the only open one").containsExactly(first);

        grantByOperations(newHolder, PAYROLL_ROLE);
        actOk(first, lineManagerAccount, "decide", APPROVE);
        UUID second = awaitTask(document, List.of(first));
        assertThat(act(second, initiatorAccount, "claim", null).getStatus()).as("the initiator is not a candidate of the second stage").isNotIn(200, 204);
        actOk(second, signIn(newHolder), "claim", null);
    }

    @Test
    @DisplayName("#1436 薪资专员阶段只有规则点名的人能接手：持有人把任务转给无薪资专员角色的人被拒绝，转给另一位持有人可以；流程管理员改派给自己或外人被拒绝")
    void theSecondStageGoesOnlyToHoldersOfThePayrollRole() throws Exception {
        UUID lineManager = person(null);
        SignedIn lineManagerAccount = signIn(lineManager);
        UUID initiator = person(lineManager);
        grantByOperations(initiator, PAYROLL_ROLE);
        SignedIn initiatorAccount = signIn(initiator);
        UUID holder = person(null);
        grantByOperations(holder, PAYROLL_ROLE);
        UUID peer = person(null);
        grantByOperations(peer, PAYROLL_ROLE);
        UUID outsider = person(null);
        UUID administrator = person(null);
        grantByOperations(administrator, "process_administrator");
        SignedIn holderAccount = signIn(holder);
        SignedIn outsiderAccount = signIn(outsider);
        SignedIn administratorAccount = signIn(administrator);
        DocumentReference document = start(initiatorAccount, initiator);
        UUID first = awaitTask(document, List.of());
        actOk(first, lineManagerAccount, "claim", null);
        actOk(first, lineManagerAccount, "decide", APPROVE);
        UUID second = awaitTask(document, List.of(first));
        actOk(second, holderAccount, "claim", null);

        MockHttpServletResponse toOutsider = act(second, holderAccount, "transfer", handover(outsider));
        MockHttpServletResponse adminToSelf = actAs(second, holderAccount, administratorAccount, "reassign", handover(administrator));
        MockHttpServletResponse adminToOutsider = actAs(second, holderAccount, administratorAccount, "reassign", handover(outsider));

        assertThat(toOutsider.getStatus()).as(toOutsider.getContentAsString()).isNotIn(200, 204);
        assertThat(toOutsider.getContentAsString()).contains("PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID");
        assertThat(adminToSelf.getStatus()).as(adminToSelf.getContentAsString()).isNotIn(200, 204);
        assertThat(adminToOutsider.getStatus()).as(adminToOutsider.getContentAsString()).isNotIn(200, 204);
        assertThat(actAs(second, holderAccount, outsiderAccount, "decide", APPROVE).getStatus()).as("nobody outside the role decides the stage").isNotIn(200, 204);
        assertThat(actAs(second, holderAccount, administratorAccount, "decide", APPROVE).getStatus()).as("the administrator does not decide the stage").isNotIn(200, 204);
        actOk(second, holderAccount, "transfer", handover(peer));
        actOk(second, signIn(peer), "decide", APPROVE);
    }

    /** A write by a caller who cannot read the task's detail (an outsider, the administrator): the version comes from the person who holds it. */
    private MockHttpServletResponse actAs(UUID task, SignedIn holder, SignedIn caller, String operation, String body) throws Exception {
        return mvc.perform(post(TASKS + task + ":" + operation).header("If-Match", versionOf(task, holder)).with(csrf()).with(session(caller))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private static String handover(UUID employee) {
        return "{\"assigneeId\":\"" + employee + "\",\"reason\":\"synthetic\"}";
    }

    private UUID person(UUID reportsTo) throws Exception {
        UUID id = newEmployeeId();
        employee(id, unit, reportsTo);
        return id;
    }

    private DocumentReference start(SignedIn initiatorAccount, UUID initiator) {
        UUID requestId = UUID.randomUUID();
        DocumentReference document = new DocumentReference(new DocumentTypeCode("BONUS_INPUT"), documentIdOf(requestId));
        List<ApprovalField> fields = List.of(new ApprovalField("employmentRelationshipId", List.of(text(UUID.randomUUID()))),
                new ApprovalField("payrollMonthStartDate", List.of("2026-10-01")), new ApprovalField("bonusAmount", List.of("100.00")),
                new ApprovalField("bonusCurrency", List.of("CNY")), new ApprovalField("reasonKey", List.of("YEAR_END")));
        actInSession(initiatorAccount);
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "test.bonus-input-no-fallback")) {
            approvals.start(new ApprovalStartRequest(PROCESS, document, legalEntity, employeeIdOf(initiator), new RoundNumber(1), UUID.randomUUID(), Set.of(), fields,
                    new ContentHash("synthetic-bonus-" + requestId), new ApprovalSummary("Synthetic bonus input " + requestId)));
        }
        return document;
    }

    private UUID awaitTask(DocumentReference document, List<UUID> seen) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> openTaskIds(document).stream().anyMatch(task -> !seen.contains(task)));
        try {
            return openTaskIds(document).stream().filter(task -> !seen.contains(task)).findFirst().orElseThrow();
        } catch (java.sql.SQLException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private void separate(UUID employee) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement update = workforce.prepareStatement(
                "update employees set employment_status = 'SEPARATED' where id = ?")) {
            update.setObject(1, stored(employee));
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
    }
}
