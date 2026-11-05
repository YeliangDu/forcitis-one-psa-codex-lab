package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonnelVersionTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String EMPLOYEE = "'019a0000-0000-7000-8000-000000000001'";
    static final String OTHER_EMPLOYEE = "'019a0000-0000-7000-8000-000000000002'";
    static final String USER = "'019a0000-0000-7000-8000-0000000000aa'";
    static final String COMMAND = "'019a0000-0000-7000-8000-0000000000c1'";
    static final String LATER_COMMAND = "'019a0000-0000-7000-8000-0000000000c2'";

    String relationship;

    @BeforeEach
    void employeesAndCommands() {
        for (String id : new String[] {EMPLOYEE, OTHER_EMPLOYEE}) {
            jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values ("
                    + id + ", 'E-' || right(" + id + ", 2), 'name', 'EMPLOYED')").update();
        }
        relationship = "'" + EmploymentRelationshipSeed.relationship(jdbc, java.util.UUID.fromString(EMPLOYEE.replace("'", ""))) + "'";
        command(COMMAND, "SEPARATE", "PENDING");
        command(LATER_COMMAND, "REGULARIZE", "PENDING", 1);
    }

    private void command(String id, String kind, String status) {
        command(id, kind, status, 0);
    }

    private void command(String id, String kind, String status, int days) {
        jdbc.sql("insert into organization_workforce.personnel_commands (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status,"
                + " created_at, created_by, updated_at) values (" + id + ", " + relationship + ", " + EMPLOYEE + ", '" + kind + "', date '2026-11-01' + " + days + ", '" + status
                + "', now(), " + USER + ", now())").update();
    }

    private void version(String id, String from, String to, String status, String startDate, String basis, String channel, String commandId, String superseded) {
        boolean employed = status.equals("EMPLOYED");
        jdbc.sql("insert into organization_workforce.employment_relationship_versions (employment_relationship_version_id, employment_relationship_id, employee_id, effective_from_date, effective_to_date,"
                + " employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,"
                + " change_channel_kind, change_command_id, version_audit_entry_id, closed_at, superseded_at, superseding_command_id, created_at, updated_at)"
                + " values ('" + id + "', " + relationship + ", " + EMPLOYEE + ", date '" + from + "', " + (to == null ? "null" : "date '" + to + "'") + ", '" + status + "', "
                + (employed ? "'REGULAR'" : "null") + ", false, true, " + (startDate == null ? "null" : "date '" + startDate + "'") + ", "
                + (basis == null ? "null" : "'" + basis + "'") + ", '" + channel + "', " + commandId + ", "
                + (channel.equals("COMMAND") ? USER : "null") + ", " + (to == null ? "null" : "now()") + ", " + (superseded == null ? "null" : "now()")
                + ", " + (superseded == null ? "null" : superseded) + ", now(), now())").update();
    }

    private static final String V1 = "019a0000-0000-7000-8000-0000000000d1";
    private static final String V2 = "019a0000-0000-7000-8000-0000000000d2";

    @Test
    @DisplayName("W2 PR-B an employed version carries a start date and its basis; a migrated row may have an unknown start; a separated version has neither")
    void employmentStartColumnsFollowTheStatus() {
        version(V1, "2026-01-01", null, "EMPLOYED", "2020-02-29", "DECLARED", "COMMAND", COMMAND, null);
        assertThat(jdbc.sql("select count(*) from organization_workforce.employment_relationship_versions").query(Long.class).single()).isEqualTo(1L);

        assertThatThrownBy(() -> version(V2, "2026-02-01", null, "EMPLOYED", null, null, "MIGRATION", "null", null)).hasMessageContaining("ck_employment_relationship_versions_employment_start_employed");
        assertThatThrownBy(() -> version(V2, "2026-02-01", null, "EMPLOYED", null, "DECLARED", "MIGRATION", "null", null)).hasMessageContaining("ck_employment_relationship_versions_employment_start_known");
        assertThatThrownBy(() -> version(V2, "2026-02-01", null, "EMPLOYED", "2026-03-01", "HR_PROVIDED", "MIGRATION", "null", null)).hasMessageContaining("ck_employment_relationship_versions_employment_start_not_after");
        assertThatThrownBy(() -> version(V2, "2026-02-01", null, "SEPARATED", "2020-01-01", "DECLARED", "MIGRATION", "null", null)).hasMessageContaining("ck_employment_relationship_versions_employment_start_employed");
        assertThatThrownBy(() -> version(V2, "2026-02-01", null, "EMPLOYED", "2020-01-01", "GUESSED", "MIGRATION", "null", null)).hasMessageContaining("ck_employment_relationship_versions_employment_start_basis_kind");
    }

    @Test
    @DisplayName("W2 PR-B a migrated employee whose start date is unknown is stored with the basis UNKNOWN and an empty date.")
    void unknownStartIsStorable() {
        version(V1, "2026-01-01", null, "EMPLOYED", null, "UNKNOWN", "MIGRATION", "null", null);

        assertThat(jdbc.sql("select employment_start_basis_kind from organization_workforce.employment_relationship_versions").query(String.class).single()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("W2 PR-B only unsuperseded versions may not overlap: a superseded row frees its range")
    void overlapIgnoresSupersededRows() {
        version(V1, "2026-01-01", null, "EMPLOYED", "2020-01-01", "DECLARED", "COMMAND", COMMAND, null);

        assertThatThrownBy(() -> version(V2, "2026-03-01", null, "EMPLOYED", "2020-01-01", "DECLARED", "COMMAND", COMMAND, null))
                .hasMessageContaining("ex_employment_relationship_versions_overlap");

        jdbc.sql("update organization_workforce.employment_relationship_versions set superseded_at = now(), superseding_command_id = " + LATER_COMMAND
                + ", updated_at = now() where employment_relationship_version_id = '" + V1 + "'").update();
        version(V2, "2026-03-01", null, "EMPLOYED", "2020-01-01", "DECLARED", "COMMAND", COMMAND, null);
    }

    @Test
    @DisplayName("W2 PR-B the closing and superseding columns are set once, nothing else of a version changes")
    void versionRowsAreAppendOnly() {
        version(V1, "2026-01-01", null, "EMPLOYED", "2020-01-01", "DECLARED", "COMMAND", COMMAND, null);
        String where = " where employment_relationship_version_id = '" + V1 + "'";

        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.employment_relationship_versions set is_fee_earner = false" + where).update())
                .hasMessageContaining("only the closing and superseding columns");
        assertThat(jdbc.sql("update organization_workforce.employment_relationship_versions set effective_to_date = date '2026-06-01', closed_at = now(), updated_at = now()" + where).update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.employment_relationship_versions set effective_to_date = date '2026-07-01'" + where).update())
                .hasMessageContaining("is set once");
        assertThat(jdbc.sql("update organization_workforce.employment_relationship_versions set superseded_at = now(), superseding_command_id = " + LATER_COMMAND + ", updated_at = now()" + where).update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.employment_relationship_versions set superseding_command_id = " + COMMAND + where).update())
                .hasMessageContaining("is set once");
    }

    @Test
    @DisplayName("W2 PR-B a personnel command's status only moves forward, its withdrawal columns are set once and its content never changes")
    void commandsAreAppendOnly() {
        String where = " where personnel_command_id = " + COMMAND;

        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.personnel_commands set effective_date = date '2026-12-01'" + where).update())
                .hasMessageContaining("only the status and timestamp columns");
        assertThat(jdbc.sql("update organization_workforce.personnel_commands set status = 'HELD', updated_at = now()" + where).update()).isEqualTo(1);
        assertThat(jdbc.sql("update organization_workforce.personnel_commands set status = 'EFFECTIVE', applied_at = now(), updated_at = now()" + where).update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.personnel_commands set status = 'PENDING', applied_at = null" + where).update())
                .hasMessageContaining("cannot move");
        String other = " where personnel_command_id = " + LATER_COMMAND;
        assertThat(jdbc.sql("update organization_workforce.personnel_commands set status = 'WITHDRAWN', withdrawn_at = now(), withdrawn_by = " + USER
                + ", withdrawal_reason = 'wrong date', updated_at = now()" + other).update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.personnel_commands set withdrawal_reason = 'changed'" + other).update())
                .hasMessageContaining("is set once");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.personnel_commands set status = 'EFFECTIVE', applied_at = now()" + other).update())
                .hasMessageContaining("cannot move");
    }

    @Test
    @DisplayName("W2 PR-B, #931 a held separation can go to pending (released before its date, ruling on #732) and be withdrawn from there")
    void heldCommandMovesOnlyForward() {
        String where = " where personnel_command_id = " + COMMAND;
        jdbc.sql("update organization_workforce.personnel_commands set status = 'HELD', updated_at = now()" + where).update();

        assertThat(jdbc.sql("update organization_workforce.personnel_commands set status = 'PENDING', updated_at = now()" + where).update()).isEqualTo(1);
        assertThat(jdbc.sql("update organization_workforce.personnel_commands set status = 'WITHDRAWN', withdrawn_at = now(), withdrawn_by = " + USER
                + ", withdrawal_reason = 'not leaving', updated_at = now()" + where).update()).isEqualTo(1);
    }

    @Test
    @DisplayName("W2 PR-B a command carries the columns of its kind: a hire without a target is refused, only a separation can be held, a correction is immediate")
    void commandRowsFollowTheirKind() {
        assertThatThrownBy(() -> command("'019a0000-0000-7000-8000-0000000000c3'", "HIRE", "PENDING")).hasMessageContaining("ck_personnel_commands_hire_columns");
        assertThatThrownBy(() -> command("'019a0000-0000-7000-8000-0000000000c3'", "TRANSFER", "HELD")).hasMessageContaining("ck_personnel_commands_held_kind");
        assertThatThrownBy(() -> command("'019a0000-0000-7000-8000-0000000000c3'", "CORRECTION", "PENDING")).hasMessageContaining("ck_personnel_commands_correction_immediate");
        assertThatThrownBy(() -> command("'019a0000-0000-7000-8000-0000000000c3'", "SEPARATE", "PENDING")).hasMessageContaining("uk_personnel_commands_open_per_relationship_day");
        assertThatThrownBy(() -> command("'019a0000-0000-7000-8000-0000000000c3'", "UNPAID_LEAVE_START", "WITHDRAWN")).hasMessageContaining("ck_personnel_commands_unpaid_leave");
    }

    @Test
    @DisplayName("W2 PR-B the employee code is unique across all employees")
    void employeeCodeIsUnique() {
        assertThatThrownBy(() -> jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) select '019a0000-0000-7000-8000-000000000003', employee_code, 'x', 'EMPLOYED' from organization_workforce.employees limit 1").update())
                .hasMessageContaining("uk_employees_employee_code");
    }
}
