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

/** IMP-1030-ORG-F1 (#905): the org structure command table and its state transitions, as the database enforces them (ADR-0068 item 4). */
class OrgStructureCommandTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String UNIT = "'019a0000-0000-7000-8000-0000000000b1'";
    static final String OTHER_UNIT = "'019a0000-0000-7000-8000-0000000000b2'";
    static final String POSITION = "'019a0000-0000-7000-8000-0000000000d1'";
    static final String EMPLOYEE = "'019a0000-0000-7000-8000-000000000001'";
    static final String LEGAL_ENTITY = "'019a0000-0000-7000-8000-0000000000f1'";
    static final String USER = "'019a0000-0000-7000-8000-0000000000aa'";
    static final String COMMAND = "'019a0000-0000-7000-8000-0000000000c1'";
    static final String OTHER_COMMAND = "'019a0000-0000-7000-8000-0000000000c2'";
    static final String OPERATOR_ROLE = "f1_runtime";

    @Autowired
    DataSource dataSource;

    String day = "date '2026-11-10'";

    @BeforeEach
    void targets() {
        day = "date '2026-11-10'";
        VersionTables.truncate(jdbc, "truncate table organization_workforce.state_transitions");
        for (String id : new String[] {UNIT, OTHER_UNIT}) {
            jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (" + id + ", 'unit', 'ACTIVE')").update();
        }
        jdbc.sql("insert into organization_workforce.positions (id, name, status) values (" + POSITION + ", 'position', 'ACTIVE')").update();
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + EMPLOYEE + ", 'E-1', 'name', 'EMPLOYED')").update();
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (" + LEGAL_ENTITY + ", 'ZQO', 'entity', 'ACTIVE') on conflict (id) do nothing").update();
    }

    /** Inserts a command with the given extra columns; the target is the unit unless the columns say otherwise. */
    private void command(String id, String kind, String targetKind, String status, String extraColumns, String extraValues) {
        String target = targetKind.equals("ORG_UNIT") ? "target_org_unit_id" : "target_position_id";
        String targetId = targetKind.equals("ORG_UNIT") ? UNIT : POSITION;
        jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, " + target + ", effective_date, status,"
                + " created_at, created_by, updated_at" + extraColumns + ") values (" + id + ", '" + kind + "', '" + targetKind + "', " + targetId + ", " + day + ", '" + status
                + "', now(), " + USER + ", now()" + extraValues + ")").update();
    }

    private void pending(String id, String kind, String extraColumns, String extraValues) {
        command(id, kind, "ORG_UNIT", "PENDING", extraColumns, extraValues);
    }

    private void refused(String constraint, Runnable insert) {
        assertThatThrownBy(insert::run).hasMessageContaining(constraint);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 every org unit kind has a passing row, RENAME and a position SET_ATTRIBUTES are refused")
    void commandKindsPerTarget() {
        pending(COMMAND, "CREATE", ", target_name, is_target_delivery_unit", ", 'n', false");
        day = "date '2026-11-11'";
        pending("gen_random_uuid()", "DISSOLVE", "", "");
        day = "date '2026-11-10'";
        List<String[]> kinds = List.of(
                new String[] {"SET_ATTRIBUTES", ", target_name", ", 'new'"},
                new String[] {"REPARENT", ", target_parent_org_unit_id", ", " + OTHER_UNIT},
                new String[] {"ASSIGN_HEAD", ", target_head_employee_id", ", " + EMPLOYEE},
                new String[] {"ASSIGN_RECEIVABLES_MANAGER", ", target_receivables_manager_employee_id", ", " + EMPLOYEE},
                new String[] {"ASSIGN_LEGAL_ENTITY", ", target_legal_entity_id", ", " + LEGAL_ENTITY},
                new String[] {"MERGE", ", merge_into_org_unit_id", ", " + OTHER_UNIT});
        int dayOfMonth = 12;
        for (String[] kind : kinds) {
            jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,"
                    + " created_at, created_by, updated_at" + kind[1] + ") values (gen_random_uuid(), '" + kind[0] + "', 'ORG_UNIT', " + UNIT + ", date '2026-11-" + dayOfMonth++
                    + "', 'PENDING', now(), " + USER + ", now()" + kind[2] + ")").update();
        }
        command("gen_random_uuid()", "CREATE", "POSITION", "PENDING", ", target_name", ", 'p'");
        day = "date '2026-11-11'";
        command("gen_random_uuid()", "DISABLE", "POSITION", "PENDING", "", "");
        day = "date '2026-11-10'";
        refused("ck_org_structure_commands_command_kind", () -> pending("gen_random_uuid()", "RENAME", ", target_name", ", 'x'"));
        refused("ck_org_structure_commands_command_kind", () -> command("gen_random_uuid()", "SET_ATTRIBUTES", "POSITION", "PENDING", ", target_name", ", 'x'"));
        refused("ck_org_structure_commands_command_kind", () -> command("gen_random_uuid()", "DISSOLVE", "POSITION", "PENDING", "", ""));
        assertThat(count("org_structure_commands")).isEqualTo(10);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 exactly one target column, matching the target kind")
    void targetMatchesKind() {
        refused("ck_org_structure_commands_target", () -> jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind,"
                + " target_org_unit_id, target_position_id, effective_date, status, target_name, created_at, created_by, updated_at) values (gen_random_uuid(), 'CREATE', 'ORG_UNIT', "
                + UNIT + ", " + POSITION + ", date '2026-11-10', 'PENDING', 'n', now(), " + USER + ", now())").update());
        refused("ck_org_structure_commands_target", () -> jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind,"
                + " target_position_id, effective_date, status, target_name, is_target_delivery_unit, created_at, created_by, updated_at) values (gen_random_uuid(), 'CREATE', 'ORG_UNIT', "
                + POSITION + ", date '2026-11-10', 'PENDING', 'n', false, now(), " + USER + ", now())").update());
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 SET_ATTRIBUTES needs one attribute and no relation target; the create-only columns stay out of other kinds")
    void setAttributesColumns() {
        pending("gen_random_uuid()", "SET_ATTRIBUTES", ", target_short_name", ", 'short'");
        day = "date '2026-11-11'";
        pending("gen_random_uuid()", "SET_ATTRIBUTES", ", is_target_delivery_unit", ", true");
        day = "date '2026-11-12'";
        pending("gen_random_uuid()", "SET_ATTRIBUTES", ", target_region_kind", ", 'REGIONAL'");
        day = "date '2026-11-10'";
        refused("ck_org_structure_commands_set_attributes_columns", () -> pending("gen_random_uuid()", "SET_ATTRIBUTES", "", ""));
        refused("ck_org_structure_commands_set_attributes_columns", () -> pending("gen_random_uuid()", "SET_ATTRIBUTES", ", target_name, target_head_employee_id", ", 'n', " + EMPLOYEE));
        refused("ck_org_structure_commands_legal_entity_columns", () -> pending("gen_random_uuid()", "SET_ATTRIBUTES", ", target_name, target_legal_entity_id", ", 'n', " + LEGAL_ENTITY));
        refused("ck_org_structure_commands_create_only_columns", () -> pending("gen_random_uuid()", "SET_ATTRIBUTES", ", target_name, target_code", ", 'n', 'C1'"));
        refused("ck_org_structure_commands_create_only_columns", () -> pending("gen_random_uuid()", "REPARENT", ", target_parent_org_unit_id, is_target_delivery_unit", ", " + OTHER_UNIT + ", true"));
        refused("ck_org_structure_commands_create_only_columns", () -> pending("gen_random_uuid()", "REPARENT", ", target_parent_org_unit_id, target_region_kind", ", " + OTHER_UNIT + ", 'REGIONAL'"));
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 CREATE columns: name required, delivery flag only for units, a position carries no unit-only column")
    void createColumns() {
        refused("ck_org_structure_commands_create_columns", () -> pending("gen_random_uuid()", "CREATE", ", is_target_delivery_unit", ", false"));
        refused("ck_org_structure_commands_unit_attributes", () -> pending("gen_random_uuid()", "CREATE", ", target_name", ", 'n'"));
        refused("ck_org_structure_commands_unit_attributes", () -> command("gen_random_uuid()", "CREATE", "POSITION", "PENDING", ", target_name, is_target_delivery_unit", ", 'n', false"));
        refused("ck_org_structure_commands_unit_attributes", () -> command("gen_random_uuid()", "CREATE", "POSITION", "PENDING", ", target_name, target_region_kind", ", 'n', 'REGIONAL'"));
        refused("ck_org_structure_commands_create_columns", () -> command("gen_random_uuid()", "CREATE", "POSITION", "PENDING", ", target_name, target_short_name", ", 'n', 's'"));
        refused("ck_org_structure_commands_legal_entity_columns", () -> command("gen_random_uuid()", "CREATE", "POSITION", "PENDING", ", target_name, target_legal_entity_id", ", 'n', " + LEGAL_ENTITY));
        refused("ck_org_structure_commands_create_columns", () -> pending("gen_random_uuid()", "CREATE", ", target_name, is_target_delivery_unit, target_head_employee_id", ", 'n', false, " + EMPLOYEE));
        pending("gen_random_uuid()", "CREATE", ", target_name, is_target_delivery_unit, target_code, target_region_kind, target_legal_entity_id, target_parent_org_unit_id",
                ", 'n', true, 'C1', 'HEADQUARTERS', " + LEGAL_ENTITY + ", " + OTHER_UNIT);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 ASSIGN_LEGAL_ENTITY needs its target and nothing else; no other kind but an org unit CREATE carries one")
    void legalEntityColumns() {
        refused("ck_org_structure_commands_legal_entity_columns", () -> pending("gen_random_uuid()", "ASSIGN_LEGAL_ENTITY", "", ""));
        refused("ck_org_structure_commands_legal_entity_columns", () -> pending("gen_random_uuid()", "ASSIGN_LEGAL_ENTITY", ", target_legal_entity_id, target_name", ", " + LEGAL_ENTITY + ", 'n'"));
        refused("ck_org_structure_commands_legal_entity_columns", () -> pending("gen_random_uuid()", "DISSOLVE", ", target_legal_entity_id", ", " + LEGAL_ENTITY));
        refused("ck_org_structure_commands_legal_entity_columns", () -> pending("gen_random_uuid()", "REPARENT", ", target_parent_org_unit_id, target_legal_entity_id", ", " + OTHER_UNIT + ", " + LEGAL_ENTITY));
        refused("ck_org_structure_commands_legal_entity_columns", () -> pending("gen_random_uuid()", "ASSIGN_HEAD", ", target_head_employee_id, target_legal_entity_id", ", " + EMPLOYEE + ", " + LEGAL_ENTITY));
        refused("ck_org_structure_commands_legal_entity_columns", () -> pending("gen_random_uuid()", "MERGE", ", merge_into_org_unit_id, target_legal_entity_id", ", " + OTHER_UNIT + ", " + LEGAL_ENTITY));
        refused("fk_org_structure_commands_target_legal_entity_id", () -> pending("gen_random_uuid()", "ASSIGN_LEGAL_ENTITY", ", target_legal_entity_id", ", gen_random_uuid()"));
        pending("gen_random_uuid()", "ASSIGN_LEGAL_ENTITY", ", target_legal_entity_id", ", " + LEGAL_ENTITY);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 REPARENT, ASSIGN_HEAD, ASSIGN_RECEIVABLES_MANAGER, DISSOLVE and MERGE carry exactly their own target")
    void ownTargetColumns() {
        refused("ck_org_structure_commands_reparent_columns", () -> pending("gen_random_uuid()", "REPARENT", "", ""));
        refused("ck_org_structure_commands_reparent_columns", () -> pending("gen_random_uuid()", "REPARENT", ", target_parent_org_unit_id, target_name", ", " + OTHER_UNIT + ", 'n'"));
        refused("ck_org_structure_commands_parent_not_self", () -> pending("gen_random_uuid()", "REPARENT", ", target_parent_org_unit_id", ", " + UNIT));
        refused("ck_org_structure_commands_head_columns", () -> pending("gen_random_uuid()", "ASSIGN_HEAD", "", ""));
        refused("ck_org_structure_commands_head_columns", () -> pending("gen_random_uuid()", "ASSIGN_HEAD", ", target_head_employee_id, target_name", ", " + EMPLOYEE + ", 'n'"));
        refused("ck_org_structure_commands_receivables_columns", () -> pending("gen_random_uuid()", "ASSIGN_RECEIVABLES_MANAGER", "", ""));
        refused("ck_org_structure_commands_receivables_columns", () -> pending("gen_random_uuid()", "ASSIGN_RECEIVABLES_MANAGER",
                ", target_receivables_manager_employee_id, target_head_employee_id", ", " + EMPLOYEE + ", " + EMPLOYEE));
        refused("ck_org_structure_commands_contentless_columns", () -> pending("gen_random_uuid()", "DISSOLVE", ", target_name", ", 'n'"));
        refused("ck_org_structure_commands_contentless_columns", () -> command("gen_random_uuid()", "DISABLE", "POSITION", "PENDING", ", target_name", ", 'n'"));
        refused("ck_org_structure_commands_merge_columns", () -> pending("gen_random_uuid()", "MERGE", "", ""));
        refused("ck_org_structure_commands_merge_columns", () -> pending("gen_random_uuid()", "SET_ATTRIBUTES", ", target_name, merge_into_org_unit_id", ", 'n', " + OTHER_UNIT));
        refused("ck_org_structure_commands_merge_columns", () -> pending("gen_random_uuid()", "MERGE", ", merge_into_org_unit_id, target_name", ", " + OTHER_UNIT + ", 'n'"));
        refused("ck_org_structure_commands_merge_not_self", () -> pending("gen_random_uuid()", "MERGE", ", merge_into_org_unit_id", ", " + UNIT));
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 names, short names, codes, region kinds and withdrawal reasons are bounded")
    void valueBounds() {
        String create = ", is_target_delivery_unit";
        refused("ck_org_structure_commands_name", () -> pending("gen_random_uuid()", "CREATE", create + ", target_name", ", false, '  '"));
        refused("ck_org_structure_commands_name", () -> pending("gen_random_uuid()", "CREATE", create + ", target_name", ", false, '" + "x".repeat(501) + "'"));
        refused("ck_org_structure_commands_name", () -> pending("gen_random_uuid()", "CREATE", create + ", target_name, target_short_name", ", false, 'n', '" + "x".repeat(51) + "'"));
        refused("ck_org_structure_commands_code", () -> pending("gen_random_uuid()", "CREATE", create + ", target_name, target_code", ", false, 'n', ' '"));
        refused("ck_org_structure_commands_region_kind", () -> pending("gen_random_uuid()", "CREATE", create + ", target_name, target_region_kind", ", false, 'n', 'ABROAD'"));
        refused("ck_org_structure_commands_effective_date_finite", () -> jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind,"
                + " target_org_unit_id, effective_date, status, created_at, created_by, updated_at) values (gen_random_uuid(), 'DISSOLVE', 'ORG_UNIT', " + UNIT
                + ", 'infinity', 'PENDING', now(), " + USER + ", now())").update());
        pending("gen_random_uuid()", "CREATE", create + ", target_name, target_short_name, target_code", ", false, '" + "x".repeat(500) + "', '" + "y".repeat(50) + "', 'C'");
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 status values, IN_PROGRESS only for MERGE, applied_at and withdrawal columns follow the status")
    void statusColumns() {
        refused("ck_org_structure_commands_withdrawn", () -> pending("gen_random_uuid()", "DISSOLVE", ", withdrawn_at", ", now()"));
        refused("ck_org_structure_commands_status", () -> command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "DONE", "", ""));
        refused("ck_org_structure_commands_in_progress_kind", () -> command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "IN_PROGRESS", "", ""));
        refused("ck_org_structure_commands_applied", () -> command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "EFFECTIVE", "", ""));
        refused("ck_org_structure_commands_applied", () -> pending("gen_random_uuid()", "DISSOLVE", ", applied_at", ", now()"));
        refused("ck_org_structure_commands_withdrawn", () -> command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "WITHDRAWN", "", ""));
        refused("ck_org_structure_commands_withdrawn", () -> command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "WITHDRAWN", ", withdrawn_at, withdrawn_by", ", now(), " + USER));
        refused("ck_org_structure_commands_withdrawal_reason", () -> command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "WITHDRAWN",
                ", withdrawn_at, withdrawn_by, withdrawal_reason", ", now(), " + USER + ", ' '"));
        command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "WITHDRAWN", ", withdrawn_at, withdrawn_by, withdrawal_reason", ", now(), " + USER + ", 'typo'");
        command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "AWAITING_ATTRIBUTION_COUNTERSIGN", "", "");
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 merge counts: null until the merge starts, then 0 <= transferred <= affected, equal once effective")
    void mergeProgress() {
        String merge = ", merge_into_org_unit_id";
        refused("ck_org_structure_commands_merge_progress", () -> command("gen_random_uuid()", "MERGE", "ORG_UNIT", "IN_PROGRESS", merge, ", " + OTHER_UNIT));
        refused("ck_org_structure_commands_merge_progress", () -> command("gen_random_uuid()", "MERGE", "ORG_UNIT", "PENDING",
                merge + ", affected_employee_count, transferred_employee_count", ", " + OTHER_UNIT + ", 3, 0"));
        refused("ck_org_structure_commands_merge_progress", () -> command("gen_random_uuid()", "MERGE", "ORG_UNIT", "IN_PROGRESS",
                merge + ", affected_employee_count", ", " + OTHER_UNIT + ", 3"));
        refused("ck_org_structure_commands_merge_progress", () -> command("gen_random_uuid()", "MERGE", "ORG_UNIT", "IN_PROGRESS",
                merge + ", affected_employee_count, transferred_employee_count", ", " + OTHER_UNIT + ", 3, 4"));
        refused("ck_org_structure_commands_merge_progress", () -> command("gen_random_uuid()", "MERGE", "ORG_UNIT", "IN_PROGRESS",
                merge + ", affected_employee_count, transferred_employee_count", ", " + OTHER_UNIT + ", 3, -1"));
        refused("ck_org_structure_commands_merge_progress", () -> command("gen_random_uuid()", "MERGE", "ORG_UNIT", "EFFECTIVE",
                merge + ", affected_employee_count, transferred_employee_count, applied_at", ", " + OTHER_UNIT + ", 3, 2, now()"));
        refused("ck_org_structure_commands_merge_progress", () -> command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "PENDING",
                ", affected_employee_count, transferred_employee_count", ", 1, 0"));
        command("gen_random_uuid()", "MERGE", "ORG_UNIT", "IN_PROGRESS", merge + ", affected_employee_count, transferred_employee_count", ", " + OTHER_UNIT + ", 3, 1");
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 one open command per unit and day, awaiting included; one per position and day; closed ones free the slot")
    void openPerDay() {
        pending(COMMAND, "DISSOLVE", "", "");
        refused("uk_org_structure_commands_open_org_unit_per_day", () -> pending(OTHER_COMMAND, "SET_ATTRIBUTES", ", target_name", ", 'n'"));
        refused("uk_org_structure_commands_open_org_unit_per_day", () -> command(OTHER_COMMAND, "SET_ATTRIBUTES", "ORG_UNIT", "AWAITING_ATTRIBUTION_COUNTERSIGN", ", target_name", ", 'n'"));
        command("gen_random_uuid()", "DISSOLVE", "ORG_UNIT", "WITHDRAWN", ", withdrawn_at, withdrawn_by, withdrawal_reason", ", now(), " + USER + ", 'again'");
        command("gen_random_uuid()", "DISABLE", "POSITION", "PENDING", "", "");
        refused("uk_org_structure_commands_open_position_per_day", () -> command("gen_random_uuid()", "DISABLE", "POSITION", "PENDING", "", ""));
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 a unit has at most one merge in progress, and a command id is unique together with its drafter")
    void mergeInProgressAndDrafterKey() {
        command(COMMAND, "MERGE", "ORG_UNIT", "IN_PROGRESS", ", merge_into_org_unit_id, affected_employee_count, transferred_employee_count", ", " + OTHER_UNIT + ", 2, 0");
        refused("uk_org_structure_commands_merge_in_progress", () -> jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind,"
                + " target_kind, target_org_unit_id, effective_date, status, merge_into_org_unit_id, affected_employee_count, transferred_employee_count, created_at, created_by, updated_at)"
                + " values (gen_random_uuid(), 'MERGE', 'ORG_UNIT', " + UNIT + ", date '2026-11-20', 'IN_PROGRESS', " + OTHER_UNIT + ", 1, 0, now(), " + USER + ", now())").update());
        assertThat(jdbc.sql("select count(*) from pg_constraint where conname = 'uk_org_structure_commands_id_drafter'").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 targets must exist: unit, position, relations, head, legal entity")
    void foreignKeys() {
        refused("fk_org_structure_commands_target_org_unit_id", () -> jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind,"
                + " target_kind, target_org_unit_id, effective_date, status, created_at, created_by, updated_at) values (gen_random_uuid(), 'DISSOLVE', 'ORG_UNIT', gen_random_uuid(),"
                + " date '2026-11-10', 'PENDING', now(), " + USER + ", now())").update());
        refused("fk_org_structure_commands_target_position_id", () -> jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind,"
                + " target_kind, target_position_id, effective_date, status, created_at, created_by, updated_at) values (gen_random_uuid(), 'DISABLE', 'POSITION', gen_random_uuid(),"
                + " date '2026-11-10', 'PENDING', now(), " + USER + ", now())").update());
        refused("fk_org_structure_commands_parent", () -> pending("gen_random_uuid()", "REPARENT", ", target_parent_org_unit_id", ", gen_random_uuid()"));
        refused("fk_org_structure_commands_head", () -> pending("gen_random_uuid()", "ASSIGN_HEAD", ", target_head_employee_id", ", gen_random_uuid()"));
        refused("fk_org_structure_commands_receivables_manager", () -> pending("gen_random_uuid()", "ASSIGN_RECEIVABLES_MANAGER", ", target_receivables_manager_employee_id", ", gen_random_uuid()"));
        refused("fk_org_structure_commands_merge_into", () -> pending("gen_random_uuid()", "MERGE", ", merge_into_org_unit_id", ", gen_random_uuid()"));
    }

    /** The employee's PRIMARY root (created when missing), as a SQL expression for the target assignment of a transfer (#711). */
    private String primaryRoot(String employee) {
        jdbc.sql("insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at)"
                + " select uuidv7(), id, 'PRIMARY', now(), now() from organization_workforce.employees where id = " + employee + " on conflict (employee_id) where assignment_kind = 'PRIMARY' do nothing").update();
        return "(select assignment_id from organization_workforce.employee_assignments where employee_id = " + employee + " and assignment_kind = 'PRIMARY')";
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 a personnel command's org structure command must exist")
    void personnelCommandForeignKey() {
        // #908 (DD §3, note §4.5): a transfer made by a merge needs the merge IN_PROGRESS, so the fixture merge is already in progress; the assertions below are unchanged.
        command(COMMAND, "MERGE", "ORG_UNIT", "IN_PROGRESS", ", merge_into_org_unit_id, affected_employee_count, transferred_employee_count", ", " + OTHER_UNIT + ", 1, 0");
        String insert = "insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, target_org_unit_id,"
                + " target_assignment_id, target_assignment_kind, org_structure_command_id, created_at, created_by, updated_at) values (gen_random_uuid(), " + EMPLOYEE
                + ", 'TRANSFER', date '2026-11-10', 'PENDING', " + OTHER_UNIT + ", " + primaryRoot(EMPLOYEE) + ", 'PRIMARY', ";
        refused("fk_personnel_commands_org_structure_command_id", () -> jdbc.sql(insert + "gen_random_uuid(), now(), " + USER + ", now())").update());
        assertThat(jdbc.sql(insert + COMMAND + ", now(), " + USER + ", now())").update()).isEqualTo(1);
    }

    private void bump(String set) {
        jdbc.sql("update organization_workforce.org_structure_commands set " + set + ", updated_at = now(), version = version + 1 where org_structure_command_id = " + COMMAND).update();
    }

    private void moveTo(String from, String to, String extra) {
        jdbc.sql("update organization_workforce.org_structure_commands set status = '" + to + "'" + extra + " where org_structure_command_id = " + COMMAND + " and status = '" + from + "'").update();
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 PENDING moves to IN_PROGRESS (merge, counts with it), EFFECTIVE or WITHDRAWN; IN_PROGRESS only to EFFECTIVE")
    void statusMoves() {
        pending(COMMAND, "MERGE", ", merge_into_org_unit_id", ", " + OTHER_UNIT);
        assertThatThrownBy(() -> bump("target_kind = 'POSITION'")).hasMessageContaining("only the status");
        assertThatThrownBy(() -> bump("effective_date = date '2026-12-01'")).hasMessageContaining("only the status");
        assertThatThrownBy(() -> bump("target_name = 'late'")).hasMessageContaining("only the status");
        assertThatThrownBy(() -> bump("status = 'IN_PROGRESS'")).hasMessageContaining("ck_org_structure_commands_merge_progress");
        bump("status = 'IN_PROGRESS', affected_employee_count = 2, transferred_employee_count = 0");
        bump("transferred_employee_count = 1");
        assertThatThrownBy(() -> bump("transferred_employee_count = 0")).hasMessageContaining("can only increase");
        assertThatThrownBy(() -> bump("affected_employee_count = 5")).hasMessageContaining("is set once");
        assertThatThrownBy(() -> bump("status = 'WITHDRAWN', withdrawn_at = now(), withdrawn_by = " + USER + ", withdrawal_reason = 'stop'")).hasMessageContaining("cannot move");
        assertThatThrownBy(() -> bump("status = 'PENDING', affected_employee_count = null, transferred_employee_count = null")).hasMessageContaining("cannot move");
        bump("status = 'EFFECTIVE', transferred_employee_count = 2, applied_at = now()");
        assertThatThrownBy(() -> bump("status = 'PENDING', applied_at = null")).hasMessageContaining("cannot move");
        assertThatThrownBy(() -> bump("applied_at = now() + interval '1 day'")).hasMessageContaining("is set once");
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 a pending command can be withdrawn once, with the withdrawal columns set once")
    void withdrawal() {
        pending(COMMAND, "DISSOLVE", "", "");
        bump("status = 'WITHDRAWN', withdrawn_at = now(), withdrawn_by = " + USER + ", withdrawal_reason = 'typo'");
        assertThatThrownBy(() -> bump("withdrawal_reason = 'other'")).hasMessageContaining("is set once");
        assertThatThrownBy(() -> bump("status = 'PENDING', withdrawn_at = null, withdrawn_by = null, withdrawal_reason = null")).hasMessageContaining("cannot move");
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 an awaiting command moves to PENDING, EFFECTIVE or WITHDRAWN; nothing moves into the awaiting status after insert")
    void awaitingMoves() {
        command(COMMAND, "ASSIGN_LEGAL_ENTITY", "ORG_UNIT", "AWAITING_ATTRIBUTION_COUNTERSIGN", ", target_legal_entity_id", ", " + LEGAL_ENTITY);
        bump("status = 'PENDING'");
        assertThatThrownBy(() -> bump("status = 'AWAITING_ATTRIBUTION_COUNTERSIGN'")).hasMessageContaining("cannot move");
        bump("status = 'EFFECTIVE', applied_at = now()");
        day = "date '2026-11-30'";
        command(OTHER_COMMAND, "ASSIGN_LEGAL_ENTITY", "ORG_UNIT", "AWAITING_ATTRIBUTION_COUNTERSIGN", ", target_legal_entity_id", ", " + LEGAL_ENTITY);
        jdbc.sql("update organization_workforce.org_structure_commands set status = 'WITHDRAWN', withdrawn_at = now(), withdrawn_by = " + USER
                + ", withdrawal_reason = 'no', updated_at = now(), version = version + 1 where org_structure_command_id = " + OTHER_COMMAND).update();
        day = "date '2026-12-05'";
        pending("gen_random_uuid()", "ASSIGN_LEGAL_ENTITY", ", target_legal_entity_id", ", " + LEGAL_ENTITY);
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.org_structure_commands set status = 'AWAITING_ATTRIBUTION_COUNTERSIGN', updated_at = now()"
                + " where status = 'PENDING' and effective_date = date '2026-12-05'").update()).hasMessageContaining("cannot move");
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 the runtime account cannot delete commands, TRUNCATE has its trigger; the table owner may delete")
    void deleteAndTruncateAreOwnerOnly() throws SQLException {
        pending(COMMAND, "DISSOLVE", "", "");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = '" + OPERATOR_ROLE + "') then create role " + OPERATOR_ROLE + "; end if; end $$");
            s.execute("grant usage on schema organization_workforce to " + OPERATOR_ROLE);
            s.execute("grant select, insert, update, delete, truncate on organization_workforce.org_structure_commands to " + OPERATOR_ROLE);
            s.execute("set role " + OPERATOR_ROLE);
            try {
                for (String statement : List.of("delete from organization_workforce.org_structure_commands")) {
                    assertThatThrownBy(() -> s.execute(statement)).as(statement).hasMessageContaining("append-only");
                }
            } finally {
                s.execute("reset role");
            }
        }
        // A TRUNCATE on this table is stopped by the FK check of the tables that reference it before any trigger runs, so the trigger is checked as defined.
        assertThat(jdbc.sql("select count(*) from pg_trigger where tgname = 'tg_org_structure_commands_append_only_truncate' and (tgtype & 32) <> 0").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("delete from organization_workforce.org_structure_commands").update()).isEqualTo(1);
    }

    private void transition(String from, String to) {
        jdbc.sql("insert into organization_workforce.state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status,"
                + " occurred_on_date, recorded_at, actor_type, actor_id, cause_kind, command_id, correlation_id, created_at, updated_at) values (gen_random_uuid(), 'OrgStructureCommand',"
                + " gen_random_uuid(), " + (from == null ? 1 : 2) + ", " + (from == null ? "null" : "'" + from + "'") + ", '" + to + "', date '2026-11-01', timestamptz '2026-11-01 08:00+08',"
                + " 'USER', " + USER + ", 'COMMAND', gen_random_uuid(), gen_random_uuid(), timestamptz '2026-11-01 08:00+08', timestamptz '2026-11-01 08:00+08')").update();
    }

    @Test
    @DisplayName("IMP-1030-ORG-F1 an org structure command's transitions are exactly the pairs of its state machine")
    void transitions() {
        String awaiting = "AWAITING_ATTRIBUTION_COUNTERSIGN";
        for (String[] pair : new String[][] {{null, "PENDING"}, {null, "EFFECTIVE"}, {null, awaiting}, {"PENDING", "IN_PROGRESS"}, {"PENDING", "EFFECTIVE"}, {"PENDING", "WITHDRAWN"},
                {"IN_PROGRESS", "EFFECTIVE"}, {awaiting, "PENDING"}, {awaiting, "EFFECTIVE"}, {awaiting, "WITHDRAWN"}}) {
            transition(pair[0], pair[1]);
        }
        for (String[] pair : new String[][] {{null, "IN_PROGRESS"}, {null, "WITHDRAWN"}, {"IN_PROGRESS", "WITHDRAWN"}, {"IN_PROGRESS", "PENDING"}, {"PENDING", awaiting},
                {"EFFECTIVE", "PENDING"}, {"WITHDRAWN", "PENDING"}, {"PENDING", "HELD"}}) {
            assertThatThrownBy(() -> transition(pair[0], pair[1])).as(pair[0] + " to " + pair[1]).isInstanceOf(Exception.class);
        }
        assertThat(count("state_transitions")).isEqualTo(10);
    }
}
