package com.forcitis.one.acceptance.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1436 acceptance part: the command {@code BonusInputs.request(RequestBonusInput)} on the real port (signature posted by the implementer on #1446; sources: the
 * PY3 record section "Approval seam, process and permissions" and the implementer's list of 2026-11-05). Every case goes red when the line it names is removed:
 * the submit permission, the relationship check (known, in service on the month, employer confirmed and the paying entity), the entry rules, the idempotent
 * retry, the initiator taken from the caller, and the amount-free summary. The relationships are real rows of the workforce schema.
 */
class BonusInputRequestIT extends BonusInputSupport {

    static final TestDatabase postgres = TestPostgres.database(BonusInputRequestIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String INVALID = "ORGANIZATION_PAYROLL_BONUS_INPUT_INVALID";

    private void refusedWithNoApproval(UUID relationship, java.time.LocalDate month, String amount, String currency, String reason, UUID payingEntity)
            throws Exception {
        int before = bonusApprovals().size();
        assertRefusedWith(() -> request(specialistAccount, requestOf(relationship, payingEntity, month, amount, currency, reason, UUID.randomUUID())), INVALID);
        assertThat(bonusApprovals()).as("a refused request starts no approval").hasSize(before);
    }

    @Test
    @DisplayName("#1436 PY3 request (a)：没有 organization.payroll:bonus-input:submit 的调用人被拒绝（AccessDenied），不发起审批")
    void aCallerWithoutTheSubmitPermissionIsDenied() throws Exception {
        UUID relationship = employedRelationship();
        int before = bonusApprovals().size();

        for (SignedIn caller : List.of(outsiderAccount, lineManagerAccount)) {
            assertThatThrownBy(() -> request(caller, requestOf(relationship, legalEntity.value(), OCTOBER, "100.00", "CNY", "YEAR_END", UUID.randomUUID())))
                    .isInstanceOf(AccessDeniedException.class);
        }

        assertThat(bonusApprovals()).as("no approval for a denied caller").hasSize(before);
    }

    @Test
    @DisplayName("#1436 PY3 request (b)：任职关系未知、该月不在职（无阶段）、用人单位未确认、用人单位不是发薪法人，均得 ORGANIZATION_PAYROLL_BONUS_INPUT_INVALID 且不发起审批")
    void theRelationshipMustBeKnownInServiceAndConfirmedAtThePayingEntity() throws Exception {
        refusedWithNoApproval(UUID.randomUUID(), OCTOBER, "100.00", "CNY", "YEAR_END", legalEntity.value());
        refusedWithNoApproval(relationshipOf(legalEntity.value(), OCTOBER.plusMonths(1), false), OCTOBER, "100.00", "CNY", "YEAR_END", legalEntity.value());
        refusedWithNoApproval(relationshipOf(legalEntity.value(), null, false), OCTOBER, "100.00", "CNY", "YEAR_END", legalEntity.value());
        refusedWithNoApproval(relationshipOf(legalEntity.value(), OCTOBER.minusMonths(6), true), OCTOBER, "100.00", "CNY", "YEAR_END", legalEntity.value());
        refusedWithNoApproval(relationshipOf(null, OCTOBER.minusMonths(6), false), OCTOBER, "100.00", "CNY", "YEAR_END", legalEntity.value());
    }

    @Test
    @DisplayName("#1436 PY3 request (c)：月份不是 1 号、金额非正或整数位超过 17、币种不是 CNY、原因键不合格（小写、过短、过长），均得 INVALID 且不发起审批；边界值（17 位整数、两位原因键）可通过")
    void theEntryRulesAreEnforcedAndTheirBoundariesAreAccepted() throws Exception {
        UUID relationship = employedRelationship();
        UUID entity = legalEntity.value();

        refusedWithNoApproval(relationship, OCTOBER.plusDays(1), "100.00", "CNY", "YEAR_END", entity);
        refusedWithNoApproval(relationship, OCTOBER, "0.00", "CNY", "YEAR_END", entity);
        refusedWithNoApproval(relationship, OCTOBER, "-5.00", "CNY", "YEAR_END", entity);
        refusedWithNoApproval(relationship, OCTOBER, "123456789012345678.00", "CNY", "YEAR_END", entity);
        refusedWithNoApproval(relationship, OCTOBER, "100.00", "USD", "YEAR_END", entity);
        refusedWithNoApproval(relationship, OCTOBER, "100.00", "CNY", "year_end", entity);
        refusedWithNoApproval(relationship, OCTOBER, "100.00", "CNY", "A", entity);
        refusedWithNoApproval(relationship, OCTOBER, "100.00", "CNY", "A".repeat(41), entity);
        refusedWithNoApproval(relationship, OCTOBER, "100.00", "CNY", "1ABC", entity);

        int before = bonusApprovals().size();
        request(specialistAccount, requestOf(relationship, entity, OCTOBER, "12345678901234567.00", "CNY", "AB", UUID.randomUUID()));
        request(specialistAccount, requestOf(relationship, entity, OCTOBER.plusMonths(1), "0.01", "CNY", "A" + "B".repeat(39), UUID.randomUUID()));
        assertThat(bonusApprovals()).as("the boundary values are accepted: 17 integer digits, the smallest amount, reason keys of 2 and 40 characters")
                .hasSize(before + 2);
    }

    @Test
    @DisplayName("#1436 PY3 request (d)：同一幂等键重试得到同一审批实例 id，approval_requests 只有一行；不同幂等键是另一份申请")
    void aRetryWithTheSameIdempotencyKeyReusesTheApproval() throws Exception {
        UUID relationship = employedRelationship();
        UUID key = UUID.randomUUID();
        int before = bonusApprovals().size();

        ApprovalInstanceId first = request(specialistAccount, requestOf(relationship, legalEntity.value(), OCTOBER, "100.00", "CNY", "YEAR_END", key));
        ApprovalInstanceId again = request(specialistAccount, requestOf(relationship, legalEntity.value(), OCTOBER, "100.00", "CNY", "YEAR_END", key));

        assertThat(again).as("the retry returns the instance of the first call").isEqualTo(first);
        assertThat(bonusApprovals()).as("one approval request for the retried key").hasSize(before + 1);

        ApprovalInstanceId other = request(specialistAccount, requestOf(relationship, legalEntity.value(), OCTOBER, "100.00", "CNY", "YEAR_END", UUID.randomUUID()));
        assertThat(other).as("another key is another request").isNotEqualTo(first);
        assertThat(bonusApprovals()).hasSize(before + 2);
    }

    @Test
    @DisplayName("#1436 PY3 request (e)：审批的发起人是调用人本人的员工 id（命令不接受客户端给的发起人）")
    void theInitiatorOfTheApprovalIsTheCaller() throws Exception {
        int before = bonusApprovals().size();

        request(specialistAccount, requestOf(employedRelationship(), legalEntity.value(), OCTOBER, "100.00", "CNY", "YEAR_END", UUID.randomUUID()));

        List<Map<String, Object>> started = bonusApprovals();
        assertThat(started).hasSize(before + 1);
        assertThat(started.getLast().get("initiator_id")).as("the caller's employee id").isEqualTo(stored(specialist));
    }

    @Test
    @DisplayName("#1436 PY3 request (f)：审批卡片摘要 approval_requests.summary_text 是「奖金输入 yyyy-MM-01」，不含金额也不含原因键")
    void theSummaryOfTheCardCarriesNeitherTheAmountNorTheReason() throws Exception {
        request(specialistAccount, requestOf(employedRelationship(), legalEntity.value(), OCTOBER, "1500.50", "CNY", "YEAR_END", UUID.randomUUID()));

        String summary = (String) bonusApprovals().getLast().get("summary_text");
        assertThat(summary).isEqualTo("奖金输入 2026-10-01").doesNotContain("1500").doesNotContain("YEAR_END");
    }
}
