package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** IMP-1030-PAR-B1: the held-separation table and the workforce state transition log, as the database enforces them (ADR-0068 1.5, 2.1; ADR-0058 d8). */
class SeparationHoldTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String EMPLOYEE = "'019a0000-0000-7000-8000-000000000001'";
    static final String OTHER_EMPLOYEE = "'019a0000-0000-7000-8000-000000000002'";
    static final String USER = "'019a0000-0000-7000-8000-0000000000aa'";
    static final String COMMAND = "'019a0000-0000-7000-8000-0000000000c1'";
    static final String HOLD = "'019a0000-0000-7000-8000-0000000000e1'";
    static final String OTHER_HOLD = "'019a0000-0000-7000-8000-0000000000e2'";
    static final String OPERATOR_ROLE = "b1_runtime";

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void employeesAndACommand() {
        VersionTables.truncate(jdbc, "truncate table organization_workforce.state_transitions");
        for (String id : new String[] {EMPLOYEE, OTHER_EMPLOYEE}) {
            jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values ("
                    + id + ", 'E-' || right(" + id + ", 2), 'name', 'EMPLOYED')").update();
        }
        jdbc.sql("insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status,"
                + " created_at, created_by, updated_at) values (" + COMMAND + ", " + EMPLOYEE + ", 'SEPARATE', date '2026-11-01', 'HELD', now(), " + USER + ", now())").update();
    }

    private void hold(String id, String commandId, String employeeId, String status, String releasedBy, String reason, String channel) {
        jdbc.sql("insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status, released_at, released_by,"
                + " release_reason, release_channel, created_at, updated_at) values (" + id + ", " + commandId + ", " + employeeId + ", '" + status + "', "
                + (status.equals("RELEASED") ? "now()" : "null") + ", " + releasedBy + ", " + (reason == null ? "null" : "'" + reason + "'") + ", "
                + (channel == null ? "null" : "'" + channel + "'") + ", now(), now())").update();
    }

    private void heldHold() {
        hold(HOLD, COMMAND, EMPLOYEE, "HELD", "null", null, null);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 a command has at most one hold, and the hold's employee is the command's employee")
    void holdIsBoundToItsCommand() {
        heldHold();
        assertThatThrownBy(() -> hold(OTHER_HOLD, COMMAND, EMPLOYEE, "HELD", "null", null, null)).hasMessageContaining("uk_separation_holds_personnel_command_id");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 a hold whose employee differs from its command's employee is refused by the composite foreign key")
    void holdEmployeeMustMatchCommand() {
        assertThatThrownBy(() -> hold(HOLD, COMMAND, OTHER_EMPLOYEE, "HELD", "null", null, null)).hasMessageContaining("fk_separation_holds_command");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 the release columns come together: released status needs released_at, and reason, channel and time are all set or all empty")
    void releaseColumnsComeTogether() {
        assertThatThrownBy(() -> hold(HOLD, COMMAND, EMPLOYEE, "RELEASED", "null", null, null)).hasMessageContaining("ck_separation_holds_release_columns");
        assertThatThrownBy(() -> jdbc.sql("insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status,"
                + " released_at, created_at, updated_at) values (" + HOLD + ", " + COMMAND + ", " + EMPLOYEE + ", 'RELEASED', null, now(), now())").update())
                .hasMessageContaining("ck_separation_holds_released");
        assertThatThrownBy(() -> jdbc.sql("insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status,"
                + " released_at, release_reason, created_at, updated_at) values (" + HOLD + ", " + COMMAND + ", " + EMPLOYEE + ", 'RELEASED', now(), 'ok', now(), now())").update())
                .hasMessageContaining("ck_separation_holds_release_columns");
        assertThatThrownBy(() -> hold(HOLD, COMMAND, EMPLOYEE, "HELD", "null", "early", "OPERATIONS_COMMAND")).hasMessageContaining("ck_separation_holds_release_columns");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 an interface release names the releasing account; an operations command release has none; the channel is one of the two")
    void releasedByFollowsTheChannel() {
        assertThatThrownBy(() -> hold(HOLD, COMMAND, EMPLOYEE, "RELEASED", "null", "approved", "INTERFACE")).hasMessageContaining("ck_separation_holds_released_by");
        assertThatThrownBy(() -> hold(HOLD, COMMAND, EMPLOYEE, "RELEASED", USER, "approved", "OPERATIONS_COMMAND")).hasMessageContaining("ck_separation_holds_released_by");
        assertThatThrownBy(() -> hold(HOLD, COMMAND, EMPLOYEE, "RELEASED", USER, "approved", "MANUAL")).hasMessageContaining("ck_separation_holds_release_channel");
        hold(HOLD, COMMAND, EMPLOYEE, "RELEASED", USER, "approved", "INTERFACE");
        assertThat(count("separation_holds")).isEqualTo(1);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 an operations command release stores no account")
    void operationsReleaseHasNoAccount() {
        hold(HOLD, COMMAND, EMPLOYEE, "RELEASED", "null", "approved", "OPERATIONS_COMMAND");
        assertThat(jdbc.sql("select released_by from organization_workforce.separation_holds").query(String.class).single()).isNull();
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 a release reason is 1 to 500 characters and not blank; the status is one of three")
    void reasonAndStatusAreBounded() {
        assertThatThrownBy(() -> hold(HOLD, COMMAND, EMPLOYEE, "RELEASED", USER, "   ", "INTERFACE")).hasMessageContaining("ck_separation_holds_release_reason");
        assertThatThrownBy(() -> hold(HOLD, COMMAND, EMPLOYEE, "RELEASED", USER, "x".repeat(501), "INTERFACE")).hasMessageContaining("ck_separation_holds_release_reason");
        assertThatThrownBy(() -> hold(HOLD, COMMAND, EMPLOYEE, "PENDING", "null", null, null)).hasMessageContaining("ck_separation_holds_status");
        hold(HOLD, COMMAND, EMPLOYEE, "RELEASED", USER, "x".repeat(500), "INTERFACE");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 a hold moves only from HELD to RELEASED or WITHDRAWN, release columns are set once, nothing else changes")
    void holdsAreAppendOnly() {
        heldHold();
        String where = " where separation_hold_id = " + HOLD;
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.separation_holds set personnel_command_id = gen_random_uuid()" + where).update())
                .hasMessageContaining("only the status and release columns");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.separation_holds set employee_id = " + OTHER_EMPLOYEE + where).update())
                .hasMessageContaining("only the status and release columns");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.separation_holds set created_at = now() - interval '1 day'" + where).update())
                .hasMessageContaining("only the status and release columns");

        assertThat(jdbc.sql("update organization_workforce.separation_holds set status = 'RELEASED', released_at = now(), released_by = " + USER
                + ", release_reason = 'approved', release_channel = 'INTERFACE', updated_at = now(), version = version + 1" + where).update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.separation_holds set release_reason = 'changed'" + where).update()).hasMessageContaining("is set once");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.separation_holds set status = 'HELD', released_at = null, released_by = null,"
                + " release_reason = null, release_channel = null" + where).update()).hasMessageContaining("cannot move");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.separation_holds set status = 'WITHDRAWN'" + where).update()).hasMessageContaining("cannot move");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 a held hold can be withdrawn")
    void heldHoldCanBeWithdrawn() {
        heldHold();
        assertThat(jdbc.sql("update organization_workforce.separation_holds set status = 'WITHDRAWN', updated_at = now(), version = version + 1"
                + " where separation_hold_id = " + HOLD).update()).isEqualTo(1);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 the runtime account cannot delete or truncate holds or transitions; the table owner may delete")
    void deleteAndTruncateAreOwnerOnly() throws SQLException {
        heldHold();
        transition("PersonnelCommand", COMMAND, 1, null, "HELD");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = '" + OPERATOR_ROLE + "') then create role " + OPERATOR_ROLE + "; end if; end $$");
            s.execute("grant usage on schema organization_workforce to " + OPERATOR_ROLE);
            s.execute("grant select, insert, update, delete, truncate on organization_workforce.separation_holds, organization_workforce.state_transitions to " + OPERATOR_ROLE);
            s.execute("set role " + OPERATOR_ROLE);
            try {
                for (String statement : List.of("delete from organization_workforce.separation_holds", "truncate organization_workforce.separation_holds",
                        "delete from organization_workforce.state_transitions", "truncate organization_workforce.state_transitions")) {
                    assertThatThrownBy(() -> s.execute(statement)).as(statement).hasMessageContaining("append-only");
                }
            } finally {
                s.execute("reset role");
            }
        }
        assertThat(jdbc.sql("delete from organization_workforce.separation_holds").update()).isEqualTo(1);
    }

    private void transition(String type, String aggregateId, long sequence, String from, String to) {
        jdbc.sql("insert into organization_workforce.state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status,"
                + " occurred_on_date, recorded_at, actor_type, actor_id, cause_kind, command_id, correlation_id, created_at, updated_at) values (gen_random_uuid(), '"
                + type + "', " + aggregateId + ", " + sequence + ", " + (from == null ? "null" : "'" + from + "'") + ", '" + to + "', date '2026-11-01', timestamptz '2026-11-01 08:00+08', 'USER', "
                + USER + ", 'COMMAND', gen_random_uuid(), gen_random_uuid(), timestamptz '2026-11-01 08:00+08', timestamptz '2026-11-01 08:00+08')").update();
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 a personnel command's transitions are the pairs of its state machine, a separation hold's are HELD then RELEASED or WITHDRAWN")
    void legalTransitions() {
        long sequence = 0;
        for (String[] pair : new String[][] {{null, "PENDING"}, {null, "HELD"}, {null, "EFFECTIVE"}, {"PENDING", "EFFECTIVE"}, {"PENDING", "WITHDRAWN"},
                {"PENDING", "HELD"}, {"HELD", "EFFECTIVE"}, {"HELD", "WITHDRAWN"}}) {
            transition("PersonnelCommand", COMMAND, ++sequence, pair[0], pair[1]);
        }
        for (String[] pair : new String[][] {{null, "HELD"}, {"HELD", "RELEASED"}, {"HELD", "WITHDRAWN"}}) {
            transition("SeparationHold", HOLD, ++sequence, pair[0], pair[1]);
        }
        assertThat(count("state_transitions")).isEqualTo(11);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 a transition outside the pairs of its type is refused, and so is an unknown type")
    void illegalTransitions() {
        for (String[] pair : new String[][] {{null, "WITHDRAWN"}, {null, "RELEASED"}, {"PENDING", "RELEASED"}, {"EFFECTIVE", "PENDING"}, {"WITHDRAWN", "PENDING"}, {"HELD", "PENDING"}}) {
            assertThatThrownBy(() -> transition("PersonnelCommand", COMMAND, 2, pair[0], pair[1])).as("PersonnelCommand " + pair[0] + " to " + pair[1]).isInstanceOf(Exception.class);
        }
        for (String[] pair : new String[][] {{null, "PENDING"}, {null, "RELEASED"}, {null, "EFFECTIVE"}, {"HELD", "EFFECTIVE"}, {"RELEASED", "HELD"}, {"PENDING", "HELD"}}) {
            assertThatThrownBy(() -> transition("SeparationHold", HOLD, 2, pair[0], pair[1])).as("SeparationHold " + pair[0] + " to " + pair[1]).isInstanceOf(Exception.class);
        }
        assertThatThrownBy(() -> transition("Employee", COMMAND, 1, null, "HELD")).hasMessageContaining("ck_state_transitions_aggregate_type_code");
        assertThatThrownBy(() -> transition("PersonnelCommand", COMMAND, 1, null, "HELD".concat("X"))).hasMessageContaining("ck_state_transitions_to_status");
        assertThatThrownBy(() -> transition("PersonnelCommand", COMMAND, 2, "SUSPENDED", "HELD")).hasMessageContaining("ck_state_transitions_from_status");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 a holder's sequence number is unique, from and to differ, and a command cause needs a command id")
    void sequenceAndCauseRules() {
        transition("PersonnelCommand", COMMAND, 1, null, "PENDING");
        assertThatThrownBy(() -> transition("PersonnelCommand", COMMAND, 1, "PENDING", "HELD"))
                .hasMessageContaining("uk_state_transitions_aggregate_id_transition_sequence_number");
        assertThatThrownBy(() -> transition("PersonnelCommand", COMMAND, 2, "PENDING", "PENDING")).hasMessageContaining("ck_state_transitions_statuses_differ");
        assertThatThrownBy(() -> jdbc.sql("insert into organization_workforce.state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number,"
                + " from_status, to_status, occurred_on_date, recorded_at, actor_type, actor_id, cause_kind, correlation_id, created_at, updated_at) values (gen_random_uuid(),"
                + " 'PersonnelCommand', " + COMMAND + ", 2, 'PENDING', 'HELD', date '2026-11-01', timestamptz '2026-11-01 08:00+08', 'USER', " + USER + ", 'COMMAND',"
                + " gen_random_uuid(), timestamptz '2026-11-01 08:00+08', timestamptz '2026-11-01 08:00+08')").update()).hasMessageContaining("ck_state_transitions_trigger");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B1 a transition is never updated")
    void transitionsAreAppendOnly() {
        transition("PersonnelCommand", COMMAND, 1, null, "PENDING");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.state_transitions set to_status = 'HELD'").update()).hasMessageContaining("append-only");
    }
}
