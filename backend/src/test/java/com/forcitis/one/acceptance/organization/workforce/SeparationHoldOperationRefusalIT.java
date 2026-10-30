package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #877 IMP-1030-PAR-B8 (cases of #876; ADR-0068 item 2.4, lines 93 to 97; ADR-0030 and ADR-0055 decision 3 item 4): everything the command refuses,
 * each time before any change. The command runs only when releasing leaves 0 available super administrators, and operator and witness must both
 * stay, that is be locally employed and not in the HELD set (the user's ruling of 2026-10-30 on #877, decision fragment 2026-10-30-131322). Operator
 * and witness must be two different existing employees, the reason is 1 to 500 characters and not blank,
 * the hold must exist and be HELD, the audit partition of the month must exist (the precedent of {@code Nits1OperationsCommandsAuditPartitionIT}),
 * and the single operator mode of ADR-0030 does not apply (the production state is the mode off).
 */
class SeparationHoldOperationRefusalIT extends SeparationHoldOperationSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationHoldOperationRefusalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 95，照 identity 双人参数）：操作人与见证人是同一员工被拒（退出码 1），录入前拒绝：暂停记录、命令、版本、事件、审计都不变")
    void operatorAndWitnessMustDiffer() throws Exception {
        Held held = heldWithNoneRemaining();
        UUID same = employed(REGULAR);
        Snapshot before = snapshot(held);

        Result result = run(line(held.hold(), REASON_TEXT, same, same));

        assertRefusedUnchanged(result, held, before, REFUSED);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 95）、第 3 项 ADR-0030 行 79：没有 --witness（模式关闭即生产状态）被拒（退出码 1）：不被当作省略，不写 single-operator-mode 标注，库内无变化")
    void aMissingWitnessIsRefused() throws Exception {
        Held held = heldWithNoneRemaining();
        UUID operator = employed(REGULAR);
        Snapshot before = snapshot(held);

        Result result = run(line(held.hold(), REASON_TEXT, operator, null));

        assertRefusedUnchanged(result, held, before, REFUSED);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"--witness=", "--witness", "--witness=alice", "--witness=00000000-0000-0000-0000-000000000000", "--witness=7"})
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 95，identity 先例 P2-06、P2-07）：给了 --witness 但为空、无值、非 UUID、全零，一律被拒，不被当作省略")
    void aGivenButInvalidWitnessIsRefused(String witnessOption) throws Exception {
        Held held = heldWithNoneRemaining();
        UUID operator = employed(REGULAR);
        Snapshot before = snapshot(held);
        String[] base = line(held.hold(), REASON_TEXT, operator, null);
        String[] withWitness = java.util.Arrays.copyOf(base, base.length + 1);
        withWitness[base.length] = witnessOption;

        Result result = run(withWitness);

        assertRefusedUnchanged(result, held, before, null);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 95）：--witness 出现两次被拒，不取其一")
    void aRepeatedWitnessIsRefused() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        Snapshot before = snapshot(held);
        String[] base = line(held.hold(), REASON_TEXT, pair.operator(), pair.witness());
        String[] twice = java.util.Arrays.copyOf(base, base.length + 1);
        twice[base.length] = "--witness=" + employed(REGULAR);

        Result result = run(twice);

        assertRefusedUnchanged(result, held, before, null);
    }

    @ParameterizedTest(name = "[{index}] --operator={0}")
    @NullSource
    @ValueSource(strings = {"", "alice", "00000000-0000-0000-0000-000000000000"})
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 95）：--operator 缺失、为空、非 UUID、全零，一律被拒，库内无变化")
    void anInvalidOperatorIsRefused(String operator) throws Exception {
        Held held = heldWithNoneRemaining();
        UUID witness = employed(REGULAR);
        Snapshot before = snapshot(held);

        Result result = run(line(held.hold(), REASON_TEXT, operator, witness));

        assertRefusedUnchanged(result, held, before, null);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 95，identity 先例 requireExisting）：操作人或见证人不是库里的员工被拒（退出码 1），审计须能点名两人，库内无变化")
    void operatorAndWitnessMustBeEmployees() throws Exception {
        Held held = heldWithNoneRemaining();
        UUID person = employed(REGULAR);
        Snapshot before = snapshot(held);

        Result unknownOperator = run(line(held.hold(), REASON_TEXT, UUID.randomUUID(), person));
        assertRefusedUnchanged(unknownOperator, held, before, REFUSED);
        Result unknownWitness = run(line(held.hold(), REASON_TEXT, person, UUID.randomUUID()));
        assertRefusedUnchanged(unknownWitness, held, before, REFUSED);
    }

    @ParameterizedTest(name = "[{index}] case {0} (0 empty, 1 whitespace only, otherwise that many characters)")
    @ValueSource(ints = {0, 1, 501, 1000})
    @DisplayName("#877 ADR-0068 第 2 条第 1 项（行 79）、附件 DDL ck_separation_holds_release_reason：原因为空串、全空白（空格、制表、换行）、超过 500 个字被拒，库内无变化")
    void aBlankOrOverlongReasonIsRefused(int length) throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        Snapshot before = snapshot(held);
        String reason = switch (length) {
            case 0 -> "";
            case 1 -> " \t\n ";
            default -> "r".repeat(length);
        };

        Result result = release(held, pair, reason);

        assertRefusedUnchanged(result, held, before, null);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 94）：没有 --reason 被拒（原因必填），库内无变化")
    void aMissingReasonIsRefused() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        Snapshot before = snapshot(held);

        Result result = run(line(held.hold(), null, pair.operator(), pair.witness()));

        assertRefusedUnchanged(result, held, before, null);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 94）：没有 --separation-hold-id、ID 不是 UUID、ID 不存在，一律被拒（不是放行了别的记录），已有暂停记录不变")
    void anUnknownOrMissingHoldIsRefused() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        Snapshot before = snapshot(held);

        assertRefusedUnchanged(run(line(null, REASON_TEXT, pair.operator(), pair.witness())), held, before, null);
        assertRefusedUnchanged(run("--operation=" + COMMAND_NAME, "--" + HOLD_OPTION + "=not-a-uuid", "--" + REASON_OPTION + "=" + REASON_TEXT,
                "--" + OPERATOR_OPTION + "=" + pair.operator(), "--" + WITNESS_OPTION + "=" + pair.witness()), held, before, null);
        assertRefusedUnchanged(run(line(UUID.randomUUID(), REASON_TEXT, pair.operator(), pair.witness())), held, before, null);
        assertRefusedUnchanged(run(line(held.command(), REASON_TEXT, pair.operator(), pair.witness())), held, before, null);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 1 项（行 78、79）：已被界面放行的暂停记录再用运维命令放行被拒（退出码 1）：放行人、时间、事件与审计都不变")
    void aHoldReleasedOnTheInterfaceIsRefused() throws Exception {
        Scenario scenario = heldWithRemaining(1);
        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);
        Pair pair = twoPeople();
        Snapshot before = snapshot(scenario.held());

        Result result = release(scenario.held(), pair, "synthetic: again by operations");

        assertThat(result.exitCode()).as(result.text()).isEqualTo(REFUSED);
        assertThat(result.err()).isNotBlank();
        assertThat(snapshot(scenario.held())).isEqualTo(before);
        assertThat(holdStatus(scenario.held().hold())).isEqualTo(RELEASED);
        assertThat(hold(scenario.held().hold()).get("release_channel_kind")).as("still the interface release").isEqualTo(CHANNEL_INTERFACE);
    }

    @Test
    @DisplayName("#877 ADR-0068 第 1 条第 6 项（行 60）、第 2 条第 1 项（行 78）：已撤回的暂停记录用运维命令放行被拒（退出码 1）：此人仍在职，不发离职事件，不写放行审计")
    void aWithdrawnHoldIsRefused() throws Exception {
        Held held = heldWithNoneRemaining();
        actIn(hr);
        commands().withdraw(held.command(), "synthetic: the person stays");
        Pair pair = twoPeople();
        Snapshot before = snapshot(held);

        Result result = release(held, pair, REASON_TEXT);

        assertThat(result.exitCode()).as(result.text()).isEqualTo(REFUSED);
        assertThat(result.err()).isNotBlank();
        assertThat(snapshot(held)).isEqualTo(before);
        assertThat(holdStatus(held.hold())).isEqualTo(WITHDRAWN);
        assertThat(commandStatus(held.command())).isEqualTo(WITHDRAWN);
        assertThat(employmentOn(held.employee(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(eventsOf(EVENT_SEPARATED, held.command())).isZero();
        assertThat(eventsOf(EVENT_HOLD_RELEASED, held.command())).isZero();
        assertThat(releaseAuditOf(held.employee())).isEmpty();
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 95，identity 先例 Nits1OperationsCommandsAuditPartitionIT）：当月审计分区不存在时退出码 1，文案点名月份并说明常驻应用建分区，不暴露数据库原文，录入前拒绝：暂停记录、命令、版本、事件都不变，也不建分区")
    void aMissingAuditPartitionRefusesBeforeAnyChange() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        Snapshot before = snapshot(held);
        setToday(farFuture());
        YearMonth month = YearMonth.from(clock.instant().atOffset(ZoneOffset.UTC));
        Result result;
        try {
            result = release(held, pair, REASON_TEXT);
        } finally {
            setToday(TODAY);
        }

        assertThat(result.exitCode()).as(result.text()).isEqualTo(REFUSED);
        assertThat(result.err()).startsWith("refused:").contains(month.toString()).contains("resident application").doesNotContain("no partition of relation");
        assertThat(snapshot(held)).isEqualTo(before);
        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
        try (Connection catalog = schemaConnection("organization_workforce")) {
            assertThat(query(catalog, "select count(*) as n from pg_class where relname = ?", "audit_entries_p" + month.toString().replace("-", "")).get(0).get("n"))
                    .as("the command creates no partition").isEqualTo(0L);
        }
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 96）、ADR-0055 决定 3 第 4 项、ADR-0030 行 79：生产不适用单人模式：模式关闭（生产状态）下省略 --witness 被拒，且从不写 platform.identity.single-operator-mode.* 标注；两人齐备时才放行")
    void productionHasNoSingleOperatorPath() throws Exception {
        Held held = heldWithNoneRemaining();
        UUID operator = employed(REGULAR);
        Snapshot before = snapshot(held);

        Result alone = run(line(held.hold(), REASON_TEXT, operator, null));

        assertRefusedUnchanged(alone, held, before, REFUSED);
        Result both = release(held, new Pair(operator, employed(REGULAR)), REASON_TEXT);
        assertThat(both.exitCode()).as(both.text()).isEqualTo(DONE);
        assertThat(auditedActions(SINGLE_OPERATOR_AUDIT_PREFIX)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"--Witness=01900000-0000-7000-8000-00000000a002", "--witnes=01900000-0000-7000-8000-00000000a002", "-witness=x", "witness=x", "stray"})
    @DisplayName("#877 ADR-0068 第 2 条第 4 项（行 95，identity 先例 P2-07）：命令行出现未知选项或非选项参数（拼错、少横线、多余词）按用法错误或拒绝处理，不被当作省略见证人，库内无变化")
    void aMisspeltOptionIsNotAnOmission(String stray) throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        Snapshot before = snapshot(held);
        String[] base = line(held.hold(), REASON_TEXT, pair.operator(), pair.witness());
        String[] withStray = java.util.Arrays.copyOf(base, base.length + 1);
        withStray[base.length] = stray;

        Result result = run(withStray);

        assertRefusedUnchanged(result, held, before, null);
    }

    @ParameterizedTest(name = "[{index}] {0} remaining")
    @ValueSource(ints = {1, 2})
    @DisplayName("#877 用户裁定 2026-10-30-131322（决定片段，ADR-0068 第 2 条第 4 项）：放行后仍剩 1 名或 2 名可用超管时运维命令拒绝（退出码 1），提示改走界面单条放行；库内无变化，不写放行审计")
    void refusesWhenAdministratorsWouldRemain(int remaining) throws Exception {
        Scenario scenario = heldWithRemaining(remaining - 1);
        Pair pair = twoPeople();
        Snapshot before = snapshot(scenario.held());

        Result result = release(scenario.held(), pair, REASON_TEXT);

        assertRefusedUnchanged(result, scenario.held(), before, REFUSED);
        assertThat(POINTS_TO_INTERFACE.matcher(result.err()).find()).as("points to the release on the interface, in: " + result.err()).isTrue();
        assertThat(releaseAuditOf(scenario.held().employee())).isEmpty();
    }

    @Test
    @DisplayName("#877 用户裁定 2026-10-30-131322（决定片段，ADR-0068 第 2 条第 4 项）：待放行的离职人本人当操作人或见证人被拒（退出码 1），库内无变化")
    void theHeldLeaverCannotAttest() throws Exception {
        Held held = heldWithNoneRemaining();
        UUID other = employed(REGULAR);
        Snapshot before = snapshot(held);

        assertRefusedUnchanged(run(line(held.hold(), REASON_TEXT, held.employee(), other)), held, before, REFUSED);
        assertRefusedUnchanged(run(line(held.hold(), REASON_TEXT, other, held.employee())), held, before, REFUSED);
    }

    @Test
    @DisplayName("#877 用户裁定 2026-10-30-131322（决定片段，ADR-0068 第 2 条第 4 项）：另一名待放行者当操作人或见证人被拒（退出码 1）：留任须不在待放行名单中；两条暂停记录都不变；对照：换两名留任员工即放行（退出码 0，另一名待放行超管不算可用）")
    void anotherHeldPersonCannotAttest() throws Exception {
        Held held = heldWithNoneRemaining();
        Held alsoHeld = heldSeparation(administrator());
        UUID other = employed(REGULAR);
        Snapshot before = snapshot(held);
        Snapshot alsoBefore = snapshot(alsoHeld);

        assertRefusedUnchanged(run(line(held.hold(), REASON_TEXT, other, alsoHeld.employee())), held, before, REFUSED);
        assertRefusedUnchanged(run(line(held.hold(), REASON_TEXT, alsoHeld.employee(), other)), held, before, REFUSED);
        assertThat(snapshot(alsoHeld)).isEqualTo(alsoBefore);
        Result control = release(held, new Pair(other, employed(REGULAR)), REASON_TEXT);
        assertThat(control.exitCode()).as("control: the same hold with two who stay is released, so the refusals above were about the held person, not the count (the other held administrator is not available), " + control.text())
                .isEqualTo(DONE);
    }

    @Test
    @DisplayName("#877 用户裁定 2026-10-30-131322（决定片段，ADR-0068 第 2 条第 4 项）：已离职（本地不在职）的员工当操作人或见证人被拒（退出码 1），库内无变化")
    void aSeparatedEmployeeCannotAttest() throws Exception {
        Held held = heldWithNoneRemaining();
        UUID former = employed(REGULAR);
        actIn(hr);
        separate(former, LAST_DAY);
        assertThat(employmentOn(former, TODAY)).as("the former employee is separated").isEqualTo(SEPARATED);
        UUID other = employed(REGULAR);
        Snapshot before = snapshot(held);

        assertRefusedUnchanged(run(line(held.hold(), REASON_TEXT, former, other)), held, before, REFUSED);
        assertRefusedUnchanged(run(line(held.hold(), REASON_TEXT, other, former)), held, before, REFUSED);
    }

    @Test
    @DisplayName("#877 用户裁定 2026-10-30-131322（两人都须留任）、ADR-0068 第 3 条（行 107「停薪留职…不算留任」）：停薪留职的员工当操作人或见证人被拒（退出码 1），库内无变化；对照：停薪留职结束后同一人当操作人即放行（退出码 0）")
    void aPersonOnUnpaidLeaveCannotAttest() throws Exception {
        Held held = heldWithNoneRemaining();
        UUID onLeave = employed(REGULAR);
        actIn(hr);
        assertThat(commandStatus(enter(onLeave, UNPAID_LEAVE_START, TODAY.minusDays(3)))).isEqualTo(EFFECTIVE);
        assertThat(onUnpaidLeaveOn(onLeave, TODAY)).as("the person is on unpaid leave today").isTrue();
        UUID other = employed(REGULAR);
        Snapshot before = snapshot(held);

        assertRefusedUnchanged(run(line(held.hold(), REASON_TEXT, onLeave, other)), held, before, REFUSED);
        assertRefusedUnchanged(run(line(held.hold(), REASON_TEXT, other, onLeave)), held, before, REFUSED);

        actIn(hr);
        assertThat(commandStatus(enter(onLeave, UNPAID_LEAVE_END, TODAY.minusDays(1)))).isEqualTo(EFFECTIVE);
        Result control = release(held, new Pair(onLeave, other), REASON_TEXT);
        assertThat(control.exitCode()).as("control: back from leave the same person may attest, " + control.text()).isEqualTo(DONE);
    }
}
