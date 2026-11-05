package com.forcitis.one.acceptance.organization.workforce.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1410: the workforce golden sample, the fictional person F-P01 with two employment lifecycles, driven in effective-date order through the
 * real entry (PersonnelCommands, the due job) with the business clock moved to each pack day. Every assertion message starts with its pack node
 * id. The pack's expected values are NOT_REVIEWED: a node whose actual differs from the pack is not asserted here and not bent to pass; it is
 * listed in the PR table. The pack dates are historical facts of the fixture, not today-like values.
 */
class WorkforceGoldenSampleIT extends GoldenSampleSupport {

    static final TestDatabase postgres = TestPostgres.database(WorkforceGoldenSampleIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate N007 = LocalDate.of(2026, 1, 5);
    private static final LocalDate N016 = LocalDate.of(2026, 4, 5);
    private static final LocalDate N022 = LocalDate.of(2026, 6, 15);
    private static final LocalDate N023 = LocalDate.of(2026, 7, 1);
    private static final LocalDate N026 = LocalDate.of(2026, 8, 10);
    private static final LocalDate N027 = LocalDate.of(2026, 8, 17);
    private static final LocalDate N030 = LocalDate.of(2026, 10, 1);
    private static final LocalDate N034 = LocalDate.of(2026, 12, 10);
    private static final LocalDate N036 = LocalDate.of(2026, 12, 31);
    private static final LocalDate AFTER_N036 = LocalDate.of(2027, 1, 1);
    private static final LocalDate SECOND_ROUND_START = LocalDate.of(2027, 3, 1);
    private static final LocalDate N068 = LocalDate.of(2028, 2, 5);
    private static final LocalDate N070 = LocalDate.of(2028, 2, 29);
    private static final LocalDate AFTER_N070 = LocalDate.of(2028, 3, 1);
    private static final LocalDate N078 = LocalDate.of(2028, 3, 31);

    private static final String ASSIGNMENTS = "assignment_versions";
    private static final String EMPLOYEE_VERSIONS = "employee_versions";

    private final List<String> report = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();

    private void node(String id, Runnable checks) {
        try {
            checks.run();
            report.add(id + " PASS");
        } catch (AssertionError mismatch) {
            report.add(id + " FAIL " + mismatch.getMessage().replace('\n', ' '));
            failed.add(id);
        }
    }

    @Test
    @DisplayName("#1410 人员黄金样例 F-P01：两轮生命期按生效日顺序走真实入口，逐节点断言；与包不符的节点不提交为红")
    void fP01GoesThroughTwoLifecycles() throws Exception {
        UUID juniorNanjing = newPosition();
        UUID seniorNanjing = newPosition();
        UUID supportShanghai = newPosition();
        UUID secondRound = newPosition();

        // HCM-LIFE-007 first day at work: one employee, in service, one primary post
        onDay(N007);
        PersonnelCommandId hire = hire(N007, nanjingUnit, juniorNanjing, EmploymentPhaseKind.PROBATION);
        UUID person = employeeOf(hire);
        node("HCM-LIFE-007", () -> {
            try {
                assertThat(employeeCount()).as("HCM-LIFE-007 employee count").isEqualTo(1);
                assertThat(employmentOn(person, N007)).as("HCM-LIFE-007 in service on the first day").contains(EmploymentStatus.EMPLOYED);
                assertThat(employmentOn(person, N007.minusDays(1))).as("HCM-LIFE-007 not in service before the first day").isEmpty();
                assertThat(inForceOn(ASSIGNMENTS, person, N007)).as("HCM-LIFE-007 one primary assignment on the first day").isPresent();
                assertThat(inForceOn(ASSIGNMENTS, person, N007).orElseThrow().get("org_unit_id")).as("HCM-LIFE-007 primary post in the Nanjing unit")
                        .isEqualTo(stored(nanjingUnit));
                assertThat(commandStatus(hire)).as("HCM-LIFE-007 the hire is effective").isEqualTo("EFFECTIVE");
                assertThat(eventsOf("EmployeeHired", hire)).as("HCM-LIFE-007 one EmployeeHired event").isEqualTo(1);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });

        // HCM-LIFE-016 probation passed: same relationship, phase REGULAR from the day, primary post untouched
        onDay(N016);
        PersonnelCommandId regularize = enter(person, PersonnelCommandKind.REGULARIZE, N016);
        node("HCM-LIFE-016", () -> {
            try {
                assertThat(inForceOn(EMPLOYEE_VERSIONS, person, N016.minusDays(1)).orElseThrow().get("employment_phase_kind"))
                        .as("HCM-LIFE-016 the day before the pass is still probation").isEqualTo("PROBATION");
                assertThat(inForceOn(EMPLOYEE_VERSIONS, person, N016).orElseThrow().get("employment_phase_kind"))
                        .as("HCM-LIFE-016 regular from the day").isEqualTo("REGULAR");
                assertThat(inForceOn(ASSIGNMENTS, person, N016).orElseThrow().get("position_id")).as("HCM-LIFE-016 primary post unchanged")
                        .isEqualTo(stored(juniorNanjing));
                assertThat(employeeCount()).as("HCM-LIFE-016 still one employee").isEqualTo(1);
                assertThat(eventsOf("EmployeeRegularized", regularize)).as("HCM-LIFE-016 one EmployeeRegularized event").isEqualTo(1);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });

        // HCM-LIFE-022 future-dated promotion entered; HCM-LIFE-023 it takes effect through the due job
        onDay(N022);
        PersonnelCommandId promotion = transfer(person, N023, nanjingUnit, seniorNanjing);
        node("HCM-LIFE-022", () -> {
            try {
                assertThat(commandStatus(promotion)).as("HCM-LIFE-022 the future-dated promotion is pending").isEqualTo("PENDING");
                assertThat(inForceOn(ASSIGNMENTS, person, N023.minusDays(1)).orElseThrow().get("position_id"))
                        .as("HCM-LIFE-022 06-30 still the old post").isEqualTo(stored(juniorNanjing));
                assertThat(eventsOf("EmployeeTransferred", promotion)).as("HCM-LIFE-022 no event before the day").isZero();
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });
        onDay(N023);
        runDueJob();
        node("HCM-LIFE-023", () -> {
            try {
                assertThat(commandStatus(promotion)).as("HCM-LIFE-023 the due job makes the promotion effective").isEqualTo("EFFECTIVE");
                assertThat(inForceOn(ASSIGNMENTS, person, N023).orElseThrow().get("position_id")).as("HCM-LIFE-023 the new primary post from the day")
                        .isEqualTo(stored(seniorNanjing));
                assertThat(inForceOn(ASSIGNMENTS, person, N023.minusDays(1)).orElseThrow().get("position_id"))
                        .as("HCM-LIFE-023 the old primary post stays readable in history").isEqualTo(stored(juniorNanjing));
                assertThat(eventsOf("EmployeeTransferred", promotion)).as("HCM-LIFE-023 one EmployeeTransferred event").isEqualTo(1);
                assertThat(employeeCount()).as("HCM-LIFE-023 still one employee").isEqualTo(1);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });

        // HCM-LIFE-026 study leave as temporary absence (partial: no capacity, no benefit inputs); HCM-LIFE-027 back on the same relationship
        onDay(N026);
        PersonnelCommandId leaveStart = enter(person, PersonnelCommandKind.UNPAID_LEAVE_START, N026);
        node("HCM-LIFE-026", () -> {
            try {
                assertThat(employmentOn(person, N026)).as("HCM-LIFE-026 still in service, not a first separation").contains(EmploymentStatus.EMPLOYED);
                assertThat(directory.availabilityOn(employeeIdOf(person), N026).onUnpaidLeave()).as("HCM-LIFE-026 on leave from the day").isTrue();
                assertThat(inForceOn(EMPLOYEE_VERSIONS, person, N026).orElseThrow().get("is_on_unpaid_leave")).as("HCM-LIFE-026 version marked on leave").isEqualTo(true);
                assertThat(eventsOf("EmployeeUnpaidLeaveStarted", leaveStart)).as("HCM-LIFE-026 one EmployeeUnpaidLeaveStarted event").isEqualTo(1);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });
        onDay(N027);
        enter(person, PersonnelCommandKind.UNPAID_LEAVE_END, N027);
        node("HCM-LIFE-027", () -> {
            try {
                assertThat(directory.availabilityOn(employeeIdOf(person), N027).onUnpaidLeave()).as("HCM-LIFE-027 working again on the return day").isFalse();
                assertThat(directory.availabilityOn(employeeIdOf(person), N027.minusDays(1)).onUnpaidLeave()).as("HCM-LIFE-027 the day before still on leave").isTrue();
                assertThat(employeeCount()).as("HCM-LIFE-027 no new employee for the return").isEqualTo(1);
                assertThat(rows("select count(*) as n from personnel_commands where employee_id = ? and command_kind in ('HIRE', 'REINSTATE')", stored(person)).get(0).get("n"))
                        .as("HCM-LIFE-027 no rehire command for the return").isEqualTo(1L);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });

        // HCM-LIFE-030 same-employer move to the Shanghai unit; the old unit's history stays
        onDay(N030);
        PersonnelCommandId move = transfer(person, N030, shanghaiUnit, supportShanghai);
        node("HCM-LIFE-030", () -> {
            try {
                assertThat(inForceOn(ASSIGNMENTS, person, N030).orElseThrow().get("org_unit_id")).as("HCM-LIFE-030 primary post in the Shanghai unit")
                        .isEqualTo(stored(shanghaiUnit));
                assertThat(inForceOn(ASSIGNMENTS, person, N030.minusDays(1)).orElseThrow().get("org_unit_id"))
                        .as("HCM-LIFE-030 the Nanjing history is not rewritten").isEqualTo(stored(nanjingUnit));
                assertThat(employmentOn(person, N030)).as("HCM-LIFE-030 same relationship, still in service").contains(EmploymentStatus.EMPLOYED);
                assertThat(commandStatus(move)).as("HCM-LIFE-030 the move is effective").isEqualTo("EFFECTIVE");
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });

        // HCM-LIFE-034 resignation approved (partial: SEPARATE only, no handover); HCM-LIFE-036 last working day, the relationship ends after it
        onDay(N034);
        PersonnelCommandId firstSeparation = separate(person, N036);
        node("HCM-LIFE-034", () -> {
            try {
                assertThat(commandStatus(firstSeparation)).as("HCM-LIFE-034 the separation waits for the day after the last working day").isEqualTo("PENDING");
                assertThat(rows("select effective_date from personnel_commands where personnel_command_id = ?", firstSeparation.value()).get(0).get("effective_date").toString())
                        .as("HCM-LIFE-034 the separation takes effect the day after the last working day").isEqualTo(AFTER_N036.toString());
                assertThat(employmentOn(person, N036)).as("HCM-LIFE-034 still in service until the last working day").contains(EmploymentStatus.EMPLOYED);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });
        onDay(N036);
        runDueJob();
        node("HCM-LIFE-036", () -> {
            assertThat(employmentOn(person, N036)).as("HCM-LIFE-036 12-31 is still inside the relationship").contains(EmploymentStatus.EMPLOYED);
        });
        onDay(AFTER_N036);
        runDueJob();
        node("HCM-LIFE-036/next-day", () -> {
            try {
                assertThat(commandStatus(firstSeparation)).as("HCM-LIFE-036 the separation is effective the next day").isEqualTo("EFFECTIVE");
                assertThat(employmentOn(person, AFTER_N036)).as("HCM-LIFE-036 the next day is outside the relationship").contains(EmploymentStatus.SEPARATED);
                assertThat(employmentOn(person, N036)).as("HCM-LIFE-036 12-31 stays inside").contains(EmploymentStatus.EMPLOYED);
                assertThat(eventsOf("EmployeeSeparated", firstSeparation)).as("HCM-LIFE-036 one EmployeeSeparated event").isEqualTo(1);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });

        // second round: the same employee comes back (the existing REINSTATE; HCM-LIFE-049 itself waits for #1372)
        onDay(SECOND_ROUND_START);
        PersonnelCommandId back = reinstate(person, SECOND_ROUND_START, nanjingUnit, secondRound, EmploymentPhaseKind.REGULAR);
        assertThat(commandStatus(back)).as("setup: the second round starts").isEqualTo("EFFECTIVE");

        // HCM-LIFE-068 second round: agreed ending entered with the last working day in the leap year; HCM-LIFE-070 it ends after 02-29
        onDay(N068);
        PersonnelCommandId secondSeparation = separate(person, N070);
        node("HCM-LIFE-068", () -> {
            try {
                assertThat(commandStatus(secondSeparation)).as("HCM-LIFE-068 the second separation is pending").isEqualTo("PENDING");
                assertThat(rows("select effective_date from personnel_commands where personnel_command_id = ?", secondSeparation.value()).get(0).get("effective_date").toString())
                        .as("HCM-LIFE-068 effective the day after the leap-day last working day").isEqualTo(AFTER_N070.toString());
                assertThat(rows("select count(*) as n from personnel_commands where employee_id = ? and command_kind = 'SEPARATE'", stored(person)).get(0).get("n"))
                        .as("HCM-LIFE-068 the second round has its own separation command").isEqualTo(2L);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });
        onDay(N070);
        runDueJob();
        node("HCM-LIFE-070/last-day", () -> assertThat(employmentOn(person, N070)).as("HCM-LIFE-070 02-29 is still inside the relationship").contains(EmploymentStatus.EMPLOYED));
        onDay(AFTER_N070);
        runDueJob();
        node("HCM-LIFE-070/next-day", () -> {
            try {
                assertThat(employmentOn(person, AFTER_N070)).as("HCM-LIFE-070 03-01 is outside the relationship").contains(EmploymentStatus.SEPARATED);
                assertThat(employeeCount()).as("HCM-LIFE-070 one employee").isEqualTo(1);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });

        // HCM-LIFE-078 final reconciliation of the command chain (partial: no pay, expense, receivable or leave balances)
        onDay(N078);
        node("HCM-LIFE-078", () -> {
            try {
                assertThat(employeeCount()).as("HCM-LIFE-078 one person, one employee").isEqualTo(1);
                assertThat(employmentOn(person, N078)).as("HCM-LIFE-078 nobody in service at the end").contains(EmploymentStatus.SEPARATED);
                List<Map<String, Object>> chain = rows("select command_kind, status from personnel_commands where employee_id = ? order by effective_date, created_at", stored(person));
                assertThat(chain).as("HCM-LIFE-078 every command of the chain is effective").allMatch(row -> "EFFECTIVE".equals(row.get("status")));
                assertThat(chain.stream().map(row -> row.get("command_kind")).toList()).as("HCM-LIFE-078 the chain in order")
                        .containsExactly("HIRE", "REGULARIZE", "TRANSFER", "UNPAID_LEAVE_START", "UNPAID_LEAVE_END", "TRANSFER", "SEPARATE", "REINSTATE", "SEPARATE");
                assertThat(inForceOn(ASSIGNMENTS, person, N023.minusDays(1))).as("HCM-LIFE-078 the first round's primary post is traceable").isPresent();
                assertThat(inForceOn(ASSIGNMENTS, person, SECOND_ROUND_START)).as("HCM-LIFE-078 the second round's primary post is traceable").isPresent();
                assertThat(inForceOn(ASSIGNMENTS, person, N078)).as("HCM-LIFE-078 no primary post at the end").isEmpty();
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        });

        report.forEach(line -> System.out.println("GOLDEN-NODE " + line));
        assertThat(failed).as("nodes whose actual differs from the pack (list them in the PR table, do not commit them red)").isEmpty();
    }
}
