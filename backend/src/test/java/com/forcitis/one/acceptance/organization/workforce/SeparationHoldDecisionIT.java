package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #873 IMP-1030-PAR-B4 (ADR-0068 item 2.2, lines 80 to 85, and item 1.8, line 67; ADR-0055 decision 3 items 3 and 4): the hold decision is made
 * when a SEPARATE of an administrator is entered, inside the one lock that every separation takes (the lock itself is {@link SeparationHoldLockIT}).
 * Lead ruling on ADR-0028 decision 1 as kept by ADR-0055 decision 3 items 3 and 4: a separation is held when the leaver is one of the
 * available super administrator or security auditor holders, or when fewer than two available super administrators would be left, not
 * counting the leaver and not counting anybody already held. A held command goes to HELD with a HELD hold, the person stays employed and
 * no EmployeeSeparated is published. Each test builds its own pool of administrators (the count
 * is global), see {@link SeparationHoldSupport}.
 */
class SeparationHoldDecisionIT extends SeparationHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationHoldDecisionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private UUID enterInThread(PersonnelCommandsProbe probe, CountDownLatch ready, CountDownLatch go, Staff leaver) throws Exception {
        actAsTheCallerOnThisThread();
        ready.countDown();
        assertThat(go.await(30, TimeUnit.SECONDS)).as("the start signal").isTrue();
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, leaver.employeeId());
        fields.put(COMMAND_KIND, SEPARATE);
        fields.put(LAST_WORKING_DAY, LAST_DAY);
        return probe.enter(fields);
    }

    @RepeatedTest(8)
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 80 至 85）、ADR-0055 决定 3 第 3 项：三名超管中两人的离职命令在两个线程、两个事务里同时录入：两边都不报错（无死锁、无并发冲突），两条都被暂停（该判定只看离职人自己，所以本条证明不了锁，锁见 SeparationHoldLockIT）（各有一条 HELD 暂停记录，两人仍在职、不发 EmployeeSeparated），两边都不报错；再逐条放行，已在待放行中的人不算可用，故每次剩余 1 名并告警，两条都放行成功")
    void twoConcurrentSeparationsOfAdministratorsBothEndHeldWithoutError() throws Exception {
        Staff first = administrator();
        Staff second = administrator();
        Staff stays = administrator();
        Staff releaser = auditor();
        actIn(hr);
        PersonnelCommandsProbe probe = commands();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        List<UUID> commands = new ArrayList<>();
        try {
            List<Future<UUID>> entered = new ArrayList<>();
            for (Staff leaver : List.of(first, second)) {
                entered.add(threads.submit(() -> enterInThread(probe, ready, go, leaver)));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("both threads are at the gate").isTrue();
            go.countDown();
            for (Future<UUID> one : entered) {
                commands.add(one.get(60, TimeUnit.SECONDS));
            }
        } finally {
            threads.shutdownNow();
        }

        List<Staff> leavers = List.of(first, second);
        for (int i = 0; i < 2; i++) {
            assertThat(commandStatus(commands.get(i))).isEqualTo(HELD);
            assertThat(holdStatus(holdIdOf(commands.get(i)))).isEqualTo(HELD);
            assertThat(employmentOn(leavers.get(i).employeeId(), TODAY)).isEqualTo(EMPLOYED);
            assertThat(eventsOf(EVENT_SEPARATED, commands.get(i))).isZero();
            assertThat(eventsOf(EVENT_HELD, commands.get(i))).isEqualTo(1);
        }
        assertThat(rows("select 1 from " + SEPARATION_HOLDS + " where personnel_command_id in (?, ?)", commands.get(0), commands.get(1))).hasSize(2);
        for (int i = 0; i < 2; i++) {
            int mark = logMark();

            releaseAs(releaser, holdIdOf(commands.get(i)), REASON_TEXT);

            assertThat(alertsSince(mark)).as("the other held person is not available, so one remains: an alert").isNotEmpty();
            assertThat(employmentOn(leavers.get(i).employeeId(), TODAY)).isEqualTo(SEPARATED);
        }
        assertThat(employmentOn(stays.employeeId(), TODAY)).isEqualTo(EMPLOYED);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 85）、第 1 条第 4 项（行 47 至 50）：只剩两名超管时其中一人的离职命令被暂停：命令 HELD、暂停记录 HELD、applied_at 为空，此人仍在职、版本里没有离职行，不发 EmployeeSeparated，发一次 SeparationHeld（带暂停记录 ID 与原生效日）")
    void aHeldPersonStaysEmployedAndNoSeparationIsPublished() throws Exception {
        Staff leaver = administrator();
        Staff stays = administrator();

        Held held = heldSeparation(leaver);

        Map<String, Object> command = command(held.command());
        assertThat(command.get("applied_at")).isNull();
        assertThat(command.get("effective_date")).isEqualTo(TODAY);
        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
        assertThat(employmentOn(leaver.employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(employmentOn(leaver.employeeId(), TODAY.plusDays(400))).as("no future separation row is left behind").isEqualTo(EMPLOYED);
        assertThat(currentVersions(EMPLOYEE_VERSIONS, leaver.employeeId())).noneMatch(row -> SEPARATED.equals(row.get("employment_status")));
        assertContiguous(currentVersions(EMPLOYEE_VERSIONS, leaver.employeeId()));
        assertThat(eventsOf(EVENT_SEPARATED, held.command())).isZero();
        List<String> published = eventJsonOf(EVENT_HELD, held.command());
        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).contains(held.hold().toString()).contains(held.employee().toString()).contains(TODAY.toString());
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, held.command())).endsWith(HELD).doesNotContain(EFFECTIVE);
        assertThat(statusPath(AGGREGATE_SEPARATION_HOLD, held.hold())).endsWith(HELD);
        assertThat(profileStatus(leaver)).as("the held person keeps the session").isEqualTo(200);
        assertThat(signIn(leaver.employeeId()).userId()).as("and can still sign in, on the same account").isEqualTo(leaver.userId());
        assertThat(employmentOn(stays.employeeId(), TODAY)).isEqualTo(EMPLOYED);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 80 至 85）、第 1 条第 8 项（行 67）：暂停在录入时判定，未来生效日也一样：生效日在十天后的离职命令同样转 HELD，不留未来的离职版本")
    void theHoldIsDecidedAtEntryEvenForAFutureEffectiveDate() throws Exception {
        Staff leaver = administrator();
        administrator();
        actIn(hr);

        UUID command = separate(leaver.employeeId(), TODAY.plusDays(9));

        assertThat(commandStatus(command)).isEqualTo(HELD);
        assertThat(command(command).get("effective_date")).isEqualTo(TODAY.plusDays(10));
        assertThat(holdStatus(holdIdOf(command))).isEqualTo(HELD);
        assertThat(employmentOn(leaver.employeeId(), TODAY.plusDays(400))).isEqualTo(EMPLOYED);
    }

    @Test
    @DisplayName("#873 ADR-0055 决定 3 第 3 项（lead 裁定 ADR-0028 决定 1 保留）：离职人与可用超管持有人有交集即暂停，与剩余多少无关：三名超管中一人离职，剩下两名，仍被暂停，无 EmployeeSeparated")
    void aSuperAdminSeparationIsHeldEvenWhenTwoRemain() throws Exception {
        Staff leaver = administrator();
        administrator();
        administrator();

        UUID command = separateAsHr(leaver);

        assertThat(commandStatus(command)).isEqualTo(HELD);
        assertThat(employmentOn(leaver.employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(holdStatus(holdIdOf(command))).isEqualTo(HELD);
        assertThat(eventsOf(EVENT_SEPARATED, command)).isZero();
        assertThat(eventsOf(EVENT_HELD, command)).isEqualTo(1);
    }

    @Test
    @DisplayName("#873 ADR-0055 决定 3 第 3 项、ADR-0028 决定 1（安全审计员持有人有交集即暂停）：只持安全审计员角色（非超管）的人离职同样被暂停，即使有三名超管在")
    void anAuditorOnlySeparationIsHeld() throws Exception {
        Staff leaver = auditor();
        administrator();
        administrator();
        administrator();

        UUID command = separateAsHr(leaver);

        assertThat(commandStatus(command)).isEqualTo(HELD);
        assertThat(employmentOn(leaver.employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(holdStatus(holdIdOf(command))).isEqualTo(HELD);
        assertThat(eventsOf(EVENT_SEPARATED, command)).isZero();
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 85）、ADR-0055 决定 3 第 3 项：只有管理员的离职会被暂停：只剩一名超管时，普通员工的离职照常生效，他人的命令也不受阻")
    void anOrdinaryEmployeeIsSeparatedWhateverTheAdministratorCount() throws Exception {
        administrator();
        UUID ordinary = employed(REGULAR);
        actIn(hr);

        UUID command = separate(ordinary, LAST_DAY);

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(employmentOn(ordinary, TODAY)).isEqualTo(SEPARATED);
        assertThat(rows("select 1 from " + SEPARATION_HOLDS + " where employee_id = ?", stored(ordinary))).isEmpty();
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 83、84）、第 3 项（行 90）：已在待放行中的人不算剩余可用超管：只有 S1、S2 两名超管且都被暂停，放行 S1 时剩余 0 名（S2 在集合中不计；若计入则剩 1 名而放行），界面拒绝")
    void thoseAlreadyHeldAreNotCountedAtRelease() throws Exception {
        Staff s1 = administrator();
        Staff s2 = administrator();
        Staff releaser = auditor();
        Held first = heldSeparation(s1);
        heldSeparation(s2);

        Throwable refused = org.assertj.core.api.Assertions.catchThrowable(() -> releaseAs(releaser, first.hold(), REASON_TEXT));

        assertThat(refused).isInstanceOf(RuntimeException.class);
        assertThat(isPermissionRefusal(refused)).isFalse();
        assertThat(holdStatus(first.hold())).isEqualTo(HELD);
        assertThat(employmentOn(s1.employeeId(), TODAY)).isEqualTo(EMPLOYED);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 1 条第 6 项（行 60）、第 2 条第 1 项（行 78）、附件守护规则 1：撤回一条真实产生的待放行命令，暂停记录同事务置为已撤回，此人仍在职、不发任何离职事件；已撤回的暂停记录不能再放行")
    void withdrawingARealHeldCommandWithdrawsItsHoldAndCannotBeReleased() throws Exception {
        Staff leaver = administrator();
        administrator();
        Staff releaser = auditor();
        Held held = heldSeparation(leaver);
        actIn(hr);

        commands().withdraw(held.command(), "synthetic: the person stays");

        assertThat(commandStatus(held.command())).isEqualTo(WITHDRAWN);
        assertThat(holdStatus(held.hold())).isEqualTo(WITHDRAWN);
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, held.command())).endsWith(HELD, WITHDRAWN);
        assertThat(statusPath(AGGREGATE_SEPARATION_HOLD, held.hold())).endsWith(HELD, WITHDRAWN);
        assertThat(employmentOn(leaver.employeeId(), TODAY)).isEqualTo(EMPLOYED);
        assertThat(eventsOf(EVENT_SEPARATED, held.command())).isZero();
        int audited = auditCount(AUDIT_HOLD_RELEASED);

        assertThatThrownBy(() -> releaseAs(releaser, held.hold(), REASON_TEXT)).isInstanceOf(RuntimeException.class);

        assertThat(holdStatus(held.hold())).as("a withdrawn hold stays withdrawn").isEqualTo(WITHDRAWN);
        assertThat(commandStatus(held.command())).isEqualTo(WITHDRAWN);
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isEqualTo(audited);
        assertThat(eventsOf(EVENT_HOLD_RELEASED, held.command())).isZero();
    }
}
