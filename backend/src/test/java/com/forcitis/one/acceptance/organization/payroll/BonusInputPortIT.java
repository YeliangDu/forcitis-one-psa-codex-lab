package com.forcitis.one.acceptance.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * #1436 acceptance part (PY3 bonus input port), behaviour lines (lead rulings on #1446, items 1 to 5): an approved bonus-input approval writes exactly one
 * ACTIVE row; a rejected one writes none; a redelivered result is a no-op; the same reason twice for a relationship and month is refused while another reason
 * or month is not; the void is one command of a payroll-role holder, once; the reads need the read permission; an unknown id is not found. Codes are the module's
 * PayrollError names. Owed, not tested here: the void after an APPROVED settlement (PY4 is not built) and the entity scope of reads.
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
    @DisplayName("#1436 PY3：审批通过后写出一行有效奖金输入，值取自表单，录入人是申请人，id 就是单据 id")
    void anApprovedApprovalWritesOneActiveRow() throws Exception {
        UUID relationship = UUID.randomUUID();
        Submitted submitted = approved(relationship, OCTOBER, "1500.50", "YEAR_END");

        assertThat(rowsOf(submitted.id())).as("PY3 record: a row is written when the approval completes, and its id is the document id").hasSize(1);
        Object bonus = summary(specialistAccount, submitted.id());
        assertThat(field(bonus, "id")).isEqualTo(submitted.id());
        assertThat(field(bonus, "employmentRelationshipId")).as("the relationship of the form").isEqualTo(relationship);
        assertThat(field(bonus, "payrollMonthStartDate")).isEqualTo(OCTOBER);
        assertThat(field(bonus, "payingLegalEntityId")).as("the paying entity of the form").isEqualTo(legalEntity.value());
        assertThat((BigDecimal) field(bonus, "bonusAmount")).isEqualByComparingTo("1500.50");
        assertThat(field(bonus, "bonusCurrency")).isEqualTo("CNY");
        assertThat(field(bonus, "reasonKey")).isEqualTo("YEAR_END");
        assertThat(String.valueOf(field(bonus, "status"))).isEqualTo("ACTIVE");
        assertThat(field(bonus, "enteredByUserId")).as("PY3 record: the approval's requester, read through the approval api").isEqualTo(specialistAccount.userId());
        assertThat(field(bonus, "voidedByUserId")).isNull();
    }

    @Test
    @DisplayName("#1436 PY3：被驳回的审批不写任何行")
    void aRejectedApprovalWritesNothing() throws Exception {
        Submitted submitted = submit(UUID.randomUUID(), OCTOBER, "100.00", "YEAR_END");
        decide(submitted, "REJECT");
        awaitConsumed(submitted.id());

        assertThat(rowsOf(submitted.id())).as("PY3 record: a row is always an approved input, there is no pending state").isEmpty();
    }

    @Test
    @DisplayName("#1436 PY3：同一审批的结果事件重放后仍只有一行，行不变（按审批实例幂等）")
    void aReplayedResultIsANoOp() throws Exception {
        Submitted submitted = approved(UUID.randomUUID(), OCTOBER, "200.00", "YEAR_END");

        replay(submitted, ApprovalOutcome.APPROVED);

        assertThat(rowsOf(submitted.id())).as("PY3 record: uk on approval_instance_id, a replay finds one row").hasSize(1);
        assertThat(query("organization_payroll", "select count(*) as n from bonus_inputs where approval_instance_id = ?", submitted.instance().value()).getFirst().get("n"))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("#1436 PY3：同一关系、月份、原因的第二次有效输入不写行（原有行不变），错误码 " + "ORGANIZATION_PAYROLL_BONUS_INPUT_DUPLICATE_REASON 存在")
    void theSameReasonTwiceIsRefused() throws Exception {
        UUID relationship = UUID.randomUUID();
        Submitted first = approved(relationship, OCTOBER, "300.00", "YEAR_END");
        Submitted second = approved(relationship, OCTOBER, "999.00", "YEAR_END");

        assertThat(rowsOf(second.id())).as("PY3 record: the second approved input for the same key leaves no row").isEmpty();
        assertThat(activeRowsOf(relationship, OCTOBER, "YEAR_END")).isEqualTo(1L);
        assertThat((BigDecimal) field(summary(specialistAccount, first.id()), "bonusAmount")).as("the first input is unchanged").isEqualByComparingTo("300.00");
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
            assertThat(rowsOf(each.id())).as("PY3 record: one row each").hasSize(1);
        }
    }

    @Test
    @DisplayName("#1436 PY3：作废把有效行变为已作废并记下作废人；第二次作废被拒绝（ORGANIZATION_PAYROLL_BONUS_INPUT_ALREADY_VOIDED）")
    void aVoidIsOnceAndRecordsWhoDidIt() throws Exception {
        Submitted submitted = approved(UUID.randomUUID(), OCTOBER, "500.00", "YEAR_END");

        voidInput(specialistAccount, submitted.id());

        Object bonus = summary(specialistAccount, submitted.id());
        assertThat(String.valueOf(field(bonus, "status"))).isEqualTo("VOIDED");
        assertThat(field(bonus, "voidedByUserId")).as("PY3 record: the void pair").isEqualTo(specialistAccount.userId());
        assertRefusedWith(() -> voidInput(specialistAccount, submitted.id()), ALREADY_VOIDED);
        assertThat(String.valueOf(field(summary(specialistAccount, submitted.id()), "status"))).as("the refused second void changed nothing").isEqualTo("VOIDED");
    }

    @Test
    @DisplayName("#1436 PY3：作废后同一键可补录更正的输入，作废的行保留（记录：作废释放业务键）")
    void aVoidFreesTheKeyForACorrectedInput() throws Exception {
        UUID relationship = UUID.randomUUID();
        Submitted wrong = approved(relationship, OCTOBER, "500.00", "YEAR_END");
        voidInput(specialistAccount, wrong.id());

        Submitted corrected = approved(relationship, OCTOBER, "550.00", "YEAR_END");

        assertThat(rowsOf(corrected.id())).as("PY3 record: the corrected input is recorded").hasSize(1);
        assertThat(rowsOf(wrong.id())).as("the voided row stays").hasSize(1);
        assertThat(activeRowsOf(relationship, OCTOBER, "YEAR_END")).isEqualTo(1L);
    }

    @Test
    @DisplayName("#1436 PY3：没有薪酬角色的人、只有审批权限的人不能作废、不能读取；行不变")
    void withoutThePayrollPermissionNothingIsVoidedOrRead() throws Exception {
        Submitted submitted = approved(UUID.randomUUID(), OCTOBER, "600.00", "YEAR_END");

        for (SignedIn caller : List.of(outsiderAccount, approverAccount)) {
            assertThatThrownBy(() -> voidInput(caller, submitted.id())).as("void needs the void permission").isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> summary(caller, submitted.id())).as("get needs the read permission").isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> list(caller, submitted.relationship(), OCTOBER, OCTOBER)).as("list needs the read permission").isInstanceOf(AccessDeniedException.class);
        }
        assertThat(String.valueOf(field(summary(specialistAccount, submitted.id()), "status"))).as("the refused voids changed nothing").isEqualTo("ACTIVE");
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
        Submitted september = approved(relationship, OCTOBER.minusMonths(1), "100.00", "YEAR_END");
        Submitted october = approved(relationship, OCTOBER, "200.00", "YEAR_END");
        Submitted november = approved(relationship, OCTOBER.plusMonths(1), "300.00", "YEAR_END");
        Submitted december = approved(relationship, OCTOBER.plusMonths(2), "400.00", "YEAR_END");
        approved(UUID.randomUUID(), OCTOBER, "999.00", "YEAR_END");

        List<Object> listed = list(specialistAccount, relationship, OCTOBER.minusMonths(1), OCTOBER.plusMonths(1));

        assertThat(listed.stream().map(each -> field(each, "id")).toList()).as("PY3 record query path 2: month range both ends in, newest month first")
                .containsExactly(november.id(), october.id(), september.id());
        assertThat(listed.stream().map(each -> field(each, "id"))).doesNotContain(december.id());
    }

    @Test
    @DisplayName("#1436 PY3：非候选人看不到奖金输入审批步骤（失败即关闭），也就裁决不了、写不出行")
    void onlyAnApproverDecidesTheApproval() throws Exception {
        Submitted submitted = submit(UUID.randomUUID(), OCTOBER, "700.00", "YEAR_END");
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(20)).until(() -> !openTaskIds(submitted.document()).isEmpty());
        UUID task = openTaskIds(submitted.document()).getFirst();

        assertThat(detail(task, outsiderAccount).getStatus()).as("a person who is no candidate of the step does not even see it (fail closed)").isEqualTo(404);
        assertThat(detail(task, approverAccount).getStatus()).as("the approver is a candidate and sees it").isEqualTo(200);
        assertThat(rowsOf(submitted.id())).isEmpty();
    }
}
