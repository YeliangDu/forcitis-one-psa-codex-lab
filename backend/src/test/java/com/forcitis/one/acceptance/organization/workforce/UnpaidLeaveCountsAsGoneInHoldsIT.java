package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.integration.api.WecomCard;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #943, user decision 2026-10-30 「统一都算已走」 (fragment 2026-10-30-172337): every count of the remaining available super administrators treats
 * a released future-dated separation and an entered, not yet started unpaid-leave start as gone. Here: the separation hold decision, the release
 * through the interface and the two-person operations command (which runs only when the count is 0, user ruling 2026-10-30-131322). Fixture note: an
 * entered future leave of B is only accepted while another available super administrator X exists (the start floor), so X is there when B's leave
 * is entered and its account is disabled afterwards.
 */
class UnpaidLeaveCountsAsGoneInHoldsIT extends UnpaidLeaveStartFloorSupport {

    static final TestDatabase postgres = TestPostgres.database(UnpaidLeaveCountsAsGoneInHoldsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** A is held; B has an entered future leave; the only other administrator X (there while B's leave was entered) is gone. */
    private record OnlyGone(Staff a, Staff b, Staff releaser, Held held, UUID leave) {
    }

    private OnlyGone aHeldAndBHasAFutureLeave(boolean withdrawn) throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        Staff x = administrator();
        Staff releaser = auditor();
        Held held = heldSeparation(a);
        UUID leave = startAccepted(b, LATER);
        if (withdrawn) {
            actIn(hr);
            commands().withdraw(leave, "synthetic: the leave is cancelled");
            assertThat(commandStatus(leave)).isEqualTo(WITHDRAWN);
        }
        disableAccount(x);
        return new OnlyGone(a, b, releaser, held, leave);
    }

    // ---- hold decision (guards: the intersection rule already holds an available administrator's separation; nothing about it may change) ----

    @Test
    @DisplayName("#943 决定 172337 离职暂停判定：A、B 是仅有的可用超管，B 已录未来停薪；录入 A 的离职：被暂停（命令 HELD、暂停记录 HELD、仍在职、不发 EmployeeSeparated）。守住「不变」，在未实现时本例为绿")
    void aSeparationIsHeldWhenTheOtherAdministratorHasAFutureLeave() throws Exception {
        OnlyGone s = aHeldAndBHasAFutureLeave(false);
        Staff c = administrator();
        Held second = heldSeparation(c);

        assertThat(commandStatus(second.command())).isEqualTo(HELD);
        assertThat(holdStatus(second.hold())).isEqualTo(HELD);
        assertThat(employmentOn(c.employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(eventsOf(EVENT_SEPARATED, second.command())).isZero();
        assertThat(commandStatus(s.held().command())).isEqualTo(HELD);
    }

    @Test
    @DisplayName("#943 决定 172337 离职暂停判定：有第三名可用超管 C 时 A 的离职照旧被暂停（离职人是可用超管即暂停，与剩余多少无关，SeparationHoldDecisionIT）。守住「不变」，在未实现时本例为绿")
    void aSeparationWithAThirdAdministratorIsHeldAsBefore() throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        administrator();
        startAccepted(b, LATER);

        Held held = heldSeparation(a);

        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
        assertThat(employmentOn(a.employeeId(), TODAY)).isEqualTo(EMPLOYED);
    }

    // ---- release through the interface ----

    @Test
    @DisplayName("#943 决定 172337 界面放行：A 的暂停放行时，唯一另一名超管 B 已录未来停薪（算已走）剩 0：界面拒绝（非权限拒绝），暂停记录与命令仍 HELD，不发卡，不写放行审计")
    void aFutureLeaveOfTheOnlyOtherAdministratorLeavesNoneAtRelease() throws Exception {
        OnlyGone s = aHeldAndBHasAFutureLeave(false);
        int audited = auditCount(AUDIT_HOLD_RELEASED);
        forgetCards();

        Throwable refused = catchThrowable(() -> releaseAs(s.releaser(), s.held().hold(), REASON_TEXT));

        assertThat(refused).as("nobody available would remain").isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).isFalse();
        assertThat(holdStatus(s.held().hold())).isEqualTo(HELD);
        assertThat(commandStatus(s.held().command())).isEqualTo(HELD);
        assertThat(employmentOn(s.a().employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(cards()).isEmpty();
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isEqualTo(audited);
    }

    @Test
    @DisplayName("#943 决定 172337 界面放行：另有第三名超管 C，B 已录未来停薪：放行 A 剩 1（C）：放行成功，恰一张通知卡（主体为该暂停记录，收件人含 C 与放行人、不含 A），ALERT 行")
    void withAThirdAdministratorTheReleaseLeavesOneAndAlerts() throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        Staff c = administrator();
        Staff releaser = auditor();
        Held held = heldSeparation(a);
        startAccepted(b, LATER);
        forgetCards();
        int mark = logMark();

        releaseAs(releaser, held.hold(), REASON_TEXT);

        assertThat(holdStatus(held.hold())).isEqualTo(RELEASED);
        assertThat(commandStatus(held.command())).isEqualTo(EFFECTIVE);
        assertThat(cards()).as("exactly one card in total").hasSize(1);
        WecomCard card = cardsFor(held.hold()).getFirst();
        assertThat(recipientsOf(card)).contains(wecomUserIdOf(c.employeeId()), wecomUserIdOf(releaser.employeeId())).doesNotContain(wecomUserIdOf(a.employeeId()));
        assertAlertLine(alertsSince(mark));
    }

    @Test
    @DisplayName("#943 决定 172337 界面放行：B 已录的未来停薪被撤回后不再算走：放行 A 剩 1（B）：放行成功并发一张卡、有 ALERT 行（撤回即恢复计数）。在未实现时本例为绿")
    void aWithdrawnFutureLeaveDoesNotCountAtRelease() throws Exception {
        OnlyGone s = aHeldAndBHasAFutureLeave(true);
        forgetCards();
        int mark = logMark();

        releaseAs(s.releaser(), s.held().hold(), REASON_TEXT);

        assertThat(holdStatus(s.held().hold())).isEqualTo(RELEASED);
        assertThat(cardsFor(s.held().hold())).hasSize(1);
        assertAlertLine(alertsSince(mark));
    }

    // ---- the two-person operations command (runs only when 0 would remain, ruling 2026-10-30-131322) ----

    @Test
    @DisplayName("#943 决定 172337 + 用户裁定 131322 运维命令：唯一另一名超管 B 已录未来停薪（算已走）剩 0：两人在场的运维命令放行 A 成功（退出码 0），途径 OPERATIONS_COMMAND，命令 EFFECTIVE，审计记剩余 0")
    void theOperationsCommandRunsWhenTheOnlyOtherAdministratorHasAFutureLeave() throws Exception {
        OnlyGone s = aHeldAndBHasAFutureLeave(false);
        Pair pair = twoPeople();

        Result result = release(s.held(), pair, REASON_TEXT);

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        assertThat(holdStatus(s.held().hold())).isEqualTo(RELEASED);
        assertThat(hold(s.held().hold()).get("release_channel_kind")).isEqualTo(CHANNEL_OPERATIONS);
        assertThat(commandStatus(s.held().command())).isEqualTo(EFFECTIVE);
        assertThat(releaseAuditOf(s.a().employeeId())).hasSize(1);
        assertThat(REMAINING_ZERO.matcher(String.valueOf(releaseAuditOf(s.a().employeeId()).getFirst().get("detail"))).find()).as("the audit records 0 remaining").isTrue();
    }

    @Test
    @DisplayName("#943 决定 172337 + 用户裁定 131322 运维命令：另有第三名超管 C 时放行 A 后剩 1 名（B 的未来停薪算已走）：运维命令拒绝（退出码 1，提示改走界面），库内无变化。守住「不变」，在未实现时本例为绿")
    void theOperationsCommandStillRefusesWhenOneRemains() throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        administrator();
        Held held = heldSeparation(a);
        startAccepted(b, LATER);
        Snapshot before = snapshot(held);

        Result result = release(held, twoPeople(), REASON_TEXT);

        assertRefusedUnchanged(result, held, before, REFUSED);
    }
}
