package com.forcitis.one.acceptance.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1436 (a), user decision of 2026-11-05 22:05, point 1 (the decision fragment of the card comment), and the shape ruled by lead on 2026-11-06:
 * {@code GET /api/v1/organization/payroll/bonus-input-approvals/{taskId}} (the platform.approval task id) answers 200 with
 * {@code {employmentRelationshipId, payrollMonthStartDate, amount{amount,currency}, reasonKey}} only to the task's current open candidates or its assignee, with
 * no payroll permission needed, and 404 to everyone else (initiator, people not on the task, people of another stage, anyone once the task is decided or closed,
 * an unknown id; fail closed, security-invariants I-10); a call without a session stays the platform's 401. Notifications, the WeCom card and the task
 * detail carry no amount. The approvals are driven through the process of the record (one stage line manager, then a payroll-role holder who is not the
 * initiator), the same as the other bonus-input classes.
 */
class BonusInputApproverReadIT extends BonusInputSupport {

    static final TestDatabase postgres = TestPostgres.database(BonusInputApproverReadIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String PATH = "/api/v1/organization/payroll/bonus-input-approvals/";
    private static final String AMOUNT = "1234.50";
    private static final String REASON = "YEAR_END";

    private MockHttpServletResponse read(Object taskId, SignedIn who) throws Exception {
        return mvc.perform(get(PATH + taskId).with(session(who))).andReturn().getResponse();
    }

    private void assertReads(Object taskId, SignedIn who, UUID relationship, String as) throws Exception {
        MockHttpServletResponse seen = read(taskId, who);
        assertThat(seen.getStatus()).as("%s reads the open task: %s", as, seen.getContentAsString()).isEqualTo(200);
        String body = seen.getContentAsString();
        assertThat((String) JsonPath.read(body, "$.employmentRelationshipId")).as("%s: relationship", as).isEqualTo(relationship.toString());
        assertThat((String) JsonPath.read(body, "$.payrollMonthStartDate")).as("%s: month", as).isEqualTo(OCTOBER.toString());
        assertThat((String) JsonPath.read(body, "$.amount.amount")).as("%s: amount", as).isEqualTo(AMOUNT);
        assertThat((String) JsonPath.read(body, "$.amount.currency")).as("%s: currency", as).isEqualTo("CNY");
        assertThat((String) JsonPath.read(body, "$.reasonKey")).as("%s: reason", as).isEqualTo(REASON);
    }

    private void assertNotFound(Object taskId, SignedIn who, String as) throws Exception {
        MockHttpServletResponse seen = read(taskId, who);
        assertThat(seen.getStatus()).as("%s must not see the task (404, not 403): %s", as, seen.getContentAsString()).isEqualTo(404);
        assertThat(seen.getContentAsString()).as("%s: the refusal names no amount", as).doesNotContain(AMOUNT);
    }

    @Test
    @DisplayName("#1436 (a) 第一阶段候选人（直属上级，不持薪资专员角色）读到谁的奖金、月份、金额与原因；不需要薪资权限")
    void theLineManagerCandidateReadsTheBonusWithoutAPayrollPermission() throws Exception {
        UUID relationship = employedRelationship();
        Submitted submitted = submit(relationship, OCTOBER, AMOUNT, REASON);
        UUID task = nextTask(submitted, List.of());

        assertReads(task, lineManagerAccount, relationship, "the line manager (candidate)");
    }

    @Test
    @DisplayName("#1436 (a) 任务办理人（认领后）仍可读")
    void theAssigneeReadsAfterClaiming() throws Exception {
        UUID relationship = employedRelationship();
        Submitted submitted = submit(relationship, OCTOBER, AMOUNT, REASON);
        UUID task = nextTask(submitted, List.of());
        actOk(task, lineManagerAccount, "claim", null);

        assertReads(task, lineManagerAccount, relationship, "the assignee");
    }

    @Test
    @DisplayName("#1436 (a) 不在任务上的人一律 404：发起人、无关人员、第二阶段才有任务的薪资专员、未知任务；无会话保持 401")
    void everyoneElseGets404AndAnonymousGets401() throws Exception {
        UUID relationship = employedRelationship();
        Submitted submitted = submit(relationship, OCTOBER, AMOUNT, REASON);
        UUID task = nextTask(submitted, List.of());

        assertNotFound(task, specialistAccount, "the initiator (a payroll-role holder)");
        assertNotFound(task, outsiderAccount, "an unrelated employee");
        assertNotFound(task, secondSpecialistAccount, "the payroll-role holder of the second stage, before the stage is reached");
        assertNotFound(UUID.randomUUID(), lineManagerAccount, "the line manager, unknown task id");
        assertThat(mvc.perform(get(PATH + task)).andReturn().getResponse().getStatus()).as("no session").isEqualTo(401);
    }

    @Test
    @DisplayName("#1436 (a) 第一阶段决定后：旧任务对直属上级 404；第二阶段任务只对新候选人 200，发起人与上一阶段的人 404；终审决定后全部 404")
    void theReadFollowsTheCurrentTaskThroughTheStagesAndClosesWithTheDecision() throws Exception {
        UUID relationship = employedRelationship();
        Submitted submitted = submit(relationship, OCTOBER, AMOUNT, REASON);
        UUID first = decideFirstStage(submitted, "APPROVE");

        assertNotFound(first, lineManagerAccount, "the line manager, after deciding the first stage");
        UUID second = nextTask(submitted, List.of(first));
        assertReads(second, secondSpecialistAccount, relationship, "the payroll-role holder (second stage candidate)");
        assertNotFound(second, lineManagerAccount, "the line manager, second stage task");
        assertNotFound(second, specialistAccount, "the initiator, second stage task");
        assertNotFound(second, outsiderAccount, "an unrelated employee, second stage task");

        claimAndDecide(second, secondSpecialistAccount, "APPROVE");
        assertNotFound(second, secondSpecialistAccount, "the deciding approver, after the final decision");
        assertNotFound(first, lineManagerAccount, "the line manager, after the final decision");
    }

    @Test
    @DisplayName("#1436 (a) 申请在第一阶段被驳回关闭后，直属上级再读得 404")
    void aRejectedRequestIsNoLongerReadable() throws Exception {
        UUID relationship = employedRelationship();
        Submitted submitted = submit(relationship, OCTOBER, AMOUNT, REASON);
        UUID first = decideFirstStage(submitted, "REJECT");

        assertNotFound(first, lineManagerAccount, "the line manager, after rejecting");
    }

    @Test
    @DisplayName("#1436 (a) 通知、企业微信卡片与审批任务详情不含金额与原因（金额只经薪资专属读取）")
    void noNotificationOrTaskDetailCarriesTheAmount() throws Exception {
        UUID relationship = employedRelationship();
        Submitted submitted = submit(relationship, OCTOBER, AMOUNT, REASON);
        UUID first = nextTask(submitted, List.of());
        String detailOfFirst = detail(first, lineManagerAccount).getContentAsString();
        decide(submitted, "APPROVE");
        awaitConsumed(submitted.requestId());

        assertThat(detailOfFirst).as("the approval task detail").doesNotContain(AMOUNT).doesNotContain(REASON);
        ArgumentCaptor<WecomCard> cards = ArgumentCaptor.forClass(WecomCard.class);
        verify(wecomGateway, atLeast(0)).send(cards.capture());
        for (WecomCard card : cards.getAllValues()) {
            assertThat(card.title() + " " + card.description() + " " + card.targetUrl()).as("a WeCom card of the flow").doesNotContain(AMOUNT).doesNotContain(REASON);
        }
    }
}
