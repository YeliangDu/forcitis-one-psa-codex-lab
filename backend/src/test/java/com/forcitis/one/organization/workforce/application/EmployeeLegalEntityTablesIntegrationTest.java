package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** IMP-1030-FC-A (#892): employee_legal_entities carries the W2 version common set and the personnel command references (W2 design 2 and 3.6). */
class EmployeeLegalEntityTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String EMPLOYEE = "'019a0000-0000-7000-8000-000000000001'";
    static final String LEGAL_ENTITY = "'019a0000-0000-7000-8000-0000000000e1'";
    static final String USER = "'019a0000-0000-7000-8000-0000000000aa'";
    static final String COMMAND = "'019a0000-0000-7000-8000-0000000000c1'";
    static final String LATER_COMMAND = "'019a0000-0000-7000-8000-0000000000c2'";
    static final String AUDIT = "'019a0000-0000-7000-8000-0000000000f1'";
    static final String P1 = "'019a0000-0000-7000-8000-0000000000d1'";
    static final String P2 = "'019a0000-0000-7000-8000-0000000000d2'";
    static final String TABLE = "organization_workforce.employee_legal_entities";

    @BeforeEach
    void anEmployeeItsRootAndTwoCommands() {
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + EMPLOYEE + ", 'E-01', 'name', 'EMPLOYED')").update();
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (" + LEGAL_ENTITY + ", 'ZQE', 'entity', 'ACTIVE') on conflict (id) do nothing").update();
        jdbc.sql("insert into organization_workforce.legal_entity_attributions (attribution_id, employee_id) values (gen_random_uuid(), " + EMPLOYEE + ")").update();
        EmploymentRelationshipSeed.seed(jdbc, EMPLOYEE);
        command(COMMAND, 0);
        command(LATER_COMMAND, 1);
    }

    private void command(String id, int days) {
        jdbc.sql("insert into organization_workforce.personnel_commands (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)"
                + " values (" + id + ", " + EmploymentRelationshipSeed.ref(EMPLOYEE) + ", " + EMPLOYEE + ", 'REGULARIZE', date '2026-11-01' + " + days + ", 'PENDING', now(), " + USER + ", now())").update();
    }

    /** Inserts a period of the employee; the extra arguments are column / value pairs (SQL literals) added to the base columns. */
    private void period(String id, String from, String to, String... more) {
        List<String> columns = new ArrayList<>(List.of("employee_legal_entity_id", "employee_id", "effective_from_date", "legal_entity_id"));
        List<String> values = new ArrayList<>(List.of(id, EMPLOYEE, "date '" + from + "'", LEGAL_ENTITY));
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
        List<String> all = new ArrayList<>(List.of("change_channel_kind", "'COMMAND'", "change_command_id", COMMAND, "version_audit_entry_id", AUDIT));
        all.addAll(List.of(more));
        return all.toArray(String[]::new);
    }

    @Test
    @DisplayName("IMP-1030-FC-A a row written without the new columns is a migration row; a command row passes with command and audit entry")
    void defaultChannelIsMigration() {
        period(P1, "2026-01-01", "2026-02-01");
        period(P2, "2026-02-01", null, commandRow());
        assertThat(jdbc.sql("select change_channel_kind from " + TABLE + " order by effective_from_date").query(String.class).list()).containsExactly("MIGRATION", "COMMAND");
    }

    @Test
    @DisplayName("IMP-1030-FC-A channel and command id come together; a command row needs an audit entry; the channel is one of two")
    void commandColumnsFollowTheChannel() {
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "change_channel_kind", "'COMMAND'", "version_audit_entry_id", AUDIT)).hasMessageContaining("ck_employee_legal_entities_command");
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "change_channel_kind", "'MIGRATION'", "change_command_id", COMMAND)).hasMessageContaining("ck_employee_legal_entities_command");
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "change_channel_kind", "'COMMAND'", "change_command_id", COMMAND)).hasMessageContaining("ck_employee_legal_entities_audit");
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "change_channel_kind", "'SYNC'")).hasMessageContaining("ck_employee_legal_entities_change_channel_kind");
        assertThatThrownBy(() -> period(P1, "infinity", null)).hasMessageContaining("ck_employee_legal_entities_finite");
    }

    @Test
    @DisplayName("IMP-1030-FC-A the command, superseding command and corrected-from references must exist")
    void referencesMustExist() {
        String unknown = "'019a0000-0000-7000-8000-00000000ffff'";
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "change_channel_kind", "'COMMAND'", "change_command_id", unknown, "version_audit_entry_id", AUDIT))
                .hasMessageContaining("fk_employee_legal_entities_change_command_id");
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "superseded_at", "now()", "superseding_command_id", unknown)).hasMessageContaining("fk_employee_legal_entities_superseding_command_id");
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "corrected_from_version_id", unknown)).hasMessageContaining("fk_employee_legal_entities_corrected_from_version_id");
        period(P1, "2026-01-01", null, "superseded_at", "now()", "superseding_command_id", COMMAND);
        period(P2, "2026-01-01", null, "corrected_from_version_id", P1);
    }

    @Test
    @DisplayName("IMP-1030-FC-A closing columns come together, and so do the superseding columns")
    void pairedColumns() {
        assertThatThrownBy(() -> jdbc.sql("insert into " + TABLE + " (employee_legal_entity_id, employee_id, effective_from_date, legal_entity_id, effective_to_date) values ("
                + P1 + ", " + EMPLOYEE + ", date '2026-01-01', " + LEGAL_ENTITY + ", date '2026-02-01')").update()).hasMessageContaining("ck_employee_legal_entities_closed");
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "closed_at", "now()")).hasMessageContaining("ck_employee_legal_entities_closed");
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "superseded_at", "now()")).hasMessageContaining("ck_employee_legal_entities_superseded");
        assertThatThrownBy(() -> period(P1, "2026-01-01", null, "superseding_command_id", COMMAND)).hasMessageContaining("ck_employee_legal_entities_superseded");
    }

    @Test
    @DisplayName("IMP-1030-FC-A one current period per employee and day; a superseded row frees the day and its range")
    void currentRowsOnly() {
        period(P1, "2026-01-01", "2026-03-01");
        assertThatThrownBy(() -> period(P2, "2026-01-01", null)).hasMessageContaining("uk_employee_legal_entities_effective_from");
        assertThatThrownBy(() -> period(P2, "2026-02-01", null)).hasMessageContaining("ex_employee_legal_entities_overlap");

        jdbc.sql("update " + TABLE + " set superseded_at = now(), superseding_command_id = " + COMMAND + ", updated_at = now() where employee_legal_entity_id = " + P1).update();
        period(P2, "2026-01-01", null);
        assertThat(count("employee_legal_entities")).isEqualTo(2);
    }

    @Test
    @DisplayName("IMP-1030-FC-A a closed row can be superseded once; closing and superseding columns are set once; nothing else of a period changes")
    void rowsAreAppendOnly() {
        period(P1, "2026-01-01", null);
        String where = " where employee_legal_entity_id = " + P1;

        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set change_command_id = " + COMMAND + where).update()).hasMessageContaining("can only be closed");
        assertThat(jdbc.sql("update " + TABLE + " set effective_to_date = date '2026-06-01', closed_at = now(), updated_at = now()" + where).update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set effective_to_date = date '2026-07-01'" + where).update()).hasMessageContaining("is set once");
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set closed_at = now() + interval '1 day'" + where).update()).hasMessageContaining("is set once");
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set legal_entity_id = " + LEGAL_ENTITY + ", version_audit_entry_id = " + AUDIT + where).update())
                .hasMessageContaining("closed version row cannot change");

        assertThat(jdbc.sql("update " + TABLE + " set superseded_at = now(), superseding_command_id = " + COMMAND + ", updated_at = now()" + where).update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set superseding_command_id = " + LATER_COMMAND + where).update()).hasMessageContaining("is set once");
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set superseded_at = now() + interval '1 day'" + where).update()).hasMessageContaining("is set once");
    }

    @Test
    @DisplayName("IMP-1030-FC-A a period is never deleted or truncated, whoever asks")
    void deleteAndTruncateAreRefused() {
        period(P1, "2026-01-01", null);
        assertThatThrownBy(() -> jdbc.sql("delete from " + TABLE).update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate " + TABLE).update()).hasMessageContaining("append-only");
    }
}
