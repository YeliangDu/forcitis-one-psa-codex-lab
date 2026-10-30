package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #871 IMP-1030-PAR-B2 (ADR-0068 items 1.2 to 1.5 and 1.8, W2 sections 1 and 2): entering a personnel command. The ID is the server's,
 * a command dated today or earlier takes effect in the entry transaction, a future one stays PENDING with its versions already written,
 * a separation takes effect the day after the last working day, and unpaid leave changes the employee version only.
 */
class PersonnelCommandEntryIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(PersonnelCommandEntryIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 2 项（行 39）：命令 ID 由服务端生成且是 UUIDv7；录入参数里没有可供客户端提供 ID、录入人或状态的字段；录入人记为调用者的账号")
    void theCommandIdIsMadeByTheServerAsUuidV7() throws Exception {
        UUID employee = employed(PROBATION);

        UUID first = enter(employee, REGULARIZE, TODAY.plusDays(10));
        UUID second = enter(employed(PROBATION), REGULARIZE, TODAY.plusDays(10));

        assertThat(first.version()).as("UUIDv7").isEqualTo(7);
        assertThat(second.version()).isEqualTo(7);
        assertThat(second).isNotEqualTo(first);
        assertThat(command(first).get("created_by")).as("the recorder is the signed-in account, not a field of the request").isEqualTo(hr.userId());
        assertThat(commands().enterComponents()).as("nothing the server decides can be supplied")
                .noneMatch(name -> name.equalsIgnoreCase(PERSONNEL_COMMAND_ID) || name.equalsIgnoreCase("id")
                        || CALLER_MUST_NOT_SUPPLY.stream().anyMatch(word -> name.toLowerCase().contains(word)));
        assertThat(commands().withdrawComponents()).noneMatch(name -> CALLER_MUST_NOT_SUPPLY.stream().anyMatch(word -> name.toLowerCase().contains(word)));
    }

    @ParameterizedTest(name = "dated {0} days from today")
    @CsvSource({"0", "-1", "-30"})
    @DisplayName("#871 ADR-0068 第 1 条第 8 项（行 66）、W2 第 0 节：生效日不晚于今天的命令在录入事务内执行：命令已生效、有 applied_at、版本落库并关闭前一版本、状态转换记入 state_transitions、发事件")
    void aCommandDatedTodayOrEarlierTakesEffectInTheEntryTransaction(int offset) throws Exception {
        UUID employee = employed(PROBATION);
        LocalDate day = TODAY.plusDays(offset);

        UUID command = enter(employee, REGULARIZE, day);

        Map<String, Object> row = command(command);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("applied_at")).as("applied_at is set exactly when EFFECTIVE").isEqualTo(NOW);
        List<Map<String, Object>> current = currentVersions(EMPLOYEE_VERSIONS, employee);
        assertContiguous(current);
        Map<String, Object> regular = inForceOn(current, TODAY);
        assertThat(regular.get("employment_phase_kind")).isEqualTo(REGULAR);
        assertThat(regular.get("effective_from_date")).isEqualTo(day);
        assertThat(regular.get("change_command_id")).isEqualTo(command);
        assertThat(regular.get("change_channel_kind")).isEqualTo("COMMAND");
        assertThat(inForceOn(current, day.minusDays(1)).get("employment_phase_kind")).as("the day before is still probation").isEqualTo(PROBATION);
        assertLawfulLifecycle(transitions(AGGREGATE_PERSONNEL_COMMAND, command), EFFECTIVE);
        assertThat(transitions(AGGREGATE_PERSONNEL_COMMAND, command).getLast().get("actor_id")).isEqualTo(hr.userId());
        assertThat(eventsOf(EVENT_REGULARIZED, command)).as("the event of a command that took effect").isEqualTo(1);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 8 项（行 65 至 66）、W2 第 0 节：生效日在将来的命令保持 PENDING、applied_at 为空，但版本已在录入时写入；按今天读仍是原状态，按生效日读是新状态；录入不发事件")
    void aFutureCommandStaysPendingWithItsVersionsAlreadyWritten() throws Exception {
        UUID employee = employed(PROBATION);
        LocalDate day = TODAY.plusDays(30);

        UUID command = enter(employee, REGULARIZE, day);

        Map<String, Object> row = command(command);
        assertThat(row.get("status")).isEqualTo(PENDING);
        assertThat(row.get("applied_at")).isNull();
        List<Map<String, Object>> current = currentVersions(EMPLOYEE_VERSIONS, employee);
        assertContiguous(current);
        assertThat(current).as("the future version is already there").hasSize(2);
        assertThat(inForceOn(current, TODAY).get("employment_phase_kind")).as("today is read from the version in force today").isEqualTo(PROBATION);
        Map<String, Object> future = inForceOn(current, day);
        assertThat(future.get("employment_phase_kind")).isEqualTo(REGULAR);
        assertThat(future.get("change_command_id")).isEqualTo(command);
        assertLawfulLifecycle(transitions(AGGREGATE_PERSONNEL_COMMAND, command), PENDING);
        assertThat(allCommandEventsOf(command)).as("entry publishes no event (contract Q7); the event comes when the command takes effect").isZero();
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 8 项补登（行 68）、W2 第 2 节：补登落在已有版本中间时作废受影响的行并追加，不改写、不删除任何已有行；被拆开的原事实以新行重新追加并保留原来源，时间线仍连续无重叠")
    void backDatingSupersedesAndAppendsAndNeverRewrites() throws Exception {
        UUID employee = employed(PROBATION);
        UUID second = newUnit();
        UUID third = newUnit();
        transfer(employee, LocalDate.of(2026, 10, 1), second);
        List<Map<String, Object>> before = allVersions(ASSIGNMENT_VERSIONS, employee);
        Map<String, Object> migrated = before.getFirst();

        UUID backdated = transfer(employee, LocalDate.of(2026, 8, 1), third);

        List<Map<String, Object>> after = allVersions(ASSIGNMENT_VERSIONS, employee);
        assertThat(after.size()).as("rows are only added").isGreaterThan(before.size());
        for (Map<String, Object> old : before) {
            Map<String, Object> same = after.stream().filter(row -> row.get("assignment_version_id").equals(old.get("assignment_version_id"))).findFirst().orElseThrow();
            for (String column : List.of("employee_id", "effective_from_date", "revision_number", "org_unit_id", "change_channel_kind", "change_command_id", "created_at")) {
                assertThat(same.get(column)).as("an existing row is never rewritten: " + column).isEqualTo(old.get(column));
            }
        }
        Map<String, Object> voided = after.stream().filter(row -> row.get("assignment_version_id").equals(migrated.get("assignment_version_id"))).findFirst().orElseThrow();
        assertThat(voided.get("superseded_at")).as("the migrated row is cut in two, so it is superseded").isEqualTo(NOW);
        assertThat(voided.get("superseding_command_id")).isEqualTo(backdated);

        List<Map<String, Object>> current = currentVersions(ASSIGNMENT_VERSIONS, employee);
        assertContiguous(current);
        assertThat(inForceOn(current, LocalDate.of(2026, 7, 1)).get("org_unit_id")).as("before the back-dated day: the original unit").isEqualTo(unit);
        assertThat(inForceOn(current, LocalDate.of(2026, 9, 1)).get("org_unit_id")).as("from the back-dated day: the new unit").isEqualTo(third);
        assertThat(inForceOn(current, TODAY).get("org_unit_id")).as("the later transfer still wins from its own day").isEqualTo(second);
        Map<String, Object> reappended = inForceOn(current, LocalDate.of(2026, 7, 1));
        assertThat(reappended.get("corrected_from_version_id")).as("the original fact is appended again, pointing at the row it came from").isEqualTo(migrated.get("assignment_version_id"));
        assertThat(reappended.get("change_channel_kind")).as("and keeps its original source").isEqualTo("MIGRATION");
        assertThat(inForceOn(current, LocalDate.of(2026, 9, 1)).get("change_command_id")).isEqualTo(backdated);
    }

    @ParameterizedTest(name = "last working day {0} gives effective date {1} and status {2}")
    @CsvSource({"2026-11-01, 2026-11-02, EFFECTIVE", "2026-11-02, 2026-11-03, PENDING", "2026-10-20, 2026-10-21, EFFECTIVE", "2026-12-31, 2027-01-01, PENDING"})
    @DisplayName("#871 ADR-0068 第 1 条第 5 项（行 52）：人事填最后工作日，离职版本的生效日为其次日（含月末、年末）；次日不晚于今天时当即生效，否则待生效")
    void theSeparationTakesEffectTheDayAfterTheLastWorkingDay(LocalDate lastWorkingDay, LocalDate effective, String status) throws Exception {
        UUID employee = employed(REGULAR);

        UUID command = separate(employee, lastWorkingDay);

        assertThat(command(command).get("effective_date")).isEqualTo(effective);
        assertThat(command(command).get("status")).isEqualTo(status);
        List<Map<String, Object>> current = currentVersions(EMPLOYEE_VERSIONS, employee);
        assertContiguous(current);
        Map<String, Object> separated = inForceOn(current, effective);
        assertThat(separated.get("employment_status")).isEqualTo(SEPARATED);
        assertThat(separated.get("effective_from_date")).isEqualTo(effective);
        assertThat(separated.get("employment_phase_kind")).as("a separated version has no phase").isNull();
        assertThat(separated.get("is_on_unpaid_leave")).isEqualTo(false);
        assertThat(inForceOn(current, lastWorkingDay).get("employment_status")).as("the last working day itself is still employed").isEqualTo(EMPLOYED);
        assertThat(eventsOf(EVENT_SEPARATED, command)).isEqualTo(status.equals(EFFECTIVE) ? 1 : 0);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 3 项（行 43）及第 3 条（行 104 起）：停薪留职开始、结束只改员工版本的 is_on_unpaid_leave，在职状态不变，任职版本一行不动")
    void unpaidLeaveSetsTheFlagOnEmployeeVersionsOnly() throws Exception {
        UUID employee = employed(REGULAR);
        List<Map<String, Object>> assignmentsBefore = allVersions(ASSIGNMENT_VERSIONS, employee);

        UUID start = enter(employee, UNPAID_LEAVE_START, LocalDate.of(2026, 10, 15));
        UUID end = enter(employee, UNPAID_LEAVE_END, LocalDate.of(2026, 10, 25));

        List<Map<String, Object>> current = currentVersions(EMPLOYEE_VERSIONS, employee);
        assertContiguous(current);
        assertThat(inForceOn(current, LocalDate.of(2026, 10, 14)).get("is_on_unpaid_leave")).isEqualTo(false);
        Map<String, Object> onLeave = inForceOn(current, LocalDate.of(2026, 10, 20));
        assertThat(onLeave.get("is_on_unpaid_leave")).isEqualTo(true);
        assertThat(onLeave.get("employment_status")).as("unpaid leave is not a separation").isEqualTo(EMPLOYED);
        assertThat(onLeave.get("change_command_id")).isEqualTo(start);
        assertThat(inForceOn(current, TODAY).get("is_on_unpaid_leave")).as("after the end command").isEqualTo(false);
        assertThat(inForceOn(current, TODAY).get("change_command_id")).isEqualTo(end);
        assertThat(allVersions(ASSIGNMENT_VERSIONS, employee)).as("assignment versions are not touched").isEqualTo(assignmentsBefore);
        assertThat(command(start).get("status")).isEqualTo(EFFECTIVE);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 5 项（行 54）：同一员工同一天只能有一条待生效命令，第二条被拒且不留任何行；另一名员工同一天、同一员工另一天都不受限")
    void oneOpenCommandPerEmployeePerDay() throws Exception {
        UUID employee = employed(PROBATION);
        LocalDate day = TODAY.plusDays(20);
        enter(employee, REGULARIZE, day);
        long commands = commandCount(employee);
        int versions = allVersions(EMPLOYEE_VERSIONS, employee).size();

        assertThatThrownBy(() -> transfer(employee, day, newUnit())).isInstanceOf(com.forcitis.one.kernel.DomainException.class);

        assertThat(commandCount(employee)).isEqualTo(commands);
        assertThat(allVersions(EMPLOYEE_VERSIONS, employee)).as("the refused command wrote no version").hasSize(versions);
        assertThat(enter(employed(PROBATION), REGULARIZE, day)).as("another employee, the same day").isNotNull();
        assertThat(transfer(employee, day.plusDays(1), newUnit())).as("the same employee, the next day").isNotNull();
    }

    @Test
    @DisplayName("#871 W2 第 3.4 节、用户决定 2026-10-30 Q8：命令写入的任职版本必须有岗位；迁入的任职没有岗位、调岗命令也不给岗位时，调岗被拒，不留命令行、不写任何版本行")
    void aTransferThatWouldLeaveAnAssignmentWithoutAPositionIsRefused() throws Exception {
        UUID employee = employedWithoutPosition(REGULAR);
        UUID elsewhere = newUnit();
        List<Map<String, Object>> assignments = allVersions(ASSIGNMENT_VERSIONS, employee);
        List<Map<String, Object>> employeeVersions = allVersions(EMPLOYEE_VERSIONS, employee);

        assertThatThrownBy(() -> transfer(employee, TODAY.minusDays(3), elsewhere)).isInstanceOf(com.forcitis.one.kernel.DomainException.class);

        assertThat(commandCount(employee)).as("no command row").isZero();
        assertThat(allVersions(ASSIGNMENT_VERSIONS, employee)).as("no assignment version written or superseded").isEqualTo(assignments);
        assertThat(allVersions(EMPLOYEE_VERSIONS, employee)).isEqualTo(employeeVersions);
    }

    @Test
    @DisplayName("#871 契约 Q7：录入写审计（暂名 entered），生效才发布领域事件；待生效命令不发布；生效的命令发布恰一次")
    void entryIsAuditedAndOnlyTakingEffectPublishes() throws Exception {
        UUID employee = employed(PROBATION);
        int entered = auditCount(AUDIT_ENTERED);

        UUID pending = transfer(employee, TODAY.plusDays(5), newUnit());
        UUID effective = transfer(employee, TODAY, newUnit());

        assertThat(auditCount(AUDIT_ENTERED)).as("both entries are audited").isEqualTo(entered + 2);
        assertThat(eventsOf(EVENT_TRANSFERRED, pending)).isZero();
        assertThat(allCommandEventsOf(pending)).isZero();
        assertThat(eventsOf(EVENT_TRANSFERRED, effective)).isEqualTo(1);
    }
}
