package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * #871 IMP-1030-PAR-B2 (ADR-0068 item 1.6, lines 58 to 60, and item 1.4): withdrawing a PENDING or HELD command needs a reason of 1 to
 * 500 characters that is not blank, writes audit and no event, restores the timeline through the supersede mechanism of W2 section 2,
 * frees the day, and withdraws the separation hold in the same transaction. The hold is seeded in the workforce schema because
 * creating one is B4 and B6.
 */
class PersonnelCommandWithdrawalIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(PersonnelCommandWithdrawalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 6 项（行 59）、第 4 项（行 47 至 50）：撤回待生效命令：状态 WITHDRAWN、撤回人为调用者账号、原因落库、applied_at 仍为空，state_transitions 追加 PENDING 到 WITHDRAWN；写审计、不发事件")
    void withdrawingAPendingCommandRecordsWhoWhenAndWhy() throws Exception {
        UUID employee = employed(PROBATION);
        UUID command = enter(employee, REGULARIZE, TODAY.plusDays(15));
        int audited = auditCount(AUDIT_WITHDRAWN);

        commands().withdraw(command, "synthetic: wrong date");

        Map<String, Object> row = command(command);
        assertThat(row.get("status")).isEqualTo(WITHDRAWN);
        assertThat(row.get("withdrawn_by")).isEqualTo(hr.userId());
        assertThat(row.get("withdrawal_reason")).isEqualTo("synthetic: wrong date");
        assertThat(row.get("withdrawn_at")).isEqualTo(NOW);
        assertThat(row.get("applied_at")).isNull();
        List<Map<String, Object>> steps = transitions(AGGREGATE_PERSONNEL_COMMAND, command);
        assertLawfulLifecycle(steps, WITHDRAWN);
        assertThat(steps.getLast().get("from_status")).isEqualTo(PENDING);
        assertThat(steps.getLast().get("actor_id")).isEqualTo(hr.userId());
        assertThat(steps.getLast().get("actor_type")).isEqualTo(ACTOR_TYPE_USER);
        assertThat(auditCount(AUDIT_WITHDRAWN)).isEqualTo(audited + 1);
        assertThat(auditEntriesBy(hr.userId(), AUDIT_WITHDRAWN)).allSatisfy(entry -> assertThat(entry.get("actorId")).isEqualTo(hr.userId().toString()));
        assertThat(allCommandEventsOf(command)).as("withdrawal publishes nothing (contract Q7)").isZero();
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 8 项（行 67 的 W2 第 0 节）：撤回把该命令写入的未来版本作废而不删除，时间线恢复为录入前的事实，没有任何未作废的行还引用该命令")
    void withdrawingRestoresTheTimelineBySupersedingNotDeleting() throws Exception {
        UUID employee = employed(PROBATION);
        UUID command = enter(employee, REGULARIZE, TODAY.plusDays(15));

        commands().withdraw(command, "synthetic: not approved");

        List<Map<String, Object>> current = currentVersions(EMPLOYEE_VERSIONS, employee);
        assertContiguous(current);
        assertThat(current).noneMatch(row -> command.equals(row.get("change_command_id")));
        assertThat(inForceOn(current, TODAY.plusDays(60)).get("employment_phase_kind")).as("still probation, open-ended").isEqualTo(PROBATION);
        List<Map<String, Object>> superseded = allVersions(EMPLOYEE_VERSIONS, employee).stream().filter(row -> row.get("superseded_at") != null).toList();
        assertThat(superseded).as("the future row is superseded, not deleted").isNotEmpty()
                .allSatisfy(row -> assertThat(row.get("superseding_command_id")).isEqualTo(command));
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 5 项（行 54）：撤回后这一天空出来，同一员工可以再录同一天的命令")
    void aWithdrawnCommandFreesTheDay() throws Exception {
        UUID employee = employed(PROBATION);
        LocalDate day = TODAY.plusDays(15);
        UUID first = enter(employee, REGULARIZE, day);

        commands().withdraw(first, "synthetic: replaced by a transfer");

        assertThat(transfer(employee, day, newUnit())).isNotNull();
    }

    @ParameterizedTest(name = "reason of {0} characters")
    @ValueSource(ints = {1, 500})
    @DisplayName("#871 ADR-0068 第 1 条第 6 项（行 59）边界：撤回原因 1 个字和 500 个字都接受并原样落库")
    void reasonsAtTheLimitsAreAccepted(int length) throws Exception {
        UUID command = enter(employed(PROBATION), REGULARIZE, TODAY.plusDays(15));
        String reason = "r".repeat(length);

        commands().withdraw(command, reason);

        assertThat(command(command).get("withdrawal_reason")).isEqualTo(reason);
    }

    @ParameterizedTest(name = "reason [{0}]")
    @NullSource
    @ValueSource(strings = {"", " ", "   \t  ", "\n"})
    @DisplayName("#871 ADR-0068 第 1 条第 6 项（行 59）：撤回须填原因：空、空白一律拒绝，命令保持待生效，不写撤回审计")
    void aMissingOrBlankReasonIsRefused(String reason) throws Exception {
        UUID command = enter(employed(PROBATION), REGULARIZE, TODAY.plusDays(15));
        int audited = auditCount(AUDIT_WITHDRAWN);

        assertThatThrownBy(() -> commands().withdraw(command, reason)).isInstanceOf(RuntimeException.class);

        assertThat(command(command).get("status")).isEqualTo(PENDING);
        assertThat(auditCount(AUDIT_WITHDRAWN)).isEqualTo(audited);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 6 项（行 59）边界：501 个字的原因被拒，命令保持待生效")
    void aReasonOfFiveHundredAndOneCharactersIsRefused() throws Exception {
        UUID command = enter(employed(PROBATION), REGULARIZE, TODAY.plusDays(15));

        assertThatThrownBy(() -> commands().withdraw(command, "r".repeat(501))).isInstanceOf(RuntimeException.class);

        assertThat(command(command).get("status")).isEqualTo(PENDING);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 4、6 项（行 48、59）：只有待生效和待放行的命令能撤回；已生效和已撤回的命令撤回被拒，状态不变")
    void onlyPendingAndHeldCommandsCanBeWithdrawn() throws Exception {
        UUID effective = enter(employed(PROBATION), REGULARIZE, TODAY);
        UUID withdrawn = enter(employed(PROBATION), REGULARIZE, TODAY.plusDays(9));
        commands().withdraw(withdrawn, "synthetic: first withdrawal");

        assertThatThrownBy(() -> commands().withdraw(effective, "synthetic: too late")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> commands().withdraw(withdrawn, "synthetic: again")).isInstanceOf(DomainException.class);

        assertThat(command(effective).get("status")).isEqualTo(EFFECTIVE);
        assertThat(command(withdrawn).get("withdrawal_reason")).as("the first reason stands").isEqualTo("synthetic: first withdrawal");
    }

    private String holdStatus(UUID hold) throws Exception {
        return (String) rows("select status from " + SEPARATION_HOLDS + " where separation_hold_id = ?", hold).get(0).get("status");
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 6 项（行 60）：撤回待放行命令时，暂停记录在同一事务内同为已撤回（两行由同一事务更新），放行列保持为空，两者的 state_transitions 都从 HELD 转 WITHDRAWN")
    void withdrawingAHeldCommandWithdrawsTheHoldInTheSameTransaction() throws Exception {
        Held held = held();

        commands().withdraw(held.command(), "synthetic: the person stays");

        assertThat(command(held.command()).get("status")).isEqualTo(WITHDRAWN);
        assertThat(holdStatus(held.hold())).isEqualTo(WITHDRAWN);
        Map<String, Object> hold = rows("select released_at, released_by, release_reason, release_channel_kind from " + SEPARATION_HOLDS + " where separation_hold_id = ?", held.hold()).get(0);
        assertThat(hold.values()).as("a withdrawn hold was never released").containsOnlyNulls();
        Object commandTransaction = rows("select xmin::text as tx from " + COMMANDS + " where personnel_command_id = ?", held.command()).get(0).get("tx");
        Object holdTransaction = rows("select xmin::text as tx from " + SEPARATION_HOLDS + " where separation_hold_id = ?", held.hold()).get(0).get("tx");
        assertThat(holdTransaction).as("both rows were last written by one transaction").isEqualTo(commandTransaction);
        assertThat(transitions(AGGREGATE_PERSONNEL_COMMAND, held.command()).getLast()).containsEntry("from_status", HELD).containsEntry("to_status", WITHDRAWN);
        assertThat(transitions(AGGREGATE_SEPARATION_HOLD, held.hold()).getLast()).containsEntry("from_status", HELD).containsEntry("to_status", WITHDRAWN);
        assertThat(allCommandEventsOf(held.command())).isZero();
        assertThat(currentVersions(EMPLOYEE_VERSIONS, held.employee())).as("the employee stays employed").hasSize(1);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 6 项（行 60）：撤回被拒（原因为空）时命令和暂停记录都保持待放行，不会只撤一半")
    void aRefusedWithdrawalOfAHeldCommandChangesNeitherRow() throws Exception {
        Held held = held();

        assertThatThrownBy(() -> commands().withdraw(held.command(), " ")).isInstanceOf(RuntimeException.class);

        assertThat(command(held.command()).get("status")).isEqualTo(HELD);
        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
    }
}
