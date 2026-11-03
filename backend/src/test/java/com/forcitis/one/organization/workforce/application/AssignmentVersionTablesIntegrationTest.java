package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** IMP-1030-PAR-B2: assignment_versions carries the W2 version common set and the personnel command references (ADR-0068 15.1, W2 design 2 and 3.4). */
class AssignmentVersionTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String EMPLOYEE = "'019a0000-0000-7000-8000-000000000001'";
    static final String UNIT = "'019a0000-0000-7000-8000-0000000000b1'";
    static final String POSITION = "'019a0000-0000-7000-8000-0000000000b2'";
    static final String USER = "'019a0000-0000-7000-8000-0000000000aa'";
    static final String COMMAND = "'019a0000-0000-7000-8000-0000000000c1'";
    static final String LATER_COMMAND = "'019a0000-0000-7000-8000-0000000000c2'";
    static final String AUDIT = "'019a0000-0000-7000-8000-0000000000f1'";
    static final String V1 = "'019a0000-0000-7000-8000-0000000000d1'";
    static final String V2 = "'019a0000-0000-7000-8000-0000000000d2'";
    static final String ROOT = "'019a0000-0000-7000-8000-0000000000e1'";
    static final String TABLE = "organization_workforce.assignment_versions";

    @BeforeEach
    void anEmployeeAUnitAndTwoCommands() {
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + EMPLOYEE
                + ", 'E-01', 'name', 'EMPLOYED')").update();
        jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (" + UNIT + ", 'unit', 'ACTIVE')").update();
        jdbc.sql("insert into organization_workforce.positions (id, name, status) values (" + POSITION + ", 'position', 'ACTIVE')").update();
        jdbc.sql("insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at)"
                + " values (" + ROOT + ", " + EMPLOYEE + ", 'PRIMARY', now(), now())").update();
        command(COMMAND, 0);
        command(LATER_COMMAND, 1);
    }

    private void command(String id, int days) {
        jdbc.sql("insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status,"
                + " created_at, created_by, updated_at) values (" + id + ", " + EMPLOYEE + ", 'REGULARIZE', date '2026-11-01' + " + days
                + ", 'PENDING', now(), " + USER + ", now())").update();
    }

    /** Inserts a version of the employee; the extra arguments are column / value pairs (SQL literals) added to the base columns. */
    private void version(String id, String from, String to, String... more) {
        List<String> columns = new ArrayList<>(List.of("assignment_version_id", "employee_id", "assignment_id", "assignment_kind", "effective_from_date", "org_unit_id"));
        List<String> values = new ArrayList<>(List.of(id, EMPLOYEE, ROOT, "'PRIMARY'", "date '" + from + "'", UNIT));
        if (to != null) {
            columns.addAll(List.of("effective_to_date", "closed_at"));
            values.addAll(List.of("date '" + to + "'", "now()"));
        }
        for (int i = 0; i < more.length; i += 2) {
            columns.add(more[i]);
            values.add(more[i + 1]);
        }
        jdbc.sql("insert into " + TABLE + " (" + String.join(", ", columns) + ") values (" + String.join(", ", values) + ")").update();
    }

    private static String[] commandRow(String... more) {
        List<String> all = new ArrayList<>(List.of("change_channel_kind", "'COMMAND'", "change_command_id", COMMAND, "version_audit_entry_id", AUDIT, "position_id", POSITION));
        all.addAll(List.of(more));
        return all.toArray(String[]::new);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B2 a row written without the new columns is a migration row; a command row names its command, audit entry and position")
    void channelAndCommandGoTogether() {
        version(V1, "2026-01-01", null);
        assertThat(jdbc.sql("select change_channel_kind from " + TABLE).query(String.class).single()).isEqualTo("MIGRATION");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B2 a command row passes with command, audit entry and position")
    void commandRowPasses() {
        version(V1, "2026-01-01", null, commandRow());
        assertThat(count("assignment_versions")).isEqualTo(1);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B2 channel and command id come together; a command row needs an audit entry and a position; the channel is one of two")
    void commandColumnsFollowTheChannel() {
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "change_channel_kind", "'COMMAND'", "version_audit_entry_id", AUDIT, "position_id", POSITION))
                .hasMessageContaining("ck_assignment_versions_command");
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "change_channel_kind", "'MIGRATION'", "change_command_id", COMMAND))
                .hasMessageContaining("ck_assignment_versions_command");
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "change_channel_kind", "'COMMAND'", "change_command_id", COMMAND, "position_id", POSITION))
                .hasMessageContaining("ck_assignment_versions_audit");
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "change_channel_kind", "'COMMAND'", "change_command_id", COMMAND, "version_audit_entry_id", AUDIT))
                .hasMessageContaining("ck_assignment_versions_command_position");
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "change_channel_kind", "'SYNC'")).hasMessageContaining("ck_assignment_versions_change_channel_kind");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B2 an employee does not report to themself, dates are finite")
    void reportsToAndDates() {
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "reports_to_employee_id", EMPLOYEE)).hasMessageContaining("ck_assignment_versions_reports_to_not_self");
        assertThatThrownBy(() -> version(V1, "infinity", null)).hasMessageContaining("ck_assignment_versions_finite");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B2 the command and the corrected-from references must exist")
    void referencesMustExist() {
        String unknown = "'019a0000-0000-7000-8000-00000000ffff'";
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "change_channel_kind", "'COMMAND'", "change_command_id", unknown, "version_audit_entry_id", AUDIT, "position_id", POSITION))
                .hasMessageContaining("fk_assignment_versions_change_command_id");
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "superseded_at", "now()", "superseding_command_id", unknown))
                .hasMessageContaining("fk_assignment_versions_superseding_command_id");
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "corrected_from_version_id", unknown))
                .hasMessageContaining("fk_assignment_versions_corrected_from");
        version(V1, "2026-01-01", null, "superseded_at", "now()", "superseding_command_id", COMMAND);
        version(V2, "2026-01-01", null, "revision_number", "1", "corrected_from_version_id", V1);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B2 closing columns come together, and so do the superseding columns")
    void pairedColumns() {
        assertThatThrownBy(() -> jdbc.sql("insert into " + TABLE + " (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date, org_unit_id, effective_to_date)"
                + " values (" + V1 + ", " + EMPLOYEE + ", " + ROOT + ", 'PRIMARY', date '2026-01-01', " + UNIT + ", date '2026-02-01')").update()).hasMessageContaining("ck_assignment_versions_closed");
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "closed_at", "now()")).hasMessageContaining("ck_assignment_versions_closed");
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "superseded_at", "now()")).hasMessageContaining("ck_assignment_versions_superseded");
        assertThatThrownBy(() -> version(V1, "2026-01-01", null, "superseding_command_id", COMMAND)).hasMessageContaining("ck_assignment_versions_superseded");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B2 only versions that are not superseded may overlap-check each other: a superseded row frees its range")
    void overlapIgnoresSupersededRows() {
        version(V1, "2026-01-01", null);
        assertThatThrownBy(() -> version(V2, "2026-03-01", null, "revision_number", "1")).hasMessageContaining("ex_assignment_versions_overlap");

        jdbc.sql("update " + TABLE + " set superseded_at = now(), superseding_command_id = " + COMMAND + ", updated_at = now() where assignment_version_id = " + V1).update();
        version(V2, "2026-03-01", null, "revision_number", "1");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B2 a closed row can be superseded once; closing and superseding columns are set once; nothing else of a version changes")
    void rowsAreAppendOnly() {
        version(V1, "2026-01-01", null);
        String where = " where assignment_version_id = " + V1;

        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set position_id = " + POSITION + where).update()).hasMessageContaining("can only be closed");
        assertThat(jdbc.sql("update " + TABLE + " set effective_to_date = date '2026-06-01', closed_at = now(), updated_at = now()" + where).update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set effective_to_date = date '2026-07-01'" + where).update()).hasMessageContaining("is set once");
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set closed_at = now() + interval '1 day'" + where).update()).hasMessageContaining("is set once");
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set position_id = " + POSITION + where).update()).hasMessageContaining("closed version row cannot change");

        assertThat(jdbc.sql("update " + TABLE + " set superseded_at = now(), superseding_command_id = " + COMMAND + ", updated_at = now()" + where).update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set superseding_command_id = " + LATER_COMMAND + where).update()).hasMessageContaining("is set once");
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set superseded_at = now() + interval '1 day'" + where).update()).hasMessageContaining("is set once");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B2 a version is never deleted or truncated, whoever asks")
    void deleteAndTruncateAreRefused() {
        version(V1, "2026-01-01", null);
        assertThatThrownBy(() -> jdbc.sql("delete from " + TABLE).update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate " + TABLE).update()).hasMessageContaining("append-only");
    }
}
