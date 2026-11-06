package com.forcitis.one.acceptance.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1436 (a), user decision of 2026-11-05 22:05, point 1 (the decision fragment of the card comment), and the shape ruled by lead on 2026-11-06:
 * {@code GET /api/v1/organization/bonus-input-approvals/{taskId}} (the platform.approval task id) answers 200 with
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

    private static final String PATH = "/api/v1/organization/bonus-input-approvals/";
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
        UUID absent = UUID.randomUUID();
        MockHttpServletResponse nothing = read(absent, who);
        assertThat(nothing.getStatus()).as("the same caller, an id that does not exist").isEqualTo(404);
        assertThat(refusalBody(seen, taskId.toString())).as("%s: the refusal answers like an id that does not exist", as)
                .isEqualTo(refusalBody(nothing, absent.toString()));
    }

    /** The error body without the per-request correlation id and with the asked id masked, as the #730 A5 adapters compare refusals. */
    private static String refusalBody(MockHttpServletResponse answer, String id) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(JsonPath.parse(answer.getContentAsString()).<Map<String, Object>>read("$"));
        body.remove("correlationId");
        return body.toString().replace(id, "<id>");
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

        assertReads(task, lineManagerAccount, relationship, "the line manager (so that the 404s below are not a wrong path)");
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
    @DisplayName("#1436 (a) 同时有两份申请时，各候选人只能读自己任务上的那份：A 的第一阶段候选人读 B 的第二阶段任务得 404，反之亦然")
    void aCandidateOfOneTaskCannotReadAnotherOpenTask() throws Exception {
        UUID relationshipA = employedRelationship();
        UUID relationshipB = employedRelationship();
        Submitted a = submit(relationshipA, OCTOBER, AMOUNT, REASON);
        Submitted b = submit(relationshipB, OCTOBER, "99.00", "PROJECT");
        UUID taskA = nextTask(a, List.of());
        UUID firstOfB = decideFirstStage(b, "APPROVE");
        UUID taskB = nextTask(b, List.of(firstOfB));

        assertReads(taskA, lineManagerAccount, relationshipA, "the line manager on request A");
        assertNotFound(taskB, lineManagerAccount, "the line manager (candidate of A) on the open task of B");
        assertNotFound(taskA, secondSpecialistAccount, "the payroll-role holder (candidate of B) on the open task of A");
        MockHttpServletResponse own = read(taskB, secondSpecialistAccount);
        assertThat(own.getStatus()).as(own.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(own.getContentAsString(), "$.employmentRelationshipId")).as("B's own relationship, not A's").isEqualTo(relationshipB.toString());
        assertThat((String) JsonPath.read(own.getContentAsString(), "$.amount.amount")).isEqualTo("99.00");
    }

    @Test
    @DisplayName("#1436 (a) 经真实命令发起：审批任务详情（两个阶段）与企业微信卡片不含金额、原因键与任职关系编号，且至少发出一张给直属上级的卡片")
    void noNotificationOrTaskDetailOfARealRequestCarriesTheAmount() throws Exception {
        UUID relationship = employedRelationship();
        request(specialistAccount, requestOf(relationship, legalEntity.value(), OCTOBER, new java.math.BigDecimal(AMOUNT).toPlainString(), "CNY", REASON, UUID.randomUUID()));
        UUID documentId = (UUID) bonusApprovals().getLast().get("document_id");
        Submitted submitted = new Submitted(documentId, relationship, null, new DocumentReference(DOCUMENT_TYPE, documentIdOf(documentId)));
        UUID first = nextTask(submitted, List.of());
        String detailOfFirst = detail(first, lineManagerAccount).getContentAsString();
        claimAndDecide(first, lineManagerAccount, "APPROVE");
        UUID second = nextTask(submitted, List.of(first));
        String detailOfSecond = detail(second, secondSpecialistAccount).getContentAsString();

        for (String body : List.of(detailOfFirst, detailOfSecond)) {
            assertThat(body).as("the approval task detail").doesNotContain(AMOUNT).doesNotContain(REASON).doesNotContain(relationship.toString());
        }
        ArgumentCaptor<WecomCard> cards = ArgumentCaptor.forClass(WecomCard.class);
        verify(wecomGateway, atLeast(1)).send(cards.capture());
        for (WecomCard card : cards.getAllValues()) {
            assertThat(card.title() + " " + card.description() + " " + card.targetUrl()).as("a WeCom card of the flow").doesNotContain(AMOUNT).doesNotContain(REASON)
                    .doesNotContain(relationship.toString());
        }
    }

    @Test
    @DisplayName("#1436 (a) 管理员模式下的 super_admin 与持 bonus-input:read（范围 ALL）的人读这个接口也得 404（他们自己的薪资读取是别的接口）")
    void anAdministratorAndAHolderOfTheReadPermissionAreNotCandidatesEither() throws Exception {
        UUID relationship = employedRelationship();
        Submitted submitted = submit(relationship, OCTOBER, AMOUNT, REASON);
        UUID task = nextTask(submitted, List.of());

        assertReads(task, lineManagerAccount, relationship, "the line manager (so that the 404s below are not a wrong path)");
        assertNotFound(task, newAdministrator(), "a super_admin in the administrator mode");
        assertNotFound(task, holderOfReadWithScope("ALL"), "a holder of organization.payroll:bonus-input:read with scope ALL");
        UUID first = decideFirstStage(submitted, "APPROVE");
        UUID second = nextTask(submitted, List.of(first));
        assertNotFound(second, newAdministrator(), "a super_admin in the administrator mode, second stage task");
        assertNotFound(second, holderOfReadWithScope("ALL"), "a holder of the read permission with scope ALL, second stage task");
    }

    @Test
    @DisplayName("#1436 (a) 第二阶段有两位非发起人的薪资专员：认领前两人都读到 200；一人认领后，认领人 200，另一位不再是当前候选人而得 404")
    void afterOneOfTwoCandidatesClaimsTheOtherCannotReadAnyMore() throws Exception {
        UUID thirdSpecialist = newEmployeeId();
        employee(thirdSpecialist, unit, null);
        grantByOperations(thirdSpecialist, PAYROLL_ROLE);
        SignedIn thirdAccount = signIn(thirdSpecialist);
        UUID relationship = employedRelationship();
        Submitted submitted = submit(relationship, OCTOBER, AMOUNT, REASON);
        UUID first = decideFirstStage(submitted, "APPROVE");
        UUID second = nextTask(submitted, List.of(first));

        assertReads(second, secondSpecialistAccount, relationship, "the first payroll-role holder, before any claim");
        assertReads(second, thirdAccount, relationship, "the second payroll-role holder, before any claim");
        actOk(second, secondSpecialistAccount, "claim", null);

        assertReads(second, secondSpecialistAccount, relationship, "the claimant (assignee)");
        assertNotFound(second, thirdAccount, "the other holder, no longer a current candidate");
    }
}
