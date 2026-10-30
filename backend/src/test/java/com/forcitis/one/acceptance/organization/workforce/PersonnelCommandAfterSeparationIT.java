package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #871 IMP-1030-PAR-B2 (ADR-0068 item 1.5, lines 55 to 57): while an employee has a separation not covered by a reinstatement, nothing
 * dated later than it may be entered except a reinstatement, and a reinstatement needs a separation that has already taken effect and
 * is dated later than it. A refused command leaves no row.
 */
class PersonnelCommandAfterSeparationIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(PersonnelCommandAfterSeparationIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private interface Entry {
        void run() throws Exception;
    }

    private void refused(UUID employee, Entry entry) throws Exception {
        long before = commandCount(employee);
        assertThatThrownBy(entry::run).isInstanceOf(DomainException.class);
        assertThat(commandCount(employee)).as("a refused command leaves no row").isEqualTo(before);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 5 项（行 55）：离职已生效且未复职时，除复职外不得再录生效日更晚的命令（调岗、转正、停薪留职开始、再次离职）；被拒的命令不留行")
    void afterAnEffectiveSeparationNothingLaterThanItCanBeEntered() throws Exception {
        UUID employee = employed(REGULAR);
        UUID elsewhere = newUnit();
        separate(employee, LocalDate.of(2026, 10, 20));

        refused(employee, () -> transfer(employee, TODAY, elsewhere));
        refused(employee, () -> enter(employee, REGULARIZE, TODAY.plusDays(1)));
        refused(employee, () -> enter(employee, UNPAID_LEAVE_START, TODAY));
        refused(employee, () -> separate(employee, TODAY.plusDays(30)));
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 5 项（行 55 至 57）：离职已生效后只有生效日更晚的复职被接受；同日复职被拒；复职生效后可照常录调岗和再次离职")
    void onlyALaterReinstatementIsAcceptedAndThenTheEmployeeIsOrdinaryAgain() throws Exception {
        UUID employee = employed(REGULAR);
        UUID back = newUnit();
        UUID position = newPosition();
        UUID separation = separate(employee, LocalDate.of(2026, 10, 20));
        assertThat(command(separation).get("effective_date")).isEqualTo(LocalDate.of(2026, 10, 21));

        refused(employee, () -> reinstate(employee, LocalDate.of(2026, 10, 21), back, position));
        UUID reinstatement = reinstate(employee, LocalDate.of(2026, 11, 1), back, position);

        assertThat(command(reinstatement).get("status")).isEqualTo(EFFECTIVE);
        assertThat(eventsOf(EVENT_REINSTATED, reinstatement)).isEqualTo(1);
        assertThat(inForceOn(currentVersions(EMPLOYEE_VERSIONS, employee), TODAY).get("employment_status")).isEqualTo(EMPLOYED);
        assertThat(transfer(employee, TODAY.plusDays(3), newUnit())).as("ordinary commands again after the reinstatement").isNotNull();
        assertThat(separate(employee, TODAY.plusDays(40))).as("and a second separation").isNotNull();
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 5 项（行 55、57）：离职还在待生效时，同样不得录更晚的命令；复职要求离职已生效，离职未生效时复职被拒；生效日早于该离职的命令不受限")
    void aPendingSeparationBlocksLaterCommandsAndCannotYetBeReinstated() throws Exception {
        UUID employee = employed(REGULAR);
        UUID back = newUnit();
        UUID position = newPosition();
        LocalDate separationDay = TODAY.plusDays(40);
        separate(employee, separationDay.minusDays(1));

        refused(employee, () -> transfer(employee, separationDay.plusDays(5), back));
        refused(employee, () -> reinstate(employee, separationDay.plusDays(5), back, position));
        assertThat(transfer(employee, separationDay.minusDays(10), back)).as("dated before the separation").isNotNull();
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 5 项（行 57）：没有离职的在职员工不能复职")
    void aReinstatementNeedsASeparation() throws Exception {
        UUID employee = employed(REGULAR);
        UUID back = newUnit();
        UUID position = newPosition();

        refused(employee, () -> reinstate(employee, TODAY, back, position));
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 5 项（行 55）：已撤回的离职不算数，撤回后可照常录更晚的命令")
    void aWithdrawnSeparationNoLongerBlocks() throws Exception {
        UUID employee = employed(REGULAR);
        UUID elsewhere = newUnit();
        UUID separation = separate(employee, TODAY.plusDays(20));
        refused(employee, () -> transfer(employee, TODAY.plusDays(30), elsewhere));

        commands().withdraw(separation, "synthetic: entered by mistake");

        assertThat(transfer(employee, TODAY.plusDays(30), elsewhere)).isNotNull();
    }
}
