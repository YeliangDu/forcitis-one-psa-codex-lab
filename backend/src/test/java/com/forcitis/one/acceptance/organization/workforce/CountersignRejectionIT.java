package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #894 IMP-1030-FC-C, design note of #878 section 10 item 4 and user answer Q1 (either head may reject): a rejection needs a reason, ends
 * the command as WITHDRAWN without versions or event, and lapses the other open requests; the drafter's withdrawal does the same. The
 * deadline job (item 11) is card E.
 */
class CountersignRejectionIT extends CountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(CountersignRejectionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final LocalDate DAY = TODAY.plusDays(10);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @ParameterizedTest(name = "reason [{0}]")
    @NullSource
    @ValueSource(strings = {"", " ", "\t \n"})
    @DisplayName("#894 note #878 第 10 条第 4 项、#893 数据设计（拒签理由必填、非空白）、ADR-0073 第 3 条第 6 项（行 72）：拒签不填理由或只填空白，被拒；请求仍未决，命令仍待会签")
    void aRejectionWithoutAReasonIsRefused(String reason) throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);

        Throwable failure = refusal(() -> reject(headE2, waiting.command(), KIND_CHANGE, e2, reason));

        assertThat(failure).isNotNull();
        assertThat(commandStatus(waiting.command())).isEqualTo(AWAITING);
        assertThat(openRequests(waiting.command())).isEqualTo(2);
    }

    @ParameterizedTest(name = "reason of {0} characters")
    @ValueSource(ints = {1, 500, 501})
    @DisplayName("#894 note #878 第 5.2 节、#893 数据设计（理由 1 至 500 字）边界：1 个字和 500 个字的拒签理由被接受并原样落库；501 个字被拒、命令仍待会签")
    void reasonLengthLimits(int length) throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        String reason = "r".repeat(length);

        Throwable failure = refusal(() -> reject(headE1, waiting.command(), KIND_CHANGE, e1, reason));

        if (length <= 500) {
            assertThat(failure).isNull();
            assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decision_reason")).isEqualTo(reason);
        } else {
            assertThat(failure).isNotNull();
            assertThat(commandStatus(waiting.command())).isEqualTo(AWAITING);
        }
    }

    @ParameterizedTest(name = "rejected by the head of {0}")
    @ValueSource(strings = {"previous", "new"})
    @DisplayName("#894 note #878 第 10 条第 4 项、第 4 节、用户答复 Q1：任一法人的负责人拒签都使命令转已撤回：理由与决定人落库，另一行未决请求转 LAPSED（无决定人、有决定时刻、无理由），无版本、无事件；state_transitions 与请求转换正确；审计 rejected 一条、lapsed 一条；此后另一位负责人不能再批准那行")
    void eitherHeadRejectionWithdrawsTheCommand(String who) throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        boolean previous = who.equals("previous");
        Staff rejecter = previous ? headE1 : headE2;
        Staff other = previous ? headE2 : headE1;
        UUID rejected = previous ? e1 : e2;
        UUID lapsed = previous ? e2 : e1;
        int rejections = auditCount(AUDIT_REJECTED);
        int lapses = auditCount(AUDIT_LAPSED);

        reject(rejecter, waiting.command(), KIND_CHANGE, rejected, "synthetic: wrong cost centre");

        assertThat(commandStatus(waiting.command())).isEqualTo(WITHDRAWN);
        Map<String, Object> row = request(waiting.command(), KIND_CHANGE, rejected);
        assertThat(row.get("decision_kind")).isEqualTo(REJECTED);
        assertThat(row.get("decision_reason")).isEqualTo("synthetic: wrong cost centre");
        assertThat(row.get("decided_by")).isEqualTo(rejecter.userId());
        Map<String, Object> lapse = request(waiting.command(), KIND_CHANGE, lapsed);
        assertThat(lapse.get("decision_kind")).isEqualTo(LAPSED);
        assertThat(lapse.get("decided_by")).isNull();
        assertThat(lapse.get("decided_at")).isEqualTo(NOW);
        assertThat(lapse.get("decision_reason")).isNull();
        assertThat(openRequests(waiting.command())).as("nothing left for a reminder to chase").isZero();
        assertThat(versionRowsWrittenBy(waiting.command())).isZero();
        assertThat(allCommandEventsOf(waiting.command())).isZero();
        assertThat(eventsOf(EVENT_LEGAL_ENTITY_CHANGED, waiting.command())).isZero();
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, waiting.command())).containsExactly(AWAITING, WITHDRAWN);
        assertThat(statusPath(AGGREGATE_COUNTERSIGN, (UUID) row.get("attribution_countersign_id"))).containsExactly(OPEN, REJECTED);
        assertThat(statusPath(AGGREGATE_COUNTERSIGN, (UUID) lapse.get("attribution_countersign_id"))).containsExactly(OPEN, LAPSED);
        assertThat(auditCount(AUDIT_REJECTED)).isEqualTo(rejections + 1);
        assertThat(auditCount(AUDIT_LAPSED)).isEqualTo(lapses + 1);
        assertThat(refusal(() -> approve(other, waiting.command(), KIND_CHANGE, lapsed))).as("a lapsed request cannot be approved").isNotNull();
        assertThat(decision(waiting.command(), KIND_CHANGE, lapsed)).isEqualTo(LAPSED);
        assertThat(commandStatus(waiting.command())).isEqualTo(WITHDRAWN);
    }

    @Test
    @DisplayName("#894 note #878 第 4 节、第 10 条第 4 项：一方已批准后另一方拒签：命令转已撤回，已批准的那行保持 APPROVED（不再改写），没有 LAPSED 行；没有版本")
    void aRejectionAfterTheOtherApprovalKeepsTheApproval() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        approve(headE1, waiting.command(), KIND_CHANGE, e1);

        reject(headE2, waiting.command(), KIND_CHANGE, e2, "synthetic: not agreed");

        assertThat(commandStatus(waiting.command())).isEqualTo(WITHDRAWN);
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isEqualTo(APPROVED);
        assertThat(decision(waiting.command(), KIND_CHANGE, e2)).isEqualTo(REJECTED);
        assertThat(versionRowsWrittenBy(waiting.command())).isZero();
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 4 项、第 4 节、ADR-0073 第 3 条第 6 项（行 72）：起草人在会签期间撤回：转已撤回，撤回人与理由落库，两行未决请求都转 LAPSED，审计 lapsed 两条，无版本无事件；该日空出，可再录同一天的命令")
    void theDrafterWithdrawsWhileAwaiting() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        int lapses = auditCount(AUDIT_LAPSED);
        actIn(hr);

        commands().withdraw(waiting.command(), "synthetic: wrong person");

        Map<String, Object> row = command(waiting.command());
        assertThat(row.get("status")).isEqualTo(WITHDRAWN);
        assertThat(row.get("withdrawn_by")).isEqualTo(hr.userId());
        assertThat(row.get("withdrawal_reason")).isEqualTo("synthetic: wrong person");
        assertThat(requests(waiting.command(), KIND_CHANGE)).hasSize(2).allSatisfy(request -> {
            assertThat(request.get("decision_kind")).isEqualTo(LAPSED);
            assertThat(request.get("decided_by")).isNull();
        });
        assertThat(auditCount(AUDIT_LAPSED)).isEqualTo(lapses + 2);
        assertThat(versionRowsWrittenBy(waiting.command())).isZero();
        assertThat(allCommandEventsOf(waiting.command())).isZero();
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, waiting.command())).containsExactly(AWAITING, WITHDRAWN);
        assertThat(transfer(waiting.employee(), DAY, unitE1b)).isNotNull();
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 4 项：已撤回的命令上的请求不能再决定：批准被拒，命令仍为已撤回")
    void nobodyDecidesOnAWithdrawnCommand() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        actIn(hr);
        commands().withdraw(waiting.command(), "synthetic: wrong person");

        assertThat(refusal(() -> approve(headE1, waiting.command(), KIND_CHANGE, e1))).isNotNull();

        assertThat(commandStatus(waiting.command())).isEqualTo(WITHDRAWN);
        assertThat(versionRowsWrittenBy(waiting.command())).isZero();
    }

    // ---- withdrawal of a countersigned command (item 5, user answer Q2) ----

    private Awaiting countersigned(LocalDate date) throws Exception {
        Awaiting waiting = crossEntityTransfer(date);
        approveBoth(waiting.command());
        assertThat(commandStatus(waiting.command())).isIn(PENDING, EFFECTIVE);
        return waiting;
    }

    private void assertVersionsStillCurrent(Awaiting waiting) throws Exception {
        assertThat(currentVersions(ASSIGNMENT_VERSIONS, waiting.employee())).anyMatch(row -> waiting.command().equals(row.get("change_command_id")));
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 5 项、第 4 节、ADR-0073 第 3 条第 6 项（行 72）：撤回已会签的待生效命令不立即撤回：命令仍待生效、版本仍在，对两个法人各开一行 WITHDRAWAL 请求（序号 1、请求人为撤回的人、起草人仍是原起草人），审计 requested 两条")
    void withdrawingACountersignedCommandOpensAWithdrawalSet() throws Exception {
        Awaiting waiting = countersigned(DAY);
        int requested = auditCount(AUDIT_REQUESTED);
        actIn(hr);

        commands().withdraw(waiting.command(), "synthetic: plan changed");

        assertThat(commandStatus(waiting.command())).isEqualTo(PENDING);
        assertVersionsStillCurrent(waiting);
        List<Map<String, Object>> set = requests(waiting.command(), KIND_WITHDRAWAL);
        assertThat(set).extracting(row -> row.get("legal_entity_id")).containsExactlyInAnyOrder(e1, e2);
        assertThat(set).allSatisfy(row -> {
            assertThat(row.get("request_sequence_number")).isEqualTo(1);
            assertThat(row.get("decision_kind")).isNull();
            assertThat(row.get("requested_by")).isEqualTo(hr.userId());
            assertThat(row.get("drafted_by")).isEqualTo(hr.userId());
        });
        assertThat(auditCount(AUDIT_REQUESTED)).isEqualTo(requested + 2);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 5 项、第 4 节：撤回请求只批准一行时命令保持待生效、版本仍在；两行都批准后命令转已撤回，版本作废（不删除，来源命令为作废命令）、时间线恢复为录入前的任职，不发事件")
    void allWithdrawalApprovalsSupersedeTheVersions() throws Exception {
        Awaiting waiting = countersigned(DAY);
        actIn(hr);
        commands().withdraw(waiting.command(), "synthetic: plan changed");

        approve(headE1, waiting.command(), KIND_WITHDRAWAL, e1);

        assertThat(commandStatus(waiting.command())).isEqualTo(PENDING);
        assertVersionsStillCurrent(waiting);
        approve(headE2, waiting.command(), KIND_WITHDRAWAL, e2);

        assertThat(commandStatus(waiting.command())).isEqualTo(WITHDRAWN);
        List<Map<String, Object>> current = currentVersions(ASSIGNMENT_VERSIONS, waiting.employee());
        assertContiguous(current);
        assertThat(current).noneMatch(row -> waiting.command().equals(row.get("change_command_id")));
        assertThat(inForceOn(current, DAY.plusDays(30)).get("org_unit_id")).isEqualTo(stored(unitE1));
        assertThat(allVersions(ASSIGNMENT_VERSIONS, waiting.employee())).anyMatch(row -> row.get("superseded_at") != null
                && waiting.command().equals(row.get("superseding_command_id")));
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, waiting.command())).containsExactly(AWAITING, PENDING, WITHDRAWN);
        assertThat(allCommandEventsOf(waiting.command())).isZero();
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 5 项、第 4 节（S1）：撤回请求被拒：命令保持待生效、版本仍在，未决的另一行转 LAPSED；之后可再次请求撤回，得到序号 2 的新一组请求（序号 1 的行不变），批准后命令转已撤回")
    void aRejectedWithdrawalLeavesTheCommandAndMayBeAskedAgain() throws Exception {
        Awaiting waiting = countersigned(DAY);
        actIn(hr);
        commands().withdraw(waiting.command(), "synthetic: plan changed");

        reject(headE2, waiting.command(), KIND_WITHDRAWAL, e2, "synthetic: the move is agreed");

        assertThat(commandStatus(waiting.command())).isEqualTo(PENDING);
        assertVersionsStillCurrent(waiting);
        assertThat(decision(waiting.command(), KIND_WITHDRAWAL, e2)).isEqualTo(REJECTED);
        assertThat(decision(waiting.command(), KIND_WITHDRAWAL, e1)).isEqualTo(LAPSED);
        actIn(hr);
        commands().withdraw(waiting.command(), "synthetic: asked again");
        List<Map<String, Object>> second = rows("select * from " + COUNTERSIGNS + " where personnel_command_id = ? and request_kind = 'WITHDRAWAL' and request_sequence_number = 2", waiting.command());
        assertThat(second).extracting(row -> row.get("legal_entity_id")).containsExactlyInAnyOrder(e1, e2);
        assertThat(second).allSatisfy(row -> assertThat(row.get("decision_kind")).isNull());
        approve(headE1, waiting.command(), KIND_WITHDRAWAL, e1);
        approve(headE2, waiting.command(), KIND_WITHDRAWAL, e2);

        assertThat(commandStatus(waiting.command())).isEqualTo(WITHDRAWN);
    }

    @Test
    @DisplayName("#894 用户答复 Q2（2026-10-30）、note #878 第 10 条第 5 项、第 4 节：撤回请求未决时生效日到了，请求不能再批准：命令不转已撤回，版本仍然有效（判断不依赖作业）")
    void anOpenWithdrawalCannotBeApprovedOnceTheDateHasCome() throws Exception {
        LocalDate date = TODAY.plusDays(3);
        Awaiting waiting = countersigned(date);
        actIn(hr);
        commands().withdraw(waiting.command(), "synthetic: plan changed");
        setToday(date);

        refusal(() -> approve(headE1, waiting.command(), KIND_WITHDRAWAL, e1));
        refusal(() -> approve(headE2, waiting.command(), KIND_WITHDRAWAL, e2));

        assertThat(commandStatus(waiting.command())).isNotEqualTo(WITHDRAWN);
        assertVersionsStillCurrent(waiting);
        assertThat(requests(waiting.command(), KIND_WITHDRAWAL)).noneMatch(row -> APPROVED.equals(row.get("decision_kind")));
    }

    @Test
    @DisplayName("#894 用户答复 Q2（2026-10-30）、ADR-0068 第 1 条第 4 项：已生效的已会签命令不能撤回（只能用经会签的更正撤销）：撤回被拒，不开撤回请求，版本仍在")
    void aCountersignedEffectiveCommandCannotBeWithdrawn() throws Exception {
        Awaiting waiting = countersigned(TODAY);
        actIn(hr);

        assertThat(refusal(() -> commands().withdraw(waiting.command(), "synthetic: too late"))).isInstanceOf(DomainException.class);

        assertThat(commandStatus(waiting.command())).isEqualTo(EFFECTIVE);
        assertThat(requests(waiting.command(), KIND_WITHDRAWAL)).isEmpty();
        assertVersionsStillCurrent(waiting);
    }
}
