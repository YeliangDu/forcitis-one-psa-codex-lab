package com.forcitis.one.acceptance.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1436 acceptance part (PY3 bonus input port), behaviour lines from the signed PY3 record (section "Approval seam, process and permissions") and the user's
 * routing decision of 2026-11-05 (docs/decisions/2026-11-05-195500-one-lead-均采纳推荐.md, item 1): an approval that passed both stages (the initiator's line
 * manager, then a payroll-role holder who is not the initiator) writes exactly one ACTIVE row with its own id, found by approval_instance_id; a rejected one
 * writes none; a redelivered result is a no-op; the same reason twice for a relationship and month is refused while another reason or month is not; the void is
 * one command of a payroll-role holder, once; the reads need the read permission; an unknown id is not found; only the stage's candidates see its task and the
 * initiator is no candidate of the second stage. Codes are the module's PayrollError names. Owed, not tested here: the void after an APPROVED settlement (PY4
 * is not built), the entity scope of reads, the RequestBonusInput command (its submit permission and idempotent retry wait for its Java signature).
 */
class BonusInputPortIT extends BonusInputSupport {

    static final TestDatabase postgres = TestPostgres.database(BonusInputPortIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String DUPLICATE = "ORGANIZATION_PAYROLL_BONUS_INPUT_DUPLICATE_REASON";
    private static final String ALREADY_VOIDED = "ORGANIZATION_PAYROLL_BONUS_INPUT_ALREADY_VOIDED";
    private static final String NOT_FOUND = "ORGANIZATION_PAYROLL_BONUS_INPUT_NOT_FOUND";

    @Test
    @DisplayName("#1436 PY3：两级审批通过后写出一行有效奖金输入，值取自表单，录入人是发起人的员工 id，行有自己的 id（不是单据 id）")
    void anApprovedApprovalWritesOneActiveRow() throws Exception {
        UUID relationship = UUID.randomUUID();
        Submitted submitted = approved(relationship, OCTOBER, "1500.50", "YEAR_END");

        UUID id = rowId(submitted);
        assertThat(id).as("PY3 record: the document id is payroll's request id and is not a table key").isNotEqualTo(submitted.requestId());
        Object bonus = summary(specialistAccount, id);
        assertThat(field(bonus, "id")).isEqualTo(id);
        assertThat(field(bonus, "employmentRelationshipId")).as("the relationship of the form").isEqualTo(relationship);
        assertThat(field(bonus, "payrollMonthStartDate")).isEqualTo(OCTOBER);
        assertThat(field(bonus, "payingLegalEntityId")).as("the legal entity of the approval start request").isEqualTo(legalEntity.value());
        assertThat((BigDecimal) field(bonus, "bonusAmount")).isEqualByComparingTo("1500.50");
        assertThat(field(bonus, "bonusCurrency")).isEqualTo("CNY");
        assertThat(field(bonus, "reasonKey")).isEqualTo("YEAR_END");
        assertThat(String.valueOf(field(bonus, "status"))).isEqualTo("ACTIVE");
        assertThat(field(bonus, "enteredByEmployeeId")).as("PY3 record: the initiator of the approval (an employee id)").isEqualTo(specialist);
        assertThat(field(bonus, "voidedByUserId")).isNull();
    }

    @Test
    @DisplayName("#1436 PY3：第一级（直属上级）驳回的审批不写任何行，也不会出现第二级任务")
    void aRejectedApprovalWritesNothing() throws Exception {
        Submitted submitted = submit(UUID.randomUUID(), OCTOBER, "100.00", "YEAR_END");
        UUID first = decideFirstStage(submitted, "REJECT");
        awaitConsumed(submitted.requestId());

        assertThat(rowsOf(submitted)).as("PY3 record: a row is always an approved input, there is no pending state").isEmpty();
        assertThat(openTaskIds(submitted.document())).as("no second stage after a rejection").doesNotContain(first).isEmpty();
    }

    @Test
    @DisplayName("#1436 PY3：同一审批的结果事件重放后仍只有一行，行不变（按审批实例幂等）")
    void aReplayedResultIsANoOp() throws Exception {
        Submitted submitted = approved(UUID.randomUUID(), OCTOBER, "200.00", "YEAR_END");

        replay(submitted, ApprovalOutcome.APPROVED);

        assertThat(rowsOf(submitted)).as("PY3 record: uk on approval_instance_id, a replay finds one row").hasSize(1);
    }

    @Test
    @DisplayName("#1436 PY3：同一关系、月份、原因的第二次有效输入不写行（原有行不变），错误码 " + "ORGANIZATION_PAYROLL_BONUS_INPUT_DUPLICATE_REASON 存在")
    void theSameReasonTwiceIsRefused() throws Exception {
        UUID relationship = UUID.randomUUID();
        Submitted first = approved(relationship, OCTOBER, "300.00", "YEAR_END");
        Submitted second = approved(relationship, OCTOBER, "999.00", "YEAR_END");

        assertThat(rowsOf(second)).as("PY3 record: the second approved input for the same key leaves no row").isEmpty();
        assertThat(activeRowsOf(relationship, OCTOBER, "YEAR_END")).isEqualTo(1L);
        assertThat((BigDecimal) field(summary(specialistAccount, rowId(first)), "bonusAmount")).as("the first input is unchanged").isEqualByComparingTo("300.00");
        assertThat(java.util.Arrays.stream(type(API + "PayrollError").getEnumConstants()).map(Object::toString)).contains(DUPLICATE);
    }

    @Test
    @DisplayName("#1436 PY3：同月不同原因、同原因不同月、不同关系的输入可以并存（记录的业务键）")
    void otherReasonMonthOrRelationshipIsAnotherInput() throws Exception {
        UUID relationship = UUID.randomUUID();
        Submitted yearEnd = approved(relationship, OCTOBER, "100.00", "YEAR_END");
        Submitted project = approved(relationship, OCTOBER, "200.00", "PROJECT");
        Submitted nextMonth = approved(relationship, OCTOBER.plusMonths(1), "300.00", "YEAR_END");
        Submitted otherPerson = approved(UUID.randomUUID(), OCTOBER, "400.00", "YEAR_END");

        for (Submitted each : List.of(yearEnd, project, nextMonth, otherPerson)) {
            assertThat(rowsOf(each)).as("PY3 record: one row each").hasSize(1);
        }
    }

    @Test
    @DisplayName("#1436 PY3：作废把有效行变为已作废并记下作废人；第二次作废被拒绝（ORGANIZATION_PAYROLL_BONUS_INPUT_ALREADY_VOIDED）")
    void aVoidIsOnceAndRecordsWhoDidIt() throws Exception {
        UUID id = rowId(approved(UUID.randomUUID(), OCTOBER, "500.00", "YEAR_END"));

        voidInput(specialistAccount, id);

        Object bonus = summary(specialistAccount, id);
        assertThat(String.valueOf(field(bonus, "status"))).isEqualTo("VOIDED");
        assertThat(field(bonus, "voidedByUserId")).as("PY3 record: the void pair").isEqualTo(specialistAccount.userId());
        assertRefusedWith(() -> voidInput(specialistAccount, id), ALREADY_VOIDED);
        assertThat(String.valueOf(field(summary(specialistAccount, id), "status"))).as("the refused second void changed nothing").isEqualTo("VOIDED");
    }

    @Test
    @DisplayName("#1436 PY3：作废后同一键可补录更正的输入，作废的行保留（记录：作废释放业务键）")
    void aVoidFreesTheKeyForACorrectedInput() throws Exception {
        UUID relationship = UUID.randomUUID();
        Submitted wrong = approved(relationship, OCTOBER, "500.00", "YEAR_END");
        voidInput(specialistAccount, rowId(wrong));

        Submitted corrected = approved(relationship, OCTOBER, "550.00", "YEAR_END");

        assertThat(rowsOf(corrected)).as("PY3 record: the corrected input is recorded").hasSize(1);
        assertThat(rowsOf(wrong)).as("the voided row stays").hasSize(1);
        assertThat(activeRowsOf(relationship, OCTOBER, "YEAR_END")).isEqualTo(1L);
    }

    @Test
    @DisplayName("#1436 PY3：没有薪酬角色的人（含发起人的直属上级）不能作废、不能读取；行不变")
    void withoutThePayrollRoleNothingIsVoidedOrRead() throws Exception {
        Submitted submitted = approved(UUID.randomUUID(), OCTOBER, "600.00", "YEAR_END");
        UUID id = rowId(submitted);

        for (SignedIn caller : List.of(outsiderAccount, lineManagerAccount)) {
            assertThatThrownBy(() -> voidInput(caller, id)).as("void needs the update permission").isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> summary(caller, id)).as("get needs the read permission").isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> list(caller, submitted.relationship(), OCTOBER, OCTOBER)).as("list needs the read permission").isInstanceOf(AccessDeniedException.class);
        }
        assertThat(String.valueOf(field(summary(specialistAccount, id), "status"))).as("the refused voids changed nothing").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("#1436 PY3：未知 id 的读取与作废得到同一个未找到错误 ORGANIZATION_PAYROLL_BONUS_INPUT_NOT_FOUND")
    void anUnknownIdIsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertRefusedWith(() -> summary(specialistAccount, unknown), NOT_FOUND);
        assertRefusedWith(() -> voidInput(specialistAccount, unknown), NOT_FOUND);
    }

    @Test
    @DisplayName("#1436 PY3：按关系列出月份区间内的输入，含两端，月份降序、再按 id，不含别的关系")
    void theListIsOneRelationshipsMonthRangeNewestFirst() throws Exception {
        UUID relationship = UUID.randomUUID();
        UUID september = rowId(approved(relationship, OCTOBER.minusMonths(1), "100.00", "YEAR_END"));
        UUID october = rowId(approved(relationship, OCTOBER, "200.00", "YEAR_END"));
        UUID november = rowId(approved(relationship, OCTOBER.plusMonths(1), "300.00", "YEAR_END"));
        UUID december = rowId(approved(relationship, OCTOBER.plusMonths(2), "400.00", "YEAR_END"));
        approved(UUID.randomUUID(), OCTOBER, "999.00", "YEAR_END");

        List<Object> listed = list(specialistAccount, relationship, OCTOBER.minusMonths(1), OCTOBER.plusMonths(1));

        assertThat(listed.stream().map(each -> field(each, "id")).toList()).as("PY3 record query path 2: month range both ends in, newest month first")
                .containsExactly(november, october, september);
        assertThat(listed.stream().map(each -> field(each, "id"))).doesNotContain(december);
    }

    @Test
    @DisplayName("#1436 PY3 路由（#1494）：第一级只有直属上级看得到，第二级只有非发起人的薪酬角色持有人看得到；第二级裁决前没有行")
    void eachStageIsSeenOnlyByItsCandidates() throws Exception {
        Submitted submitted = submit(UUID.randomUUID(), OCTOBER, "700.00", "YEAR_END");
        UUID first = nextTask(submitted, List.of());

        assertThat(detail(first, outsiderAccount).getStatus()).as("a person who is no candidate of the step does not see it (fail closed)").isEqualTo(404);
        assertThat(detail(first, secondSpecialistAccount).getStatus()).as("a payroll-role holder is not a candidate of the supervisor stage").isEqualTo(404);
        assertThat(detail(first, specialistAccount).getStatus()).as("the initiator is not a candidate of the stage that approves the own request").isEqualTo(404);
        assertThat(detail(first, lineManagerAccount).getStatus()).as("the initiator's line manager is the first candidate").isEqualTo(200);
        claimAndDecide(first, lineManagerAccount, "APPROVE");
        assertThat(rowsOf(submitted)).as("one stage is not the approval").isEmpty();

        UUID second = nextTask(submitted, List.of(first));
        assertThat(detail(second, lineManagerAccount).getStatus()).as("the line manager holds no payroll role: no candidate of the second stage").isEqualTo(404);
        assertThat(detail(second, outsiderAccount).getStatus()).isEqualTo(404);
        assertThat(detail(second, secondSpecialistAccount).getStatus()).as("a payroll-role holder who is not the initiator is a candidate").isEqualTo(200);
        assertThat(rowsOf(submitted)).isEmpty();
    }

    @Test
    @DisplayName("#1436 PY3 职责分离（#1494、完成标准 11）：发起人即使持有薪酬角色也不是第二级候选人，不能裁决；不写行")
    void theInitiatorCannotApproveTheOwnInput() throws Exception {
        Submitted submitted = submit(UUID.randomUUID(), OCTOBER, "800.00", "YEAR_END");
        UUID first = decideFirstStage(submitted, "APPROVE");
        UUID second = nextTask(submitted, List.of(first));

        assertThat(detail(second, specialistAccount).getStatus()).as("the initiator holds the payroll role but is no candidate").isEqualTo(404);
        // the version is read as a real candidate: the initiator cannot even read it, so act() would fail on that read instead of on the claim
        String version = versionOf(second, secondSpecialistAccount);
        int claimed = mvc.perform(post(TASKS + second + ":claim").header("If-Match", version).with(csrf()).with(session(specialistAccount))).andReturn().getResponse().getStatus();
        assertThat(claimed).as("the initiator cannot claim the second stage").isIn(403, 404);
        assertThat(rowsOf(submitted)).as("nothing was approved").isEmpty();
        assertThat(openTaskIds(submitted.document())).as("the stage is still open for a real candidate").contains(second);
    }
}
