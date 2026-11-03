package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #894 IMP-1030-FC-C, design note of #878 section 10 items 2, 3 and 12 (ADR-0073 item 3.6 lines 71 and 73, guard 1 of item 3): nothing is
 * written while a command awaits; the last approval of the required set writes every version at the original effective date and the
 * command goes to PENDING or EFFECTIVE; with one request approved and another still open the command stays awaiting.
 */
class CountersignApprovalIT extends CountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(CountersignApprovalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final LocalDate DAY = TODAY.plusDays(10);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private Map<String, Object> assignmentOn(UUID employee, LocalDate day) throws Exception {
        return inForceOn(currentVersions(ASSIGNMENT_VERSIONS, employee), day);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 2 项、ADR-0073 第 3 条守护 1「未会签不写版本」：待会签期间没有任何行以该命令为来源，员工的任职、法人归属时间线与录入前相同")
    void nothingIsWrittenWhileTheCommandAwaits() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);

        assertThat(versionRowsWrittenBy(waiting.command())).isZero();
        assertThat(assignmentOn(waiting.employee(), DAY).get("org_unit_id")).isEqualTo(stored(unitE1));
        assertThat(allVersions(ASSIGNMENT_VERSIONS, waiting.employee())).hasSize(1);
    }

    @ParameterizedTest(name = "first decided: the head of {0}")
    @ValueSource(strings = {"previous", "new"})
    @DisplayName("#894 note #878 第 10 条第 12 项、第 4 节：一行已批准、另一行仍未决时命令保持待会签、不写版本（不会因「全部已批准」对空集成立而放行）；两个顺序都一样")
    void oneApprovalIsNotEnough(String first) throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff head = first.equals("previous") ? headE1 : headE2;
        UUID entity = first.equals("previous") ? e1 : e2;

        approve(head, waiting.command(), KIND_CHANGE, entity);

        assertThat(commandStatus(waiting.command())).isEqualTo(AWAITING);
        assertThat(openRequests(waiting.command())).isEqualTo(1);
        assertThat(decision(waiting.command(), KIND_CHANGE, entity)).isEqualTo(APPROVED);
        assertThat(versionRowsWrittenBy(waiting.command())).isZero();
        assertThat(allCommandEventsOf(waiting.command())).isZero();
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 2、3 项、第 4 节：两个法人的负责人都批准后（生效日在将来）：命令待生效、未发事件；任职版本以原生效日写入、来源是该命令、前一版本在该日关闭、时间线连续；两行请求已批准并记决定人、时刻与审计记录 ID，state_transitions 为待会签到待生效、请求为 OPEN 到 APPROVED；审计 approved 两条")
    void bothApprovalsWriteTheVersionsAtTheOriginalDate() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        int approved = auditCount(AUDIT_APPROVED);

        approveBoth(waiting.command());

        assertThat(commandStatus(waiting.command())).isEqualTo(PENDING);
        assertThat(command(waiting.command()).get("applied_at")).isNull();
        List<Map<String, Object>> current = currentVersions(ASSIGNMENT_VERSIONS, waiting.employee());
        assertContiguous(current);
        assertThat(inForceOn(current, DAY).get("org_unit_id")).isEqualTo(stored(unitE2));
        assertThat(inForceOn(current, DAY).get("change_command_id")).isEqualTo(waiting.command());
        assertThat(inForceOn(current, DAY).get("effective_from_date")).as("the original effective date, not the countersign day").isEqualTo(DAY);
        assertThat(inForceOn(current, TODAY).get("org_unit_id")).as("today is still the old unit").isEqualTo(stored(unitE1));
        assertThat(allCommandEventsOf(waiting.command())).as("a pending command publishes nothing").isZero();
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, waiting.command())).containsExactly(AWAITING, PENDING);
        for (UUID entity : List.of(e1, e2)) {
            Map<String, Object> request = request(waiting.command(), KIND_CHANGE, entity);
            assertThat(request.get("decision_kind")).isEqualTo(APPROVED);
            assertThat(request.get("decided_by")).isIn(headE1.userId(), headE2.userId());
            assertThat(request.get("decided_at")).isEqualTo(NOW);
            assertThat(request.get("decision_audit_entry_id")).isNotNull();
            assertThat(statusPath(AGGREGATE_COUNTERSIGN, (UUID) request.get("attribution_countersign_id"))).containsExactly(OPEN, APPROVED);
        }
        assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decided_by")).isEqualTo(headE1.userId());
        assertThat(request(waiting.command(), KIND_CHANGE, e2).get("decided_by")).isEqualTo(headE2.userId());
        assertThat(auditCount(AUDIT_APPROVED)).isEqualTo(approved + 2);
    }

    @ParameterizedTest(name = "effective date {0} days from today")
    @ValueSource(ints = {0, -3})
    @DisplayName("#894 note #878 第 10 条第 3 项、第 4 节、ADR-0068 第 1 条第 8 项：生效日是今天或更早的待会签命令，在最后一次批准的事务内转已生效：有 applied_at、版本写入、前一版本关闭；法人归属已变更事件恰一次（负载为命令、员工、生效日、前后法人），调岗事件一次")
    void aDueCommandTakesEffectInTheLastApproval(int offset) throws Exception {
        LocalDate date = TODAY.plusDays(offset);
        Awaiting waiting = crossEntityTransfer(date);

        approveBoth(waiting.command());

        assertThat(commandStatus(waiting.command())).isEqualTo(EFFECTIVE);
        assertThat(command(waiting.command()).get("applied_at")).isEqualTo(NOW);
        assertThat(assignmentOn(waiting.employee(), TODAY).get("org_unit_id")).isEqualTo(stored(unitE2));
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, waiting.command())).containsExactly(AWAITING, EFFECTIVE);
        assertThat(eventsOf(EVENT_LEGAL_ENTITY_CHANGED, waiting.command())).isEqualTo(1);
        assertThat(eventsOf(EVENT_TRANSFERRED, waiting.command())).isEqualTo(1);
        String json = eventJsonOf(EVENT_LEGAL_ENTITY_CHANGED, waiting.command()).get(0);
        for (String field : EVENT_PAYLOAD) {
            assertThat(json).as("payload field " + field).contains("\"" + field + "\"");
        }
        assertThat(json).contains(waiting.command().toString(), text(waiting.employee()), date.toString(), e1.toString(), e2.toString());
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 2、3 项、第 7 节、ADR-0073 第 3 条第 2 项：变更法人归属批准后，员工法人归属区间写入新法人（来源是该命令、渠道 COMMAND、旧区间在生效日关闭），时间线连续；事件恰一次，无调岗事件")
    void aLegalEntityChangeWritesTheAttributionInterval() throws Exception {
        Awaiting waiting = changeTo(e2, TODAY);

        approveBoth(waiting.command());

        assertThat(commandStatus(waiting.command())).isEqualTo(EFFECTIVE);
        List<Map<String, Object>> current = currentLegalEntities(waiting.employee());
        assertContiguous(current);
        Map<String, Object> now = inForceOn(current, TODAY);
        assertThat(now.get("legal_entity_id")).isEqualTo(e2);
        assertThat(now.get("change_command_id")).isEqualTo(waiting.command());
        assertThat(now.get("change_channel_kind")).isEqualTo("COMMAND");
        assertThat(inForceOn(current, TODAY.minusDays(1)).get("legal_entity_id")).isEqualTo(e1);
        assertThat(eventsOf(EVENT_LEGAL_ENTITY_CHANGED, waiting.command())).isEqualTo(1);
        assertThat(eventsOf(EVENT_TRANSFERRED, waiting.command())).isZero();
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 3 项：改法人的更正批准后在生效日已过时转已生效、写版本、发法人归属已变更事件一次")
    void aCorrectionTakesEffectWhenApproved() throws Exception {
        UUID employee = employed(REGULAR);
        actIn(hr);
        UUID correction = enterChange(employee, CountersignNames.CORRECTION, TODAY.minusDays(5), e2);

        approveBoth(correction);

        assertThat(commandStatus(correction)).isEqualTo(EFFECTIVE);
        assertThat(inForceOn(currentLegalEntities(employee), TODAY).get("legal_entity_id")).isEqualTo(e2);
        assertThat(eventsOf(EVENT_LEGAL_ENTITY_CHANGED, correction)).isEqualTo(1);
    }

    @Test
    @DisplayName("#894 note #878 第 6 节、ADR-0073 第 3 条第 6 项（行 71）、用户答复 Q1：决定不由调用方指明决定人（参数无决定人字段）；批准的决定人记为已登录账号")
    void theDeciderIsTheSignedInCaller() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);

        approve(headE1, waiting.command(), KIND_CHANGE, e1);

        assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decided_by")).isEqualTo(headE1.userId());
        assertThat(auditEntriesBy(headE1.userId(), AUDIT_APPROVED)).allSatisfy(entry -> assertThat(entry.get("actorId")).isEqualTo(headE1.userId().toString()));
    }
}
