package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** IMP-1030-FC-B (#893): the legal entity attribution countersign tables, the personnel command columns they need and the finance head of a legal entity, as the database enforces them. */
class AttributionCountersignTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String EMPLOYEE = "'019a0000-0000-7000-8000-00000000a101'";
    static final String OTHER_EMPLOYEE = "'019a0000-0000-7000-8000-00000000a102'";
    static final String HEAD = "'019a0000-0000-7000-8000-00000000a103'";
    static final String ENTITY_A = "'019a0000-0000-7000-8000-00000000a1f1'";
    static final String ENTITY_B = "'019a0000-0000-7000-8000-00000000a1f2'";
    static final String DRAFTER = "'019a0000-0000-7000-8000-00000000a1a1'";
    static final String DECIDER = "'019a0000-0000-7000-8000-00000000a1a2'";
    static final String OTHER_USER = "'019a0000-0000-7000-8000-00000000a1a3'";
    static final String COMMAND = "'019a0000-0000-7000-8000-00000000a1c1'";
    static final String OTHER_COMMAND = "'019a0000-0000-7000-8000-00000000a1c2'";
    static final String UNIT = "'019a0000-0000-7000-8000-00000000a1b1'";
    static final String CS = "organization_workforce.attribution_countersigns";
    static final String PC = "organization_workforce.personnel_commands";

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void entitiesAndEmployees() {
        jdbc.sql("delete from " + CS).update();
        jdbc.sql("delete from " + PC + " where employee_id in (" + EMPLOYEE + ", " + OTHER_EMPLOYEE + ")").update();
        for (String id : new String[] {EMPLOYEE, OTHER_EMPLOYEE, HEAD}) {
            jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + id + ", 'FCB-" + id.substring(34, 37)
                    + "', 'name', 'EMPLOYED') on conflict do nothing").update();
        }
        jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (" + UNIT + ", 'unit', 'ACTIVE') on conflict do nothing").update();
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (" + ENTITY_A + ", 'QZFCA', 'entity a', 'ACTIVE') on conflict do nothing").update();
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (" + ENTITY_B + ", 'QZFCB', 'entity b', 'ACTIVE') on conflict do nothing").update();
    }

    private String day = "date '2026-12-01'";

    /** A personnel command row; the extra columns override the kind's own. */
    private void command(String id, String employee, String kind, String status, String extraColumns, String extraValues) {
        jdbc.sql(commandSql(id, employee, kind, status, extraColumns, extraValues)).update();
    }

    private String commandSql(String id, String employee, String kind, String status, String extraColumns, String extraValues) {
        return "insert into " + PC + " (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at" + extraColumns + ") values (" + id + ", "
                + employee + ", '" + kind + "', " + day + ", '" + status + "', now(), " + DRAFTER + ", now()" + extraValues + ")";
    }

    private static final String BASIS_COLUMNS = ", countersign_previous_legal_entity_id, countersign_new_legal_entity_id";
    private static final String BASIS = ", " + ENTITY_A + ", " + ENTITY_B;

    private void awaiting(String id, String employee) {
        command(id, employee, "CHANGE_LEGAL_ENTITY", "AWAITING_ATTRIBUTION_COUNTERSIGN", ", target_legal_entity_id" + BASIS_COLUMNS, ", " + ENTITY_B + BASIS);
    }

    private String countersignSql(String command, String employee, String drafter, String kind, int sequence, String entity, String requester, String extraColumns, String extraValues) {
        return "insert into " + CS + " (attribution_countersign_id, personnel_command_id, employee_id, drafted_by, request_kind, request_sequence_number, legal_entity_id, requested_at,"
                + " requested_by, due_on_date, created_at, updated_at" + extraColumns + ") values (gen_random_uuid(), " + command + ", " + employee + ", " + drafter + ", '" + kind + "', " + sequence
                + ", " + entity + ", now(), " + requester + ", date '2026-12-04', now(), now()" + extraValues + ")";
    }

    private void countersign(String kind, int sequence, String entity, String extraColumns, String extraValues) {
        jdbc.sql(countersignSql(COMMAND, EMPLOYEE, DRAFTER, kind, sequence, entity, DRAFTER, extraColumns, extraValues)).update();
    }

    private void refused(String constraint, String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update()).hasMessageContaining(constraint);
    }

    private String decided(String kind, String decider) {
        return ", decision_kind, decided_at, decided_by, decision_audit_entry_id" + (kind.equals("REJECTED") ? ", decision_reason" : "") + "§"
                + ", '" + kind + "', now(), " + decider + ", gen_random_uuid()" + (kind.equals("REJECTED") ? ", 'no'" : "");
    }

    private void countersignDecided(String kind, int sequence, String entity, String decider) {
        String[] parts = decided(kind, decider).split("§");
        countersign("CHANGE", sequence, entity, parts[0], parts[1]);
    }

    // ---- personnel_commands ----

    @Test
    @DisplayName("IMP-1030-FC-B CHANGE_LEGAL_ENTITY needs its target legal entity and no other target; the target is allowed for TRANSFER, CORRECTION, HIRE, REINSTATE and refused for every other kind")
    void legalEntityColumns() {
        command(COMMAND, EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id", ", " + ENTITY_B);
        refused("ck_personnel_commands_legal_entity_columns", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", "", ""));
        refused("ck_personnel_commands_legal_entity_columns", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id, target_org_unit_id", ", " + ENTITY_B + ", " + UNIT));
        refused("ck_personnel_commands_legal_entity_columns", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id, is_target_fee_earner", ", " + ENTITY_B + ", true"));
        refused("ck_personnel_commands_legal_entity_columns", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "SEPARATE", "PENDING", ", target_legal_entity_id", ", " + ENTITY_B));
        refused("ck_personnel_commands_legal_entity_columns", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "REGULARIZE", "PENDING", ", target_legal_entity_id", ", " + ENTITY_B));
        refused("ck_personnel_commands_legal_entity_columns", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "UNPAID_LEAVE_START", "PENDING", ", is_target_on_unpaid_leave, target_legal_entity_id", ", true, " + ENTITY_B));
        command(OTHER_COMMAND, OTHER_EMPLOYEE, "TRANSFER", "PENDING", ", target_org_unit_id, target_legal_entity_id", ", " + UNIT + ", " + ENTITY_B);
        day = "date '2026-12-20'";
        refused("fk_personnel_commands_target_legal_entity_id", commandSql("gen_random_uuid()", OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id", ", gen_random_uuid()"));
    }

    @Test
    @DisplayName("IMP-1030-FC-B the countersign basis is two different legal entities written together, and an awaiting command carries one")
    void countersignBasis() {
        awaiting(COMMAND, EMPLOYEE);
        refused("ck_personnel_commands_countersign_basis", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "AWAITING_ATTRIBUTION_COUNTERSIGN", ", target_legal_entity_id", ", " + ENTITY_B));
        refused("ck_personnel_commands_countersign_basis", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id, countersign_new_legal_entity_id", ", " + ENTITY_B + ", " + ENTITY_B));
        refused("ck_personnel_commands_countersign_basis", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id, countersign_previous_legal_entity_id", ", " + ENTITY_B + ", " + ENTITY_A));
        refused("ck_personnel_commands_countersign_basis", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id" + BASIS_COLUMNS, ", " + ENTITY_B + ", " + ENTITY_A + ", " + ENTITY_A));
        refused("fk_personnel_commands_countersign_new_legal_entity_id", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id" + BASIS_COLUMNS, ", " + ENTITY_B + ", " + ENTITY_A + ", gen_random_uuid()"));
        refused("fk_personnel_commands_countersign_previous_legal_entity_id", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id" + BASIS_COLUMNS, ", " + ENTITY_B + ", gen_random_uuid(), " + ENTITY_B));
        command(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "PENDING", ", target_legal_entity_id" + BASIS_COLUMNS, ", " + ENTITY_B + BASIS);
    }

    @Test
    @DisplayName("IMP-1030-FC-B a CORRECTION is immediate, or awaiting (or withdrawn) with a countersign basis; one open command per employee and day counts the awaiting one")
    void correctionAndOpenPerDay() {
        command(COMMAND, EMPLOYEE, "CORRECTION", "EFFECTIVE", ", applied_at", ", now()");
        refused("ck_personnel_commands_correction_immediate", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CORRECTION", "PENDING", "", ""));
        refused("ck_personnel_commands_correction_immediate", commandSql(OTHER_COMMAND, OTHER_EMPLOYEE, "CORRECTION", "AWAITING_ATTRIBUTION_COUNTERSIGN", ", target_legal_entity_id", ", " + ENTITY_B));
        command(OTHER_COMMAND, OTHER_EMPLOYEE, "CORRECTION", "AWAITING_ATTRIBUTION_COUNTERSIGN", ", target_legal_entity_id" + BASIS_COLUMNS, ", " + ENTITY_B + BASIS);
        day = "date '2026-12-02'";
        String awaitingId = "'019a0000-0000-7000-8000-00000000a1c3'";
        awaiting(awaitingId, EMPLOYEE);
        refused("uk_personnel_commands_open_per_day", commandSql("gen_random_uuid()", EMPLOYEE, "TRANSFER", "PENDING", ", target_org_unit_id", ", " + UNIT));
        refused("uk_personnel_commands_open_per_day", commandSql("gen_random_uuid()", EMPLOYEE, "CHANGE_LEGAL_ENTITY", "AWAITING_ATTRIBUTION_COUNTERSIGN", ", target_legal_entity_id" + BASIS_COLUMNS, ", " + ENTITY_B + BASIS));
        jdbc.sql("update " + PC + " set status = 'WITHDRAWN', withdrawn_at = now(), withdrawn_by = " + DRAFTER + ", withdrawal_reason = 'no', updated_at = now() where personnel_command_id = " + awaitingId).update();
        command("gen_random_uuid()", EMPLOYEE, "TRANSFER", "PENDING", ", target_org_unit_id", ", " + UNIT);
    }

    @Test
    @DisplayName("IMP-1030-FC-B an awaiting command moves to PENDING, EFFECTIVE or WITHDRAWN only; no status moves into awaiting after insert; the countersign basis never changes")
    void awaitingTransitions() {
        awaiting(COMMAND, EMPLOYEE);
        assertThatThrownBy(() -> jdbc.sql("update " + PC + " set status = 'HELD', updated_at = now() where personnel_command_id = " + COMMAND).update()).hasMessageContaining("cannot move");
        assertThatThrownBy(() -> jdbc.sql("update " + PC + " set countersign_new_legal_entity_id = " + ENTITY_A + ", countersign_previous_legal_entity_id = " + ENTITY_B
                + ", updated_at = now() where personnel_command_id = " + COMMAND).update()).hasMessageContaining("only the status");
        jdbc.sql("update " + PC + " set status = 'PENDING', updated_at = now() where personnel_command_id = " + COMMAND).update();
        assertThatThrownBy(() -> jdbc.sql("update " + PC + " set status = 'AWAITING_ATTRIBUTION_COUNTERSIGN', updated_at = now() where personnel_command_id = " + COMMAND).update())
                .hasMessageContaining("cannot move");
        command(OTHER_COMMAND, OTHER_EMPLOYEE, "CHANGE_LEGAL_ENTITY", "AWAITING_ATTRIBUTION_COUNTERSIGN", ", target_legal_entity_id" + BASIS_COLUMNS, ", " + ENTITY_B + BASIS);
        jdbc.sql("update " + PC + " set status = 'EFFECTIVE', applied_at = now(), updated_at = now() where personnel_command_id = " + OTHER_COMMAND).update();
        day = "date '2026-12-03'";
        awaiting("gen_random_uuid()", OTHER_EMPLOYEE);
        jdbc.sql("update " + PC + " set status = 'WITHDRAWN', withdrawn_at = now(), withdrawn_by = " + DRAFTER + ", withdrawal_reason = 'no', updated_at = now() where employee_id = " + OTHER_EMPLOYEE
                + " and status = 'AWAITING_ATTRIBUTION_COUNTERSIGN'").update();
    }

    // ---- attribution_countersigns ----

    @Test
    @DisplayName("IMP-1030-FC-B the drafter is the command's own created_by, enforced by the composite key; unknown legal entities and request kinds are refused")
    void commandKeyAndKinds() {
        awaiting(COMMAND, EMPLOYEE);
        countersign("CHANGE", 1, ENTITY_A, "", "");
        refused("fk_attribution_countersigns_command", countersignSql(COMMAND, EMPLOYEE, OTHER_USER, "CHANGE", 1, ENTITY_B, OTHER_USER, "", ""));
        refused("fk_attribution_countersigns_command", countersignSql(COMMAND, OTHER_EMPLOYEE, DRAFTER, "CHANGE", 1, ENTITY_B, DRAFTER, "", ""));
        refused("fk_attribution_countersigns_command", countersignSql("gen_random_uuid()", EMPLOYEE, DRAFTER, "CHANGE", 1, ENTITY_B, DRAFTER, "", ""));
        refused("fk_attribution_countersigns_legal_entity_id", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "CHANGE", 1, "gen_random_uuid()", DRAFTER, "", ""));
        refused("ck_attribution_countersigns_request_kind", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "OTHER", 1, ENTITY_B, DRAFTER, "", ""));
        refused("ck_attribution_countersigns_request_sequence_number", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 0, ENTITY_B, DRAFTER, "", ""));
        refused("ck_attribution_countersigns_due_on_date_finite", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 1, ENTITY_B, DRAFTER, "", "").replace("date '2026-12-04'", "date 'infinity'"));
        countersign("CHANGE", 1, ENTITY_B, "", "");
    }

    @Test
    @DisplayName("IMP-1030-FC-B a change is requested by the drafter with sequence 1; a withdrawal by anyone with 1, 2, ...")
    void changeRequester() {
        awaiting(COMMAND, EMPLOYEE);
        refused("ck_attribution_countersigns_change_requester", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "CHANGE", 1, ENTITY_A, OTHER_USER, "", ""));
        refused("ck_attribution_countersigns_change_requester", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "CHANGE", 2, ENTITY_A, DRAFTER, "", ""));
        countersign("CHANGE", 1, ENTITY_A, "", "");
        jdbc.sql(countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 1, ENTITY_A, OTHER_USER, "", "")).update();
    }

    @Test
    @DisplayName("IMP-1030-FC-B one open request per command, kind and legal entity (a decided one frees the slot), and one row per request sequence number")
    void uniqueness() {
        awaiting(COMMAND, EMPLOYEE);
        countersign("CHANGE", 1, ENTITY_A, "", "");
        refused("uk_attribution_countersigns_request", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "CHANGE", 1, ENTITY_A, DRAFTER, ", decision_kind, decided_at, decided_by, decision_audit_entry_id",
                ", 'APPROVED', now(), " + DECIDER + ", gen_random_uuid()"));
        jdbc.sql(countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 1, ENTITY_A, OTHER_USER, "", "")).update();
        refused("uk_attribution_countersigns_open", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 2, ENTITY_A, OTHER_USER, "", ""));
        bump("decision_kind = 'REJECTED', decided_at = now(), decided_by = " + DECIDER + ", decision_reason = 'not now', decision_audit_entry_id = gen_random_uuid()",
                "request_kind = 'WITHDRAWAL' and legal_entity_id = " + ENTITY_A);
        jdbc.sql(countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 2, ENTITY_A, OTHER_USER, "", "")).update();
        jdbc.sql(countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 1, ENTITY_B, OTHER_USER, "", "")).update();
    }

    @Test
    @DisplayName("IMP-1030-FC-B the decider is neither the drafter nor the requester, even when the requester is someone else; decision columns are written together; LAPSED has no decider; REJECTED needs a reason")
    void decisionColumns() {
        awaiting(COMMAND, EMPLOYEE);
        countersignDecided("APPROVED", 1, ENTITY_A, DECIDER);
        String withdrawalByOther = ", decision_kind, decided_at, decided_by, decision_audit_entry_id";
        refused("ck_attribution_countersigns_not_drafter", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 1, ENTITY_A, OTHER_USER, withdrawalByOther, ", 'APPROVED', now(), " + DRAFTER + ", gen_random_uuid()"));
        refused("ck_attribution_countersigns_not_drafter", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 1, ENTITY_A, OTHER_USER, withdrawalByOther, ", 'APPROVED', now(), " + OTHER_USER + ", gen_random_uuid()"));
        jdbc.sql(countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 1, ENTITY_A, OTHER_USER, withdrawalByOther, ", 'APPROVED', now(), " + DECIDER + ", gen_random_uuid()")).update();
        refused("ck_attribution_countersigns_decision", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 2, ENTITY_A, OTHER_USER, ", decision_kind", ", 'APPROVED'"));
        refused("ck_attribution_countersigns_decision", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 2, ENTITY_A, OTHER_USER, ", decided_at, decided_by", ", now(), " + DECIDER));
        refused("ck_attribution_countersigns_decision", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 2, ENTITY_A, OTHER_USER, ", decision_kind, decided_at, decision_audit_entry_id", ", 'APPROVED', now(), gen_random_uuid()"));
        refused("ck_attribution_countersigns_decision", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 2, ENTITY_A, OTHER_USER, ", decision_kind, decided_at, decided_by, decision_audit_entry_id",
                ", 'LAPSED', now(), " + DECIDER + ", gen_random_uuid()"));
        jdbc.sql(countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 2, ENTITY_A, OTHER_USER, ", decision_kind, decided_at, decision_audit_entry_id", ", 'LAPSED', now(), gen_random_uuid()")).update();
        refused("ck_attribution_countersigns_reason", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 3, ENTITY_A, OTHER_USER, withdrawalByOther, ", 'REJECTED', now(), " + DECIDER + ", gen_random_uuid()"));
        refused("ck_attribution_countersigns_reason", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 3, ENTITY_A, OTHER_USER, withdrawalByOther + ", decision_reason", ", 'REJECTED', now(), " + DECIDER + ", gen_random_uuid(), ' '"));
        refused("ck_attribution_countersigns_reason", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 3, ENTITY_A, OTHER_USER, ", decision_reason", ", 'a reason before any decision'"));
        refused("ck_attribution_countersigns_decision_kind", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 3, ENTITY_A, OTHER_USER, withdrawalByOther, ", 'MAYBE', now(), " + DECIDER + ", gen_random_uuid()"));
        refused("ck_attribution_countersigns_escalation_level", countersignSql(COMMAND, EMPLOYEE, DRAFTER, "WITHDRAWAL", 3, ENTITY_A, OTHER_USER, ", escalation_level", ", 4"));
        countersign("CHANGE", 1, ENTITY_B, ", escalation_level", ", 3");
    }

    private void bump(String set, String where) {
        jdbc.sql("update " + CS + " set " + set + ", updated_at = now(), version = version + 1 where " + where).update();
    }

    @Test
    @DisplayName("IMP-1030-FC-B the decision is written once, the escalation level only goes up while open, every other column stays; a decided row never changes")
    void appendOnly() {
        awaiting(COMMAND, EMPLOYEE);
        countersign("CHANGE", 1, ENTITY_A, "", "");
        countersign("CHANGE", 1, ENTITY_B, "", "");
        bump("escalation_level = 1", "legal_entity_id = " + ENTITY_A);
        assertThatThrownBy(() -> bump("escalation_level = 0", "legal_entity_id = " + ENTITY_A)).hasMessageContaining("can only increase");
        bump("escalation_level = 2", "legal_entity_id = " + ENTITY_A);
        assertThatThrownBy(() -> bump("due_on_date = date '2026-12-31'", "legal_entity_id = " + ENTITY_A)).hasMessageContaining("only the decision columns");
        assertThatThrownBy(() -> bump("requested_by = " + OTHER_USER, "legal_entity_id = " + ENTITY_A)).hasMessageContaining("only the decision columns");
        bump("decision_kind = 'APPROVED', decided_at = now(), decided_by = " + DECIDER + ", decision_audit_entry_id = gen_random_uuid()", "legal_entity_id = " + ENTITY_A);
        assertThatThrownBy(() -> bump("decision_kind = 'REJECTED', decision_reason = 'changed my mind'", "legal_entity_id = " + ENTITY_A)).hasMessageContaining("set once");
        assertThatThrownBy(() -> bump("decided_by = " + OTHER_USER, "legal_entity_id = " + ENTITY_A)).hasMessageContaining("set once");
        assertThatThrownBy(() -> bump("decision_audit_entry_id = gen_random_uuid()", "legal_entity_id = " + ENTITY_A)).hasMessageContaining("set once");
        assertThatThrownBy(() -> bump("escalation_level = 3", "legal_entity_id = " + ENTITY_A)).hasMessageContaining("can only increase");
        assertThatThrownBy(() -> bump("decision_kind = 'APPROVED'", "legal_entity_id = " + ENTITY_B)).hasMessageContaining("ck_attribution_countersigns_decision");
        bump("decision_kind = 'REJECTED', decided_at = now(), decided_by = " + DECIDER + ", decision_audit_entry_id = gen_random_uuid(), decision_reason = 'no'", "legal_entity_id = " + ENTITY_B);
    }

    @Test
    @DisplayName("IMP-1030-FC-B the runtime account cannot delete countersigns, TRUNCATE has its trigger; the table owner may delete")
    void deleteAndTruncateAreOwnerOnly() throws SQLException {
        awaiting(COMMAND, EMPLOYEE);
        countersign("CHANGE", 1, ENTITY_A, "", "");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'fcb_runtime') then create role fcb_runtime; end if; end $$");
            s.execute("grant usage on schema organization_workforce to fcb_runtime");
            s.execute("grant select, insert, update, delete on " + CS + " to fcb_runtime");
            s.execute("set role fcb_runtime");
            try {
                assertThatThrownBy(() -> s.execute("delete from " + CS)).hasMessageContaining("append-only");
            } finally {
                s.execute("reset role");
            }
        }
        assertThat(jdbc.sql("select count(*) from pg_trigger where tgname = 'tg_attribution_countersigns_append_only_truncate' and (tgtype & 32) <> 0").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("delete from " + CS).update()).isEqualTo(1);
    }

    // ---- state_transitions ----

    private void transition(String type, String from, String to) {
        jdbc.sql("insert into organization_workforce.state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status,"
                + " occurred_on_date, recorded_at, actor_type, actor_id, cause_kind, command_id, correlation_id, created_at, updated_at) values (gen_random_uuid(), '" + type + "',"
                + " gen_random_uuid(), " + (from == null ? 1 : 2) + ", " + (from == null ? "null" : "'" + from + "'") + ", '" + to + "', date '2026-11-01', timestamptz '2026-11-01 08:00+08',"
                + " 'USER', " + DRAFTER + ", 'COMMAND', gen_random_uuid(), gen_random_uuid(), timestamptz '2026-11-01 08:00+08', timestamptz '2026-11-01 08:00+08')").update();
    }

    @Test
    @DisplayName("IMP-1030-FC-B state transitions: a countersign request opens and is decided once; a personnel command enters awaiting at creation and leaves it to PENDING, EFFECTIVE or WITHDRAWN")
    void stateTransitions() {
        String[][] allowed = {{"AttributionCountersign", null, "OPEN"}, {"AttributionCountersign", "OPEN", "APPROVED"}, {"AttributionCountersign", "OPEN", "REJECTED"},
                {"AttributionCountersign", "OPEN", "LAPSED"}, {"PersonnelCommand", null, "AWAITING_ATTRIBUTION_COUNTERSIGN"},
                {"PersonnelCommand", "AWAITING_ATTRIBUTION_COUNTERSIGN", "PENDING"}, {"PersonnelCommand", "AWAITING_ATTRIBUTION_COUNTERSIGN", "EFFECTIVE"},
                {"PersonnelCommand", "AWAITING_ATTRIBUTION_COUNTERSIGN", "WITHDRAWN"}, {"PersonnelCommand", "PENDING", "EFFECTIVE"}, {"OrgStructureCommand", "PENDING", "IN_PROGRESS"}};
        for (String[] pair : allowed) {
            transition(pair[0], pair[1], pair[2]);
        }
        String[][] refused = {{"AttributionCountersign", null, "APPROVED"}, {"AttributionCountersign", "OPEN", "PENDING"}, {"AttributionCountersign", "APPROVED", "OPEN"},
                {"AttributionCountersign", "PENDING", "OPEN"}, {"PersonnelCommand", "AWAITING_ATTRIBUTION_COUNTERSIGN", "HELD"}, {"PersonnelCommand", "PENDING", "AWAITING_ATTRIBUTION_COUNTERSIGN"},
                {"PersonnelCommand", null, "OPEN"}, {"SeparationHold", null, "OPEN"}};
        for (String[] pair : refused) {
            assertThatThrownBy(() -> transition(pair[0], pair[1], pair[2])).as(pair[0] + " " + pair[1] + " to " + pair[2]).isInstanceOf(Exception.class);
        }
    }

    // ---- legal_entity_versions: finance head ----

    private void version(String entity, String number, String from, String to, String head) {
        jdbc.sql("insert into organization_workforce.legal_entity_versions (legal_entity_version_id, legal_entity_id, effective_from_date, revision_number, effective_to_date, code, name, status,"
                + " finance_head_employee_id) values (gen_random_uuid(), " + entity + ", date '" + from + "', " + number + ", " + (to == null ? "null" : "date '" + to + "'")
                + ", 'QZFCA', 'entity a', 'ACTIVE', " + head + ")").update();
    }

    @Test
    @DisplayName("IMP-1030-FC-B the finance head of a legal entity is a versioned, nullable employee reference: an unknown employee is refused, a closed version keeps its head, a new version appended after closing may name another or none")
    void financeHead() {
        jdbc.sql("delete from organization_workforce.legal_entity_versions where legal_entity_id in (" + ENTITY_A + ", " + ENTITY_B + ")").update();
        assertThatThrownBy(() -> version(ENTITY_B, "0", "2030-01-01", null, "gen_random_uuid()")).hasMessageContaining("fk_legal_entity_versions_finance_head_employee_id");
        version(ENTITY_A, "0", "2030-01-01", null, HEAD);
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.legal_entity_versions set finance_head_employee_id = " + EMPLOYEE + " where legal_entity_id = " + ENTITY_A
                + " and effective_to_date is null").update()).hasMessageContaining("an open version row can only be closed");
        jdbc.sql("update organization_workforce.legal_entity_versions set effective_to_date = date '2031-01-01' where legal_entity_id = " + ENTITY_A + " and effective_to_date is null").update();
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.legal_entity_versions set finance_head_employee_id = " + EMPLOYEE + " where legal_entity_id = " + ENTITY_A
                + " and effective_to_date is not null").update()).hasMessageContaining("closed version");
        version(ENTITY_A, "0", "2031-01-01", null, EMPLOYEE);
        jdbc.sql("update organization_workforce.legal_entity_versions set effective_to_date = date '2032-01-01' where legal_entity_id = " + ENTITY_A + " and effective_to_date is null").update();
        version(ENTITY_A, "0", "2032-01-01", null, "null");
    }
}
