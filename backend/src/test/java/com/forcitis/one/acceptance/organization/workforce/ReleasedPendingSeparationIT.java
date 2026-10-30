package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #931 IMP-1030-PAR-C1, user ruling of 2026-10-30 (decision fragment 2026-10-30-142845-one-lead (也发, 只发可用的, 算已走, 不能), PR #940;
 * ADR-0068 items 1.6, 2.3, 2.4). A released future-dated administrator separation (command PENDING, hold RELEASED) (2) counts as leaving when a later
 * release counts the remaining available super administrators, at the release only (the interface refuses at 0, the operations command is then
 * allowed); (3) can be withdrawn before its date (not part of the user ruling: it follows from ADR-0068 item 1.6, pending commands
 * can be withdrawn, and the separation_holds trigger, a hold leaves only HELD, so it stays RELEASED); (4) is not retained before its date: it can be neither the releaser of the interface nor the
 * operator or the witness of the operations command. Cases at entry and at the due-date job are left out on purpose: the count is unobservable there.
 */
class ReleasedPendingSeparationIT extends FutureHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(ReleasedPendingSeparationIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** Admins A and B and an auditor: A's future-dated separation is held and released (PENDING, one administrator B left), then B's is held. */
    private record Pair2(Staff a, Staff b, Staff releaser, Held heldA, Held heldB) {
    }

    private Pair2 aReleasedPendingThenAnotherHeld() throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        Staff releaser = auditor();
        Held heldA = heldSeparationOn(a, DUE);
        releaseAs(releaser, heldA.hold(), REASON_TEXT);
        assertThat(commandStatus(heldA.command())).as("A is released and waits for its date").isEqualTo(PENDING);
        Held heldB = heldSeparationOn(b, DUE);
        return new Pair2(a, b, releaser, heldA, heldB);
    }

    // ---- point 2: counted as leaving, at the release ----

    @Test
    @DisplayName("#931 用户裁定点 2（ADR-0068 第 2 条第 3 项）：放行 A 的将来日离职（剩余 B 一名，允许并告警）后，B 的将来日放行被拒（A 已放行待生效，算已离开，剩余 0；界面拒绝、非权限拒绝），不发卡、不写放行审计、B 的命令与暂停记录仍 HELD")
    void aReleasedPendingAdministratorCountsAsLeavingAtTheInterface() throws Exception {
        Pair2 s = aReleasedPendingThenAnotherHeld();
        int audited = auditCount(AUDIT_HOLD_RELEASED);
        forgetCards();

        Throwable refused = catchThrowable(() -> releaseAs(s.releaser(), s.heldB().hold(), REASON_TEXT));

        assertThat(refused).as("releasing B would leave no administrator once A has left").isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).as("the refusal is about the count").isFalse();
        assertThat(commandStatus(s.heldB().command())).isEqualTo(HELD);
        assertThat(holdStatus(s.heldB().hold())).isEqualTo(HELD);
        assertThat(cardsFor(s.heldB().hold())).isEmpty();
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isEqualTo(audited);
        assertThat(eventsOf(EVENT_HOLD_RELEASED, s.heldB().command())).isZero();
    }

    @Test
    @DisplayName("#931 用户裁定点 2、#877 裁定（运维命令只在剩 0 名时执行）：同一个 B 的放行改走双人运维命令成功（退出码 0）：途径 OPERATIONS_COMMAND，命令因生效日在将来转 PENDING、不发 EmployeeSeparated，审计记剩余 0")
    void theSameReleaseThroughTheOperationsCommandIsAllowed() throws Exception {
        Pair2 s = aReleasedPendingThenAnotherHeld();
        Pair pair = twoPeople();

        Result result = release(s.heldB(), pair, REASON_TEXT);

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        assertThat(holdStatus(s.heldB().hold())).isEqualTo(RELEASED);
        assertThat(hold(s.heldB().hold()).get("release_channel_kind")).isEqualTo(CHANNEL_OPERATIONS);
        assertThat(commandStatus(s.heldB().command())).as("dated in the future: PENDING").isEqualTo(PENDING);
        assertThat(eventsOf(EVENT_SEPARATED, s.heldB().command())).isZero();
        List<Map<String, Object>> entries = releaseAuditOf(s.heldB().employee());
        assertThat(entries).hasSize(1);
        String detail = String.valueOf(entries.getFirst().get("detail"));
        assertThat(REMAINING_ZERO.matcher(detail).find()).as("remaining available super administrators (0), counting A as leaving, in: " + detail).isTrue();
    }

    // ---- point 3: withdrawing a released, still-PENDING separation ----

    @Test
    @DisplayName("#931 ADR-0068 第 1 条第 6 项与 separation_holds 触发器（非用户裁定）：撤回已放行、仍 PENDING 的离职（生效日前）：命令路径 HELD、PENDING、WITHDRAWN；暂停记录路径只有 HELD、RELEASED 且整行不变（放行列不动）；生效日及日作业之后此人仍在职；一条 EmployeeSeparated 都没有；一条撤回审计；不再发卡；撤回后 A 不再算离开，B 的将来日放行得以通过")
    void aReleasedPendingSeparationCanBeWithdrawn() throws Exception {
        Staff a = administrator();
        Staff b = administrator();
        Staff releaser = auditor();
        Held heldA = heldSeparationOn(a, DUE);
        releaseAs(releaser, heldA.hold(), REASON_TEXT);
        assertThat(commandStatus(heldA.command())).isEqualTo(PENDING);
        Map<String, Object> holdBefore = hold(heldA.hold());
        int cards = cardsFor(heldA.hold()).size();
        int withdrawn = auditCount(AUDIT_WITHDRAWN);

        actIn(hr);
        commands().withdraw(heldA.command(), "synthetic: the leaver stays");

        assertThat(commandStatus(heldA.command())).isEqualTo(WITHDRAWN);
        assertThat(command(heldA.command()).get("applied_at")).isNull();
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, heldA.command())).containsExactly(HELD, PENDING, WITHDRAWN);
        assertThat(statusPath(AGGREGATE_SEPARATION_HOLD, heldA.hold())).as("no SeparationHold step: the release was decided").containsExactly(HELD, RELEASED);
        assertThat(hold(heldA.hold())).as("the hold row, release columns included, is untouched").isEqualTo(holdBefore);
        assertThat(employmentOn(heldA.employee(), DUE.plusDays(1))).as("the future separation version is voided").isEqualTo(EMPLOYED);
        assertThat(eventsOf(EVENT_SEPARATED, heldA.command())).isZero();
        assertThat(auditCount(AUDIT_WITHDRAWN)).as("one withdrawal audit").isEqualTo(withdrawn + 1);
        assertThat(cardsFor(heldA.hold())).as("no further card").hasSize(cards);

        Held heldB = heldSeparationOn(b, DUE);
        releaseAs(releaser, heldB.hold(), REASON_TEXT);
        assertThat(commandStatus(heldB.command())).as("A no longer counts as leaving: one administrator remains for B's release").isEqualTo(PENDING);

        setToday(DUE);
        runDueJob();
        setToday(DUE.plusDays(1));
        runDueJob();

        assertThat(eventsOf(EVENT_SEPARATED, heldA.command())).as("never published for a withdrawn command").isZero();
        assertThat(commandStatus(heldA.command())).isEqualTo(WITHDRAWN);
        assertThat(hold(heldA.hold())).isEqualTo(holdBefore);
        assertThat(employmentOn(heldA.employee(), DUE)).isEqualTo(EMPLOYED);
        assertThat(employmentOn(heldA.employee(), DUE.plusDays(1))).isEqualTo(EMPLOYED);
        assertThat(cardsFor(heldA.hold())).hasSize(cards);
    }

    // ---- point 4: not retained before the date ----

    @Test
    @DisplayName("#931 用户裁定点 4（ADR-0068 第 2 条第 2 项「留任」）：已放行、离职日在将来的 A 不能当放行人（管理模式下持有放行权限，放行另一条暂停被拒、非权限拒绝）；换不在其中的审计员放行同一条则成功")
    void aReleasedPendingLeaverCannotReleaseAnotherHold() throws Exception {
        Staff a = administrator();
        Staff c = administrator();
        administrator();
        Staff other = auditor();
        Held heldA = heldSeparationOn(a, DUE);
        releaseAs(other, heldA.hold(), REASON_TEXT);
        Held heldC = heldSeparationOn(c, DUE);
        Staff aInMode = inAdministratorMode(signedInAgain(a));
        int audited = auditCount(AUDIT_HOLD_RELEASED);

        Throwable refused = catchThrowable(() -> releaseAs(aInMode, heldC.hold(), REASON_TEXT));

        assertThat(refused).isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).as("A holds the permission in the administrator mode; the refusal is that A is not retained").isFalse();
        assertThat(holdStatus(heldC.hold())).isEqualTo(HELD);
        assertThat(commandStatus(heldC.command())).isEqualTo(HELD);
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isEqualTo(audited);

        releaseAs(other, heldC.hold(), REASON_TEXT);

        assertThat(commandStatus(heldC.command())).isEqualTo(PENDING);
    }

    @Test
    @DisplayName("#931 用户裁定点 4（ADR-0068 第 2 条第 4 项）：已放行待生效的 A 作运维命令的操作人被拒（退出码 1，库内无变化）")
    void aReleasedPendingLeaverCannotBeTheOperator() throws Exception {
        Pair2 s = aReleasedPendingThenAnotherHeld();
        Snapshot before = snapshot(s.heldB());

        Result result = run(line(s.heldB().hold(), REASON_TEXT, s.a().employeeId(), employed(REGULAR)));

        assertRefusedUnchanged(result, s.heldB(), before, REFUSED);
    }

    @Test
    @DisplayName("#931 用户裁定点 4（ADR-0068 第 2 条第 4 项）：已放行待生效的 A 作运维命令的见证人被拒（退出码 1，库内无变化）")
    void aReleasedPendingLeaverCannotBeTheWitness() throws Exception {
        Pair2 s = aReleasedPendingThenAnotherHeld();
        Snapshot before = snapshot(s.heldB());

        Result result = run(line(s.heldB().hold(), REASON_TEXT, employed(REGULAR), s.a().employeeId()));

        assertRefusedUnchanged(result, s.heldB(), before, REFUSED);
    }
}
