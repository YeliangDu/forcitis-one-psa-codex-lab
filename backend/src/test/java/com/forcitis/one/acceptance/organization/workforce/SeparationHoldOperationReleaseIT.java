package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #877 IMP-1030-PAR-B8 (cases of #876; ADR-0068 item 2.4, lines 93 to 97; ADR-0055 decision 3 item 4): the ops command releases a held separation
 * when releasing leaves 0 available super administrators, the case the interface refuses (item 2.3, line 90). Two different people are named on the
 * command line; the hold is recorded as released through the operations command with no releasing account, the command runs at its original
 * effective date without being re-judged, and the audit carries operator, witness, reason and the ids.
 */
class SeparationHoldOperationReleaseIT extends SeparationHoldOperationSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationHoldOperationReleaseIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 93 至 95）、第 3 项（行 90）：放行后剩余 0 名超管（界面拒绝的情形）：两人在场的运维命令放行成功，退出码 0；暂停记录 RELEASED、途径 OPERATIONS_COMMAND、放行账号为空；命令 EFFECTIVE，离职自原生效日起生效，只发一次 EmployeeSeparated 与一次 SeparationHoldReleased（releasedBy 为空）")
    void releasesWhenNoAdministratorWouldRemain() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        int versionsBefore = allVersions(EMPLOYEE_VERSIONS, held.employee()).size();
        assertThat(profileStatus(new Staff(held.employee(), signIn(held.employee())))).as("held, the person is still signed in").isEqualTo(200);

        Result result = release(held, pair, REASON_TEXT);

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        assertExecutedAtTheOriginalDate(held, versionsBefore);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 95）、第 1 条第 8 项（行 67）：放行发生在三天后：离职版本仍从原生效日起（前后版本衔接，历史只作废不删除），命令生效日不变")
    void aLaterReleaseIsWrittenBackAtTheOriginalDate() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        int versionsBefore = allVersions(EMPLOYEE_VERSIONS, held.employee()).size();
        setToday(TODAY.plusDays(3));

        Result result = release(held, pair, REASON_TEXT);

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        assertExecutedAtTheOriginalDate(held, versionsBefore);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 3 项（行 91）、第 4 项（行 79「操作人与见证人只记审计」）：审计 organization.workforce.separation-hold.released 一条，主体为被放行员工（EMPLOYEE），内容含操作人、见证人、原因、命令 ID、暂停记录 ID、剩余可用超管数 0 与运维命令途径")
    void theAuditCarriesOperatorWitnessReasonAndIds() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();

        Result result = release(held, pair, REASON_TEXT);

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        List<Map<String, Object>> entries = releaseAuditOf(held.employee());
        assertThat(entries).as("one audit entry of the release").hasSize(1);
        Map<String, Object> entry = entries.getFirst();
        String detail = String.valueOf(entry.get("detail"));
        assertThat(entry.get("action")).isEqualTo(AUDIT_HOLD_RELEASED);
        assertThat(String.valueOf(entry.get("subjectKind"))).isEqualTo("EMPLOYEE");
        assertThat(detail).as("operator").contains(pair.operator().toString());
        assertThat(detail).as("witness").contains(pair.witness().toString());
        assertThat(detail).as("reason").contains(REASON_TEXT);
        assertThat(detail).as("command id").contains(held.command().toString());
        assertThat(detail).as("hold id").contains(held.hold().toString());
        assertThat(ROUTE_OPERATIONS.matcher(detail).find()).as("session or route is the operations command, in: " + detail).isTrue();
        assertThat(REMAINING_ZERO.matcher(detail).find()).as("remaining available super administrators (0) in: " + detail).isTrue();
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 1 项（行 79）边界、附件 DDL ck_separation_holds_release_reason：原因恰 500 个字接受并原样落库")
    void aReasonOfFiveHundredCharactersIsAccepted() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        String longest = "r".repeat(500);

        Result result = release(held, pair, longest);

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        assertThat(hold(held.hold()).get("release_reason")).isEqualTo(longest);
        assertThat(holdStatus(held.hold())).isEqualTo(RELEASED);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 94 至 95）：运维命令无需任何登录会话：即使界面上持放行权限的安全审计员存在，命令也不借用其会话，放行账号仍为空、审计不记该审计员为操作人")
    void noSessionIsBorrowed() throws Exception {
        Held held = heldWithNoneRemaining();
        Staff auditor = auditor();
        actIn(auditor);
        Pair pair = twoPeople();

        Result result = release(held, pair, REASON_TEXT);

        assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        assertThat(hold(held.hold()).get("released_by")).isNull();
        assertThat(auditQuery("actorId", auditor.userId().toString(), AUDIT_HOLD_RELEASED)).as("the signed-in auditor of the same process is not the operator").isEmpty();
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 1 项（行 79「放行只能一次」）：同一暂停记录用运维命令再放行一次被拒（退出码 1）：放行人、时间、原因、事件与审计都不变")
    void aHoldIsReleasedOnlyOnceByTheCommand() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        assertThat(release(held, pair, REASON_TEXT).exitCode()).isEqualTo(DONE);
        Snapshot afterFirst = snapshot(held);

        Result again = release(held, pair, "synthetic: again");

        assertThat(again.exitCode()).as(again.text()).isEqualTo(REFUSED);
        assertThat(snapshot(held)).isEqualTo(afterFirst);
        assertThat(releaseAuditOf(held.employee())).hasSize(1);
    }
}
