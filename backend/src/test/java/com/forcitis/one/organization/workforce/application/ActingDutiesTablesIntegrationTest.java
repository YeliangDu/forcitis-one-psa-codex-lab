package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * #1536 (slice 1 of #1525, the signed workforce-acting-duty data design record): the acting duty table and the acting columns of the personnel commands
 * refuse every row the design says is wrong, and take every row it says is right.
 */
class ActingDutiesTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String DUTIES = "organization_workforce.acting_duties";
    static final String COMMANDS = "organization_workforce.personnel_commands";
    static final LocalDate START = LocalDate.of(2026, 12, 1);
    static final LocalDate END = LocalDate.of(2026, 12, 11);

    @Autowired
    DataSource dataSource;

    PersonnelSeedSupport.Person person(String code) {
        return PersonnelSeedSupport.employed(jdbc, clock, code);
    }

    /** An effective ACTING_START command of the person; the extra column / value pairs are appended to the base columns. */
    UUID startCommand(PersonnelSeedSupport.Person who, LocalDate day, LocalDate endsBefore, UUID unit, String... more) {
        UUID id = Uuids.timeOrdered(clock);
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < more.length; i += 2) {
            columns.append(", ").append(more[i]);
            values.append(", ").append(more[i + 1]);
        }
        jdbc.sql("insert into " + COMMANDS + " (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at,"
                + " applied_at, acting_covered_org_unit_id, acting_ends_before_date, reason" + columns + ") values (:id, :relationship, :employee, 'ACTING_START', :day, 'EFFECTIVE', now(), :by, now(),"
                + " now(), :unit, :ends, 'cover while away'" + values + ")")
                .param("id", id).param("relationship", EmploymentRelationshipSeed.relationship(jdbc, who.employee())).param("employee", who.employee())
                .param("day", day).param("by", Uuids.timeOrdered(clock)).param("unit", unit).param("ends", endsBefore).update();
        return id;
    }

    /** A duty of the person for the unit (and optional position) over [starts, endsBefore); the command is made on the way. */
    UUID duty(PersonnelSeedSupport.Person who, UUID unit, UUID position, LocalDate starts, LocalDate endsBefore) {
        UUID command = position == null ? startCommand(who, starts, endsBefore, unit)
                : startCommand(who, starts, endsBefore, unit, "acting_covered_position_id", "'" + position + "'");
        return insertDuty(who, unit, position, starts, endsBefore, command, null);
    }

    UUID insertDuty(PersonnelSeedSupport.Person who, UUID unit, UUID position, LocalDate starts, LocalDate endsBefore, UUID command, UUID covered) {
        UUID id = Uuids.timeOrdered(clock);
        jdbc.sql("insert into " + DUTIES + " (acting_duty_id, employment_relationship_id, employee_id, covered_org_unit_id, covered_position_id, covered_employee_id, starts_on_date, ends_before_date,"
                + " reason, source_personnel_command_id, created_at, created_by, updated_at) values (:id, :relationship, :employee, :unit, :position, :covered, :starts, :ends,"
                + " 'cover while away', :command, now(), :by, now())")
                .param("id", id).param("relationship", EmploymentRelationshipSeed.relationship(jdbc, who.employee())).param("employee", who.employee()).param("unit", unit)
                .param("position", position).param("covered", covered).param("starts", starts).param("ends", endsBefore).param("command", command)
                .param("by", Uuids.timeOrdered(clock)).update();
        return id;
    }

    UUID otherUnit() {
        return PersonnelSeedSupport.orgUnit(jdbc, clock);
    }

    long rows(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }

    @Test
    @DisplayName("#1536 a valid duty is stored open, with and without a covered post and person; a duty of one person for two different units is allowed")
    void aValidDutyIsStored() {
        PersonnelSeedSupport.Person who = person("AD-1");
        PersonnelSeedSupport.Person covered = person("AD-2");

        duty(who, covered.orgUnit(), null, START, END);
        UUID withPost = insertDuty(who, covered.orgUnit(), covered.position(), START, END, startCommand(who, START, END, covered.orgUnit()), covered.employee());
        duty(who, otherUnit(), null, START, END);

        assertThat(rows(DUTIES)).isEqualTo(3);
        assertThat(jdbc.sql("select ended_on_date is null from " + DUTIES + " where acting_duty_id = :id").param("id", withPost).query(Boolean.class).single()).isTrue();
    }

    @Test
    @DisplayName("#1536 the period is at least one day, and a row with an end day but no cause (or the reverse) is refused")
    void periodAndClosingPairAreChecked() {
        PersonnelSeedSupport.Person who = person("AD-3");
        UUID unit = otherUnit();

        UUID command = startCommand(who, START, END, unit);
        assertThatThrownBy(() -> insertDuty(who, unit, null, START, START, command, null)).hasMessageContaining("ck_acting_duties_period");
        assertThatThrownBy(() -> insertDuty(who, unit, null, END, START, command, null)).hasMessageContaining("ck_acting_duties_period");
        UUID open = insertDuty(who, unit, null, START, END, command, null);
        assertThatThrownBy(() -> jdbc.sql("update " + DUTIES + " set ended_on_date = date '2026-12-05' where acting_duty_id = :id").param("id", open).update())
                .hasMessageContaining("ck_acting_duties_end_pair");
        assertThatThrownBy(() -> jdbc.sql("update " + DUTIES + " set end_cause_kind = 'EXPIRED' where acting_duty_id = :id").param("id", open).update())
                .hasMessageContaining("ck_acting_duties_end_pair");
        assertThat(rows(DUTIES)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1536 a closing needs a known cause, an end day inside the period (a separation may close on the start day), and once closed the row never changes")
    void closingIsCheckedAndFinal() {
        PersonnelSeedSupport.Person who = person("AD-4");
        UUID id = duty(who, otherUnit(), null, START, END);

        assertThatThrownBy(() -> close(id, "'2026-12-05'", "'GONE'")).hasMessageContaining("ck_acting_duties_end_cause");
        assertThatThrownBy(() -> close(id, "'2026-12-12'", "'EXPIRED'")).hasMessageContaining("ck_acting_duties_ended_range");
        assertThatThrownBy(() -> close(id, "'2026-12-01'", "'ENDED_EARLY'")).hasMessageContaining("ck_acting_duties_ended_range");
        close(id, "'2026-12-05'", "'ENDED_EARLY'");

        assertThatThrownBy(() -> jdbc.sql("update " + DUTIES + " set end_cause_kind = 'EXPIRED' where acting_duty_id = :id").param("id", id).update())
                .hasMessageContaining("has ended and cannot change");
        assertThatThrownBy(() -> jdbc.sql("update " + DUTIES + " set ends_before_date = date '2026-12-31' where acting_duty_id = :id").param("id", id).update())
                .hasMessageContaining("has ended and cannot change");

        UUID sameDay = duty(who, otherUnit(), null, START, END);
        close(sameDay, "'2026-12-01'", "'SEPARATION'");
        assertThat(jdbc.sql("select ended_on_date from " + DUTIES + " where acting_duty_id = :id").param("id", sameDay).query(LocalDate.class).single()).isEqualTo(START);
    }

    private void close(UUID id, String endedOn, String cause) {
        jdbc.sql("update " + DUTIES + " set ended_on_date = " + endedOn + ", end_cause_kind = " + cause + ", version = version + 1, updated_at = now() where acting_duty_id = :id").param("id", id).update();
    }

    @Test
    @DisplayName("#1536 two duties of one person for the same unit overlap-refuse; back-to-back and a closed-early duty do not; a unit-head duty and a post duty are separate shapes")
    void overlapIsRefusedPerCoveredPost() {
        PersonnelSeedSupport.Person who = person("AD-5");
        PersonnelSeedSupport.Person other = person("AD-6");
        UUID unit = otherUnit();
        UUID first = duty(who, unit, null, START, END);

        assertThatThrownBy(() -> duty(who, unit, null, START.plusDays(5), END.plusDays(5))).hasMessageContaining("ex_acting_duties_unit_head_overlap");
        duty(who, unit, null, END, END.plusDays(3));
        duty(other, unit, null, START, END);
        duty(who, unit, other.position(), START, END);
        assertThatThrownBy(() -> duty(who, unit, other.position(), START.plusDays(1), END)).hasMessageContaining("ex_acting_duties_position_overlap");

        close(first, "'2026-12-04'", "'ENDED_EARLY'");
        duty(who, unit, null, START.plusDays(4), START.plusDays(8));
        assertThat(rows(DUTIES)).isEqualTo(5);
    }

    @Test
    @DisplayName("#1536 a person cannot cover themself, in the duty and in the command")
    void selfCoverIsRefused() {
        PersonnelSeedSupport.Person who = person("AD-7");
        UUID unit = otherUnit();

        assertThatThrownBy(() -> insertDuty(who, unit, null, START, END, startCommand(who, START, END, unit), who.employee())).hasMessageContaining("ck_acting_duties_not_self");
        assertThatThrownBy(() -> startCommand(who, START, END, unit, "acting_covered_employee_id", "'" + who.employee() + "'")).hasMessageContaining("ck_personnel_commands_acting_not_self");
    }

    @Test
    @DisplayName("#1536 the trigger refuses to change the start, the post, the person or the reason of an open duty, and a shorter end; it takes a longer end")
    void anOpenDutyOnlyGrowsOrCloses() {
        PersonnelSeedSupport.Person who = person("AD-8");
        UUID id = duty(who, otherUnit(), null, START, END);

        assertThatThrownBy(() -> jdbc.sql("update " + DUTIES + " set starts_on_date = date '2026-12-02' where acting_duty_id = :id").param("id", id).update())
                .hasMessageContaining("only ends_before_date, ended_on_date and end_cause_kind");
        assertThatThrownBy(() -> jdbc.sql("update " + DUTIES + " set reason = 'changed' where acting_duty_id = :id").param("id", id).update())
                .hasMessageContaining("only ends_before_date, ended_on_date and end_cause_kind");
        assertThatThrownBy(() -> jdbc.sql("update " + DUTIES + " set covered_org_unit_id = :unit where acting_duty_id = :id").param("unit", otherUnit()).param("id", id).update())
                .hasMessageContaining("only ends_before_date, ended_on_date and end_cause_kind");
        assertThatThrownBy(() -> jdbc.sql("update " + DUTIES + " set ends_before_date = date '2026-12-05' where acting_duty_id = :id").param("id", id).update())
                .hasMessageContaining("can only grow");

        jdbc.sql("update " + DUTIES + " set ends_before_date = date '2026-12-20', version = version + 1, updated_at = now() where acting_duty_id = :id").param("id", id).update();
        assertThatThrownBy(() -> jdbc.sql("update " + DUTIES + " set ends_before_date = date '2026-12-25', ended_on_date = date '2026-12-20', end_cause_kind = 'EXPIRED' where acting_duty_id = :id")
                .param("id", id).update()).hasMessageContaining("without changing ends_before_date");
        close(id, "'2026-12-20'", "'EXPIRED'");
        assertThat(jdbc.sql("select end_cause_kind from " + DUTIES + " where acting_duty_id = :id").param("id", id).query(String.class).single()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("#1536 a duty cannot be inserted already closed, truncated, or deleted by a role that does not own the table")
    void insertDeleteAndTruncateAreGuarded() throws SQLException {
        PersonnelSeedSupport.Person who = person("AD-9");
        UUID unit = otherUnit();
        UUID command = startCommand(who, START, END, unit);

        assertThatThrownBy(() -> jdbc.sql("insert into " + DUTIES + " (acting_duty_id, employment_relationship_id, employee_id, covered_org_unit_id, starts_on_date, ends_before_date, ended_on_date,"
                + " end_cause_kind, reason, source_personnel_command_id, created_at, created_by, updated_at) select gen_random_uuid(), employment_relationship_id, employee_id, :unit, :s, :e,"
                + " :ended, 'EXPIRED', 'x', :command, now(), gen_random_uuid(), now() from " + COMMANDS + " where personnel_command_id = :command")
                .param("unit", unit).param("s", START).param("e", END).param("ended", END).param("command", command).update()).hasMessageContaining("inserted open");
        duty(who, unit, null, START, END);
        assertThatThrownBy(() -> jdbc.sql("truncate table " + DUTIES + " cascade").update()).hasMessageContaining("acting duties cannot be truncated");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'acting_duty_reader') then create role acting_duty_reader; end if; end $$");
            statement.execute("grant usage on schema organization_workforce to acting_duty_reader");
            statement.execute("grant select, delete on " + DUTIES + " to acting_duty_reader");
            statement.execute("set role acting_duty_reader");
            try {
                assertThatThrownBy(() -> statement.execute("delete from " + DUTIES)).hasMessageContaining("delete on");
            } finally {
                statement.execute("reset role");
            }
        }
        assertThat(rows(DUTIES)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1536 one command makes at most one duty, and the duty's command must be the same person's")
    void aCommandMakesOneDutyOfItsOwnPerson() {
        PersonnelSeedSupport.Person who = person("AD-10");
        PersonnelSeedSupport.Person other = person("AD-11");
        UUID unit = otherUnit();
        UUID command = startCommand(who, START, END, unit);
        insertDuty(who, unit, null, START, END, command, null);

        assertThatThrownBy(() -> insertDuty(who, unit, null, END, END.plusDays(2), command, null)).hasMessageContaining("uk_acting_duties_source_command");
        UUID second = startCommand(who, END, END.plusDays(2), unit);
        assertThatThrownBy(() -> insertDuty(other, unit, null, START, END, second, null)).hasMessageContaining("fk_acting_duties_source_command");
    }

    @Test
    @DisplayName("#1536 an unknown command kind is refused; each acting kind needs its own columns and no others, and a plain kind carries none of them")
    void commandColumnsFollowTheKind() {
        PersonnelSeedSupport.Person who = person("AD-12");
        UUID unit = otherUnit();
        UUID duty = duty(who, unit, null, START, END);

        assertThatThrownBy(() -> command(who, "ACTING_PAUSE", "EFFECTIVE", START)).hasMessageContaining("ck_personnel_commands_command_kind");
        assertThatThrownBy(() -> command(who, "ACTING_START", "PENDING", START, "reason", "'x'", "acting_ends_before_date", "date '2026-12-10'")).hasMessageContaining("ck_personnel_commands_acting_columns");
        assertThatThrownBy(() -> command(who, "ACTING_START", "PENDING", START, "acting_covered_org_unit_id", "'" + unit + "'", "acting_ends_before_date", "date '2026-12-10'"))
                .hasMessageContaining("ck_personnel_commands_acting_columns");
        assertThatThrownBy(() -> command(who, "ACTING_START", "PENDING", START, "acting_covered_org_unit_id", "'" + unit + "'", "reason", "'x'", "acting_ends_before_date", "date '2026-12-01'"))
                .hasMessageContaining("ck_personnel_commands_acting_columns");
        assertThatThrownBy(() -> command(who, "ACTING_END", "PENDING", START, "reason", "'x'")).hasMessageContaining("ck_personnel_commands_acting_columns");
        assertThatThrownBy(() -> command(who, "ACTING_END", "PENDING", START, "target_acting_duty_id", "'" + duty + "'", "reason", "'x'", "acting_ends_before_date", "date '2026-12-10'"))
                .hasMessageContaining("ck_personnel_commands_acting_columns");
        assertThatThrownBy(() -> command(who, "ACTING_EXTEND", "EFFECTIVE", START, "target_acting_duty_id", "'" + duty + "'", "reason", "'x'"))
                .hasMessageContaining("ck_personnel_commands_acting_columns");
        assertThatThrownBy(() -> command(who, "REGULARIZE", "PENDING", START, "reason", "'x'")).hasMessageContaining("ck_personnel_commands_acting_columns");
        assertThatThrownBy(() -> command(who, "ACTING_END", "PENDING", START, "target_acting_duty_id", "'" + duty + "'", "reason", "'x'", "target_org_unit_id", "'" + unit + "'"))
                .hasMessageContaining("ck_personnel_commands_acting_no_assignment_targets");
        assertThatThrownBy(() -> command(who, "ACTING_END", "PENDING", START, "target_acting_duty_id", "'" + duty + "'", "reason", "' '")).hasMessageContaining("ck_personnel_commands_reason");

        command(who, "ACTING_END", "PENDING", START.plusDays(2), "target_acting_duty_id", "'" + duty + "'", "reason", "'back early'");
        command(who, "ACTING_EXTEND", "EFFECTIVE", START.plusDays(3), "target_acting_duty_id", "'" + duty + "'", "reason", "'longer'", "acting_ends_before_date", "date '2026-12-20'");
    }

    @Test
    @DisplayName("#1536 an extension is applied when it is entered: a pending ACTING_EXTEND is refused")
    void anExtensionIsNeverPending() {
        PersonnelSeedSupport.Person who = person("AD-13");
        UUID duty = duty(who, otherUnit(), null, START, END);

        assertThatThrownBy(() -> command(who, "ACTING_EXTEND", "PENDING", START, "target_acting_duty_id", "'" + duty + "'", "reason", "'x'", "acting_ends_before_date", "date '2026-12-20'"))
                .hasMessageContaining("ck_personnel_commands_correction_immediate");
    }

    @Test
    @DisplayName("#1536 acting commands do not take the day's one open slot of the relationship (a pending transfer-day command stays allowed), but two pending starts for one post and day, or two pending ends for one duty, are refused")
    void actingCommandsHaveTheirOwnDaySlots() {
        PersonnelSeedSupport.Person who = person("AD-14");
        UUID unit = otherUnit();
        UUID duty = duty(who, unit, null, START, END);
        LocalDate day = LocalDate.of(2027, 1, 10);

        command(who, "REGULARIZE", "PENDING", day);
        command(who, "ACTING_END", "PENDING", day, "target_acting_duty_id", "'" + duty + "'", "reason", "'x'");
        assertThatThrownBy(() -> command(who, "REGULARIZE", "PENDING", day)).hasMessageContaining("uk_personnel_commands_open_per_relationship_day");
        assertThatThrownBy(() -> command(who, "ACTING_END", "PENDING", day.plusDays(1), "target_acting_duty_id", "'" + duty + "'", "reason", "'y'"))
                .hasMessageContaining("uk_personnel_commands_open_acting_end");

        command(who, "ACTING_START", "PENDING", day, "acting_covered_org_unit_id", "'" + unit + "'", "acting_ends_before_date", "date '2027-01-20'", "reason", "'x'");
        assertThatThrownBy(() -> command(who, "ACTING_START", "PENDING", day, "acting_covered_org_unit_id", "'" + unit + "'", "acting_ends_before_date", "date '2027-01-25'", "reason", "'y'"))
                .hasMessageContaining("uk_personnel_commands_open_acting_start_unit");
        command(who, "ACTING_START", "PENDING", day, "acting_covered_org_unit_id", "'" + unit + "'", "acting_covered_position_id", "'" + who.position() + "'",
                "acting_ends_before_date", "date '2027-01-25'", "reason", "'other post'");
        assertThatThrownBy(() -> command(who, "ACTING_START", "PENDING", day, "acting_covered_org_unit_id", "'" + unit + "'", "acting_covered_position_id", "'" + who.position() + "'",
                "acting_ends_before_date", "date '2027-01-30'", "reason", "'again'")).hasMessageContaining("uk_personnel_commands_open_acting_start_position");
    }

    @Test
    @DisplayName("#1536 reason of a duty is 1 to 500 non-blank characters, the period is finite, a command names only the own person's duty, and start and extension carry only their own columns")
    void remainingDefencesOfDutyAndCommandAreRed() {
        PersonnelSeedSupport.Person who = person("AD-16");
        PersonnelSeedSupport.Person other = person("AD-17");
        UUID unit = otherUnit();
        UUID command = startCommand(who, START, END, unit);
        for (String[] bad : new String[][] {{"date '2026-12-10'", "' '"}, {"date '2026-12-10'", "repeat('x', 501)"}}) {
            assertThatThrownBy(() -> insertDutyRaw(who, unit, command, bad[0], bad[1])).hasMessageContaining("ck_acting_duties_reason");
        }
        assertThatThrownBy(() -> insertDutyRaw(who, unit, command, "'infinity'::date", "'x'")).hasMessageContaining("ck_acting_duties_period");

        UUID duty = duty(who, unit, null, START, END);
        assertThatThrownBy(() -> command(other, "ACTING_END", "PENDING", START, "target_acting_duty_id", "'" + duty + "'", "reason", "'not mine'"))
                .hasMessageContaining("fk_personnel_commands_target_acting_duty");
        assertThatThrownBy(() -> startCommand(who, START.plusDays(1), END, unit, "target_acting_duty_id", "'" + duty + "'")).hasMessageContaining("ck_personnel_commands_acting_columns");
        assertThatThrownBy(() -> command(who, "ACTING_EXTEND", "EFFECTIVE", START, "target_acting_duty_id", "'" + duty + "'", "reason", "'x'", "acting_ends_before_date",
                "date '2026-12-20'", "acting_covered_org_unit_id", "'" + unit + "'")).hasMessageContaining("ck_personnel_commands_acting_columns");
    }

    private void insertDutyRaw(PersonnelSeedSupport.Person who, UUID unit, UUID command, String endsBefore, String reason) {
        jdbc.sql("insert into " + DUTIES + " (acting_duty_id, employment_relationship_id, employee_id, covered_org_unit_id, starts_on_date, ends_before_date, reason,"
                + " source_personnel_command_id, created_at, created_by, updated_at) values (:id, :relationship, :employee, :unit, :starts, " + endsBefore + ", " + reason
                + ", :command, now(), :by, now())")
                .param("id", Uuids.timeOrdered(clock)).param("relationship", EmploymentRelationshipSeed.relationship(jdbc, who.employee())).param("employee", who.employee())
                .param("unit", unit).param("starts", START).param("command", command).param("by", Uuids.timeOrdered(clock)).update();
    }

    /** A command of the person; the extra arguments are column / value pairs (SQL literals) added to the base columns. */
    private void command(PersonnelSeedSupport.Person who, String kind, String status, LocalDate day, String... more) {
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < more.length; i += 2) {
            columns.append(", ").append(more[i]);
            values.append(", ").append(more[i + 1]);
        }
        jdbc.sql("insert into " + COMMANDS + " (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at,"
                + " applied_at" + columns + ") values (:id, :relationship, :employee, '" + kind + "', :day, '" + status + "', now(), :by, now(), "
                + (status.equals("EFFECTIVE") ? "now()" : "null") + values + ")")
                .param("id", Uuids.timeOrdered(clock)).param("relationship", EmploymentRelationshipSeed.relationship(jdbc, who.employee())).param("employee", who.employee())
                .param("day", day).param("by", Uuids.timeOrdered(clock)).update();
    }

    @Test
    @DisplayName("#1536 the acting columns of a command are written once like every other column: an update that fills one later is refused")
    void actingColumnsOfACommandAreWriteOnce() {
        PersonnelSeedSupport.Person who = person("AD-15");
        UUID unit = otherUnit();
        UUID command = startCommand(who, START, END, unit);

        assertThatThrownBy(() -> jdbc.sql("update " + COMMANDS + " set acting_ends_before_date = date '2027-02-01' where personnel_command_id = :id").param("id", command).update())
                .hasMessageContaining("only the status and timestamp columns");
    }
}
