package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** IMP-1030-ORG-F0 (#904): org_unit_versions, org_unit_legal_entities and position_versions carry the W2 version common set and the org structure command references. */
class OrgUnitAndPositionVersionTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String UNIT = "'019a0000-0000-7000-8000-0000000000b1'";
    static final String POSITION = "'019a0000-0000-7000-8000-0000000000b2'";
    static final String LEGAL_ENTITY = "'019a0000-0000-7000-8000-0000000000e1'";
    static final String USER = "'019a0000-0000-7000-8000-0000000000aa'";
    static final String COMMAND = "'019a0000-0000-7000-8000-0000000000c1'";
    static final String LATER_COMMAND = "'019a0000-0000-7000-8000-0000000000c2'";
    static final String AUDIT = "'019a0000-0000-7000-8000-0000000000f1'";
    static final String V1 = "'019a0000-0000-7000-8000-0000000000d1'";
    static final String V2 = "'019a0000-0000-7000-8000-0000000000d2'";
    static final String UNKNOWN = "'019a0000-0000-7000-8000-00000000ffff'";

    /** One table under test: its name, key column, subject column and the columns of a minimal row. */
    record Table(String name, String idColumn, String subjectColumn, String subject, String commandColumn, String supersedingColumn, String baseColumns, String baseValues) {
        String qualified() {
            return "organization_workforce." + name;
        }
    }

    static final Table UNIT_VERSIONS = new Table("org_unit_versions", "org_unit_version_id", "org_unit_id", UNIT, "change_org_structure_command_id",
            "superseding_org_structure_command_id", "name, status, is_delivery_unit", "'unit', 'ACTIVE', false");
    static final Table UNIT_ENTITIES = new Table("org_unit_legal_entities", "org_unit_legal_entity_id", "org_unit_id", UNIT, "change_org_structure_command_id",
            "superseding_org_structure_command_id", "legal_entity_id", LEGAL_ENTITY);
    static final Table POSITION_VERSIONS = new Table("position_versions", "position_version_id", "position_id", POSITION, "change_org_structure_command_id",
            "superseding_org_structure_command_id", "name, status", "'position', 'ACTIVE'");

    @BeforeEach
    void targetsAndTwoCommands() {
        jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (" + UNIT + ", 'unit', 'ACTIVE')").update();
        jdbc.sql("insert into organization_workforce.positions (id, name, status) values (" + POSITION + ", 'position', 'ACTIVE')").update();
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (" + LEGAL_ENTITY + ", 'ABC', 'entity', 'ACTIVE') on conflict do nothing").update();
        jdbc.sql("insert into organization_workforce.legal_entity_attributions (attribution_id, org_unit_id) values (gen_random_uuid(), " + UNIT + ")").update();
        command(COMMAND, 0);
        command(LATER_COMMAND, 1);
    }

    private void command(String id, int days) {
        jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,"
                + " created_at, created_by, updated_at) values (" + id + ", 'DISSOLVE', 'ORG_UNIT', " + UNIT + ", date '2026-11-01' + " + days + ", 'PENDING', now(), " + USER + ", now())").update();
    }

    /** Inserts a row of the table; the extra arguments are column / value pairs (SQL literals) added to the base columns. */
    private void row(Table t, String id, String from, String to, String... more) {
        List<String> columns = new ArrayList<>(List.of(t.idColumn(), t.subjectColumn(), "effective_from_date", t.baseColumns()));
        List<String> values = new ArrayList<>(List.of(id, t.subject(), "date '" + from + "'", t.baseValues()));
        if (to != null) {
            columns.addAll(List.of("effective_to_date", "closed_at"));
            values.addAll(List.of("date '" + to + "'", "now()"));
        }
        for (int i = 0; i < more.length; i += 2) {
            columns.add(more[i]);
            values.add(more[i + 1]);
        }
        if (t == POSITION_VERSIONS && !columns.contains("change_channel_kind")) {
            columns.add("change_channel_kind");
            values.add("'MIGRATION'");
        }
        jdbc.sql("insert into " + t.qualified() + " (" + String.join(", ", columns) + ") values (" + String.join(", ", values) + ")").update();
    }

    /** A command-written row: the channel, command and audit entry as the table's constraints want them. */
    private String[] commandRow(Table t, String... more) {
        List<String> all = new ArrayList<>(List.of(t.commandColumn(), COMMAND, "version_audit_entry_id", AUDIT));
        all.addAll(List.of("change_channel_kind", "'COMMAND'"));
        all.addAll(List.of(more));
        return all.toArray(String[]::new);
    }

    private Table[] tables() {
        return new Table[] {UNIT_VERSIONS, UNIT_ENTITIES, POSITION_VERSIONS};
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 a row without the new columns is a migration row (position versions name the channel); a command row passes with command and audit entry")
    void channelAndCommandGoTogether() {
        row(UNIT_VERSIONS, V1, "2026-01-01", "2026-02-01");
        row(UNIT_VERSIONS, V2, "2026-02-01", null, commandRow(UNIT_VERSIONS));
        assertThat(jdbc.sql("select change_channel_kind from " + UNIT_VERSIONS.qualified() + " order by effective_from_date").query(String.class).list()).containsExactly("MIGRATION", "COMMAND");
        row(UNIT_ENTITIES, V1, "2026-01-01", null, commandRow(UNIT_ENTITIES));
        row(POSITION_VERSIONS, V1, "2026-01-01", "2026-02-01", commandRow(POSITION_VERSIONS));
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 channel and command id come together, a command row needs an audit entry, the channel is one of two, dates are finite — on each table")
    void commandColumnsFollowTheChannel() {
        for (Table t : tables()) {
            String ck = "ck_" + t.name();
            String channel = "change_channel_kind";
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, channel, "'COMMAND'", "version_audit_entry_id", AUDIT)).as(t.name()).hasMessageContaining(ck + "_command");
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, channel, "'MIGRATION'", t.commandColumn(), COMMAND)).as(t.name()).hasMessageContaining(ck + "_command");
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, channel, "'COMMAND'", t.commandColumn(), COMMAND)).as(t.name()).hasMessageContaining(ck + "_audit");
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, channel, "'SYNC'")).as(t.name()).hasMessageContaining(ck + "_change_channel_kind");
            assertThatThrownBy(() -> row(t, V1, "infinity", null)).as(t.name()).hasMessageContaining(ck + "_finite");
        }
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 the command, superseding command and corrected-from references must exist — on each table")
    void referencesMustExist() {
        for (Table t : tables()) {
            String fk = "fk_" + t.name() + "_";
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, t.commandColumn(), UNKNOWN, "version_audit_entry_id", AUDIT, "change_channel_kind", "'COMMAND'"))
                    .as(t.name()).hasMessageContaining(fk + t.commandColumn());
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, "superseded_at", "now()", t.supersedingColumn(), UNKNOWN)).as(t.name()).hasMessageContaining(fk + t.supersedingColumn());
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, "corrected_from_version_id", UNKNOWN)).as(t.name()).hasMessageContaining(fk + "corrected_from_version_id");
            row(t, V1, "2026-01-01", null, "superseded_at", "now()", t.supersedingColumn(), COMMAND);
            if (t == UNIT_ENTITIES) {
                row(t, V2, "2026-01-01", null, "corrected_from_version_id", V1);
            } else {
                row(t, V2, "2026-01-01", null, "corrected_from_version_id", V1, "revision_number", "1");
            }
        }
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 closing columns come together, and so do the superseding columns — on each table")
    void pairedColumns() {
        for (Table t : tables()) {
            String ck = "ck_" + t.name();
            String channel = t == POSITION_VERSIONS ? ", change_channel_kind" : "";
            String channelValue = t == POSITION_VERSIONS ? ", 'MIGRATION'" : "";
            assertThatThrownBy(() -> jdbc.sql("insert into " + t.qualified() + " (" + t.idColumn() + ", " + t.subjectColumn() + ", effective_from_date, " + t.baseColumns() + ", effective_to_date" + channel + ")"
                    + " values (" + V1 + ", " + t.subject() + ", date '2026-01-01', " + t.baseValues() + ", date '2026-02-01'" + channelValue + ")").update()).as(t.name()).hasMessageContaining(ck + "_closed");
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, "closed_at", "now()")).as(t.name()).hasMessageContaining(ck + "_closed");
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, "superseded_at", "now()")).as(t.name()).hasMessageContaining(ck + "_superseded");
            assertThatThrownBy(() -> row(t, V1, "2026-01-01", null, t.supersedingColumn(), COMMAND)).as(t.name()).hasMessageContaining(ck + "_superseded");
        }
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 overlap is checked between current rows only: a superseded row frees its range — on each table")
    void overlapIgnoresSupersededRows() {
        for (Table t : tables()) {
            row(t, V1, "2026-01-01", null);
            assertThatThrownBy(() -> row(t, V2, "2026-03-01", null)).as(t.name()).hasMessageContaining("ex_" + t.name() + "_overlap");
            jdbc.sql("update " + t.qualified() + " set superseded_at = now(), " + t.supersedingColumn() + " = " + COMMAND + ", updated_at = now() where " + t.idColumn() + " = " + V1).update();
            row(t, V2, "2026-03-01", null);
            VersionTables.truncate(jdbc, "truncate " + t.qualified());
        }
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 two current rows on the same day are refused where there is no revision number (legal entity periods); a superseded twin passes")
    void currentNaturalKeyOfLegalEntityPeriods() {
        row(UNIT_ENTITIES, V1, "2026-01-01", "2026-03-01");
        assertThatThrownBy(() -> row(UNIT_ENTITIES, V2, "2026-01-01", null)).hasMessageContaining("uk_org_unit_legal_entities_effective_from");
        jdbc.sql("update " + UNIT_ENTITIES.qualified() + " set superseded_at = now(), superseding_org_structure_command_id = " + COMMAND + ", updated_at = now() where org_unit_legal_entity_id = " + V1).update();
        row(UNIT_ENTITIES, V2, "2026-01-01", null);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 a closed row can be superseded once; closing and superseding columns are set once; nothing else changes; open rows only close — on each table")
    void rowsAreAppendOnly() {
        for (Table t : tables()) {
            row(t, V1, "2026-01-01", null);
            String where = " where " + t.idColumn() + " = " + V1;
            String content = t == POSITION_VERSIONS ? "name = 'other'" : t == UNIT_VERSIONS ? "name = 'other'" : "legal_entity_id = " + LEGAL_ENTITY + ", version_audit_entry_id = " + AUDIT;

            assertThatThrownBy(() -> jdbc.sql("update " + t.qualified() + " set " + content + where).update()).as(t.name()).hasMessageContaining("can only be closed");
            assertThat(jdbc.sql("update " + t.qualified() + " set effective_to_date = date '2026-06-01', closed_at = now(), updated_at = now()" + where).update()).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.sql("update " + t.qualified() + " set effective_to_date = date '2026-07-01'" + where).update()).as(t.name()).hasMessageContaining("is set once");
            assertThatThrownBy(() -> jdbc.sql("update " + t.qualified() + " set closed_at = now() + interval '1 day'" + where).update()).as(t.name()).hasMessageContaining("is set once");
            assertThatThrownBy(() -> jdbc.sql("update " + t.qualified() + " set " + content + where).update()).as(t.name()).hasMessageContaining("closed version row cannot change");

            assertThat(jdbc.sql("update " + t.qualified() + " set superseded_at = now(), " + t.supersedingColumn() + " = " + COMMAND + ", updated_at = now()" + where).update()).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.sql("update " + t.qualified() + " set " + t.supersedingColumn() + " = " + LATER_COMMAND + where).update()).as(t.name()).hasMessageContaining("is set once");
            assertThatThrownBy(() -> jdbc.sql("update " + t.qualified() + " set superseded_at = now() + interval '1 day'" + where).update()).as(t.name()).hasMessageContaining("is set once");
            VersionTables.truncate(jdbc, "truncate " + t.qualified());
        }
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 a row is never deleted or truncated, whoever asks — on each table")
    void deleteAndTruncateAreRefused() {
        for (Table t : tables()) {
            row(t, V1, "2026-01-01", null);
            assertThatThrownBy(() -> jdbc.sql("delete from " + t.qualified()).update()).as(t.name()).hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.sql("truncate " + t.qualified()).update()).as(t.name()).hasMessageContaining("append-only");
            VersionTables.truncate(jdbc, "truncate " + t.qualified());
        }
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 short names are 1 to 50 characters and not blank, empty means none")
    void shortNameBounds() {
        assertThatThrownBy(() -> row(UNIT_VERSIONS, V1, "2026-01-01", null, "short_name", "' '")).hasMessageContaining("ck_org_unit_versions_short_name");
        assertThatThrownBy(() -> row(UNIT_VERSIONS, V1, "2026-01-01", null, "short_name", "'" + "x".repeat(51) + "'")).hasMessageContaining("ck_org_unit_versions_short_name");
        row(UNIT_VERSIONS, V1, "2026-01-01", "2026-02-01", "short_name", "'" + "x".repeat(50) + "'");
        row(UNIT_VERSIONS, V2, "2026-02-01", null);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 position versions: status DELETED is refused; a code is unique among current rows only, never reused")
    void positionVersionRules() {
        String insert = "insert into organization_workforce.position_versions (position_version_id, position_id, effective_from_date, name, status, code, change_channel_kind"
                + ", revision_number, effective_to_date, closed_at) values (";
        assertThatThrownBy(() -> jdbc.sql(insert + V1 + ", " + POSITION + ", date '2026-01-01', 'p', 'DELETED', null, 'MIGRATION', 0, null, null)").update()).hasMessageContaining("ck_position_versions_status");
        jdbc.sql(insert + V1 + ", " + POSITION + ", date '2026-01-01', 'p', 'DISABLED', 'P1', 'MIGRATION', 0, date '2026-05-01', now())").update();
        assertThatThrownBy(() -> jdbc.sql(insert + V2 + ", " + POSITION + ", date '2026-05-01', 'p', 'ACTIVE', 'P1', 'MIGRATION', 1, null, null)").update()).hasMessageContaining("uk_position_versions_code");
        jdbc.sql("update organization_workforce.position_versions set superseded_at = now(), superseding_org_structure_command_id = "
                + COMMAND + ", updated_at = now() where position_version_id = " + V1).update();
        jdbc.sql(insert + V2 + ", " + POSITION + ", date '2026-05-01', 'p', 'ACTIVE', 'P1', 'MIGRATION', 1, null, null)").update();
    }
}
