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

/** IMP-1030-ORG-F3 (#908): the countersign basis of org structure commands, the org parent of the countersign table and the merge transfer guard, as the database enforces them. */
class OrgCountersignTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String EMPLOYEE = "'019a0000-0000-7000-8000-00000000b101'";
    static final String OTHER_EMPLOYEE = "'019a0000-0000-7000-8000-00000000b102'";
    static final String ENTITY_A = "'019a0000-0000-7000-8000-00000000b1f1'";
    static final String ENTITY_B = "'019a0000-0000-7000-8000-00000000b1f2'";
    static final String UNIT = "'019a0000-0000-7000-8000-00000000b1b1'";
    static final String OTHER_UNIT = "'019a0000-0000-7000-8000-00000000b1b2'";
    static final String DRAFTER = "'019a0000-0000-7000-8000-00000000b1a1'";
    static final String DECIDER = "'019a0000-0000-7000-8000-00000000b1a2'";
    static final String OTHER_USER = "'019a0000-0000-7000-8000-00000000b1a3'";
    static final String MERGE = "'019a0000-0000-7000-8000-00000000b1c1'";
    static final String OTHER_MERGE = "'019a0000-0000-7000-8000-00000000b1c2'";
    static final String PERSONNEL_COMMAND = "'019a0000-0000-7000-8000-00000000b1d1'";
    static final String BASES = "organization_workforce.attribution_countersign_bases";
    static final String CS = "organization_workforce.attribution_countersigns";
    static final String OSC = "organization_workforce.org_structure_commands";
    static final String PC = "organization_workforce.personnel_commands";

    @Autowired
    DataSource dataSource;

    @BeforeEach
    void rows() {
        jdbc.sql("delete from " + CS).update();
        jdbc.sql("delete from " + BASES).update();
        jdbc.sql("delete from " + PC + " where employee_id in (" + EMPLOYEE + ", " + OTHER_EMPLOYEE + ")").update();
        VersionTables.truncate(jdbc, "truncate table organization_workforce.state_transitions");
        jdbc.sql("delete from " + OSC + " where target_org_unit_id in (" + UNIT + ", " + OTHER_UNIT + ")").update();
        for (String id : new String[] {EMPLOYEE, OTHER_EMPLOYEE}) {
            jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + id + ", 'FCF-" + id.substring(34, 37)
                    + "', 'name', 'EMPLOYED') on conflict do nothing").update();
        }
        for (String id : new String[] {UNIT, OTHER_UNIT}) {
            jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (" + id + ", 'unit', 'ACTIVE') on conflict do nothing").update();
        }
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (" + ENTITY_A + ", 'QZFFA', 'entity a', 'ACTIVE') on conflict do nothing").update();
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (" + ENTITY_B + ", 'QZFFB', 'entity b', 'ACTIVE') on conflict do nothing").update();
    }

    private String day = "date '2026-12-01'";

    private String mergeSql(String id, String status) {
        boolean progressed = status.equals("IN_PROGRESS");
        return "insert into " + OSC + " (org_structure_command_id, command_kind, target_kind, target_org_unit_id, merge_into_org_unit_id, effective_date, status, created_at, created_by, updated_at"
                + (progressed ? ", affected_employee_count, transferred_employee_count" : "") + ") values (" + id + ", 'MERGE', 'ORG_UNIT', " + UNIT + ", " + OTHER_UNIT + ", " + day + ", '" + status
                + "', now(), " + DRAFTER + ", now()" + (progressed ? ", 2, 0" : "") + ")";
    }

    private void merge(String id, String status) {
        jdbc.sql(mergeSql(id, status)).update();
    }

    private void dissolve(String id) {
        jdbc.sql("insert into " + OSC + " (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status, created_at, created_by, updated_at) values (" + id
                + ", 'DISSOLVE', 'ORG_UNIT', " + UNIT + ", date '2026-12-15', 'PENDING', now(), " + DRAFTER + ", now())").update();
    }

    /** The employee's PRIMARY root (created when missing), as a SQL expression for the target assignment of a transfer (#711). */
    private String primaryRoot(String employee) {
        jdbc.sql("insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at)"
                + " select uuidv7(), id, 'PRIMARY', now(), now() from organization_workforce.employees where id = " + employee + " on conflict (employee_id) where assignment_kind = 'PRIMARY' do nothing").update();
        return "(select assignment_id from organization_workforce.employee_assignments where employee_id = " + employee + " and assignment_kind = 'PRIMARY')";
    }

    private String transferSql(String employee, String orgCommand, String status) {
        return "insert into " + PC + " (personnel_command_id, employee_id, command_kind, effective_date, status, target_org_unit_id, target_assignment_id, target_assignment_kind, org_structure_command_id, created_at, created_by, updated_at"
                + (status.equals("AWAITING_ATTRIBUTION_COUNTERSIGN") ? ", countersign_previous_legal_entity_id, countersign_new_legal_entity_id" : "") + ") values (gen_random_uuid(), "
                + employee + ", 'TRANSFER', " + day + ", '" + status + "', " + OTHER_UNIT + ", " + primaryRoot(employee) + ", 'PRIMARY', " + orgCommand + ", now(), " + DRAFTER + ", now()"
                + (status.equals("AWAITING_ATTRIBUTION_COUNTERSIGN") ? ", " + ENTITY_A + ", " + ENTITY_B : "") + ")";
    }

    private String orgCountersignSql(String command, String drafter, String kind, int sequence, String entity, String requester, String extraColumns, String extraValues) {
        return "insert into " + CS + " (attribution_countersign_id, org_structure_command_id, drafted_by, request_kind, request_sequence_number, legal_entity_id, requested_at, requested_by,"
                + " due_on_date, created_at, updated_at" + extraColumns + ") values (gen_random_uuid(), " + command + ", " + drafter + ", '" + kind + "', " + sequence + ", " + entity
                + ", now(), " + requester + ", date '2026-12-04', now(), now()" + extraValues + ")";
    }

    private static final String APPROVED_COLUMNS = ", decision_kind, decided_at, decided_by, decision_audit_entry_id";

    private void change(String command, String entity, String decision) {
        String values = decision == null ? "" : ", '" + decision + "', now(), " + DECIDER + ", gen_random_uuid()" + (decision.equals("REJECTED") ? ", 'no'" : "");
        String columns = decision == null ? "" : APPROVED_COLUMNS + (decision.equals("REJECTED") ? ", decision_reason" : "");
        if ("LAPSED".equals(decision)) {
            columns = ", decision_kind, decided_at, decision_audit_entry_id";
            values = ", 'LAPSED', now(), gen_random_uuid()";
        }
        jdbc.sql(orgCountersignSql(command, DRAFTER, "CHANGE", 1, entity, DRAFTER, columns, values)).update();
    }

    private void basis(String command, String employee, String previous, String next) {
        jdbc.sql(basisSql(command, employee, previous, next)).update();
    }

    private String basisSql(String command, String employee, String previous, String next) {
        return "insert into " + BASES + " (attribution_countersign_basis_id, org_structure_command_id, employee_id, previous_legal_entity_id, new_legal_entity_id, created_at, updated_at) values (gen_random_uuid(), "
                + command + ", " + employee + ", " + previous + ", " + next + ", now(), now())";
    }

    private void refused(String constraint, String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update()).hasMessageContaining(constraint);
    }

    // ---- attribution_countersign_bases ----

    @Test
    @DisplayName("IMP-1030-ORG-F3 one basis row per employee and command; previous and new legal entity differ and exist; the keys are enforced")
    void basisConstraints() {
        merge(MERGE, "PENDING");
        basis(MERGE, EMPLOYEE, ENTITY_A, ENTITY_B);
        basis(MERGE, OTHER_EMPLOYEE, ENTITY_B, ENTITY_A);
        refused("uk_attribution_countersign_bases_command_employee", basisSql(MERGE, EMPLOYEE, ENTITY_B, ENTITY_A));
        refused("ck_attribution_countersign_bases_changed", basisSql(MERGE, "'019a0000-0000-7000-8000-00000000b103'", ENTITY_A, ENTITY_A));
        day = "date '2026-12-02'";
        merge(OTHER_MERGE, "PENDING");
        refused("fk_attribution_countersign_bases_employee_id", basisSql(OTHER_MERGE, "gen_random_uuid()", ENTITY_A, ENTITY_B));
        refused("fk_attribution_countersign_bases_previous_legal_entity_id", basisSql(OTHER_MERGE, EMPLOYEE, "gen_random_uuid()", ENTITY_B));
        refused("fk_attribution_countersign_bases_new_legal_entity_id", basisSql(OTHER_MERGE, EMPLOYEE, ENTITY_A, "gen_random_uuid()"));
        refused("fk_attribution_countersign_bases_org_structure_command_id", basisSql("gen_random_uuid()", EMPLOYEE, ENTITY_A, ENTITY_B));
        basis(OTHER_MERGE, EMPLOYEE, ENTITY_A, ENTITY_B);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F3 a basis row never changes; the runtime account cannot delete it and TRUNCATE has its trigger; the owner may delete")
    void basisAppendOnly() throws SQLException {
        merge(MERGE, "PENDING");
        basis(MERGE, EMPLOYEE, ENTITY_A, ENTITY_B);
        assertThatThrownBy(() -> jdbc.sql("update " + BASES + " set new_legal_entity_id = " + ENTITY_A + ", previous_legal_entity_id = " + ENTITY_B).update()).hasMessageContaining("update");
        assertThatThrownBy(() -> jdbc.sql("update " + BASES + " set created_at = now()").update()).hasMessageContaining("append-only");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'fcf_runtime') then create role fcf_runtime; end if; end $$");
            s.execute("grant usage on schema organization_workforce to fcf_runtime");
            s.execute("grant select, insert, update, delete on " + BASES + " to fcf_runtime");
            s.execute("set role fcf_runtime");
            try {
                assertThatThrownBy(() -> s.execute("delete from " + BASES)).hasMessageContaining("append-only");
            } finally {
                s.execute("reset role");
            }
        }
        assertThat(jdbc.sql("select count(*) from pg_trigger where tgname = 'tg_attribution_countersign_bases_append_only_truncate' and (tgtype & 32) <> 0").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("delete from " + BASES).update()).isEqualTo(1);
    }

    // ---- attribution_countersigns with an org parent ----

    @Test
    @DisplayName("IMP-1030-ORG-F3 a countersign has exactly one parent; an org row has no employee; the org parent key binds the drafter to the command's created_by")
    void parents() {
        merge(MERGE, "IN_PROGRESS");
        change(MERGE, ENTITY_A, null);
        refused("ck_attribution_countersigns_parent", "insert into " + CS + " (attribution_countersign_id, drafted_by, request_kind, request_sequence_number, legal_entity_id, requested_at, requested_by,"
                + " due_on_date, created_at, updated_at) values (gen_random_uuid(), " + DRAFTER + ", 'CHANGE', 1, " + ENTITY_B + ", now(), " + DRAFTER + ", date '2026-12-04', now(), now())");
        refused("ck_attribution_countersigns_parent", orgCountersignSql(MERGE, DRAFTER, "CHANGE", 1, ENTITY_B, DRAFTER, ", personnel_command_id, employee_id", ", " + PERSONNEL_COMMAND + ", " + EMPLOYEE));
        refused("ck_attribution_countersigns_parent", orgCountersignSql(MERGE, DRAFTER, "CHANGE", 1, ENTITY_B, DRAFTER, ", employee_id", ", " + EMPLOYEE));
        refused("fk_attribution_countersigns_org_command", orgCountersignSql(MERGE, OTHER_USER, "CHANGE", 1, ENTITY_B, OTHER_USER, "", ""));
        refused("fk_attribution_countersigns_org_command", orgCountersignSql("gen_random_uuid()", DRAFTER, "CHANGE", 1, ENTITY_B, DRAFTER, "", ""));
        change(MERGE, ENTITY_B, null);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F3 one open request per org command, kind and legal entity (a decided one frees the slot); one row per sequence number; the decider is not the drafter")
    void orgUniqueness() {
        merge(MERGE, "IN_PROGRESS");
        change(MERGE, ENTITY_A, null);
        refused("uk_attribution_countersigns_request_org", orgCountersignSql(MERGE, DRAFTER, "CHANGE", 1, ENTITY_A, DRAFTER, APPROVED_COLUMNS, ", 'APPROVED', now(), " + DECIDER + ", gen_random_uuid()"));
        jdbc.sql(orgCountersignSql(MERGE, DRAFTER, "WITHDRAWAL", 1, ENTITY_A, OTHER_USER, "", "")).update();
        refused("uk_attribution_countersigns_open_org", orgCountersignSql(MERGE, DRAFTER, "WITHDRAWAL", 2, ENTITY_A, OTHER_USER, "", ""));
        jdbc.sql("update " + CS + " set decision_kind = 'REJECTED', decided_at = now(), decided_by = " + DECIDER + ", decision_reason = 'no', decision_audit_entry_id = gen_random_uuid(),"
                + " updated_at = now(), version = version + 1 where request_kind = 'WITHDRAWAL'").update();
        jdbc.sql(orgCountersignSql(MERGE, DRAFTER, "WITHDRAWAL", 2, ENTITY_A, OTHER_USER, "", "")).update();
        refused("ck_attribution_countersigns_not_drafter", orgCountersignSql(MERGE, DRAFTER, "WITHDRAWAL", 1, ENTITY_B, OTHER_USER, APPROVED_COLUMNS, ", 'APPROVED', now(), " + DRAFTER + ", gen_random_uuid()"));
    }

    @Test
    @DisplayName("IMP-1030-ORG-F3 the personnel parent keeps its own keys: one open and one request row per personnel command")
    void personnelKeysRemain() {
        jdbc.sql("insert into " + PC + " (personnel_command_id, employee_id, command_kind, effective_date, status, target_legal_entity_id, countersign_previous_legal_entity_id,"
                + " countersign_new_legal_entity_id, created_at, created_by, updated_at) values (" + PERSONNEL_COMMAND + ", " + EMPLOYEE + ", 'CHANGE_LEGAL_ENTITY', date '2026-12-09',"
                + " 'AWAITING_ATTRIBUTION_COUNTERSIGN', " + ENTITY_B + ", " + ENTITY_A + ", " + ENTITY_B + ", now(), " + DRAFTER + ", now())").update();
        String insert = "insert into " + CS + " (attribution_countersign_id, personnel_command_id, employee_id, drafted_by, request_kind, request_sequence_number, legal_entity_id, requested_at,"
                + " requested_by, due_on_date, created_at, updated_at) values (gen_random_uuid(), " + PERSONNEL_COMMAND + ", " + EMPLOYEE + ", " + DRAFTER + ", 'CHANGE', 1, " + ENTITY_A
                + ", now(), " + DRAFTER + ", date '2026-12-04', now(), now())";
        jdbc.sql(insert).update();
        refused("uk_attribution_countersigns_", insert);
        refused("fk_attribution_countersigns_command", insert.replace(ENTITY_A, ENTITY_B).replace("'CHANGE', 1", "'WITHDRAWAL', 1").replace(DRAFTER + ", 'WITHDRAWAL'", OTHER_USER + ", 'WITHDRAWAL'"));
    }

    // ---- the merge transfer guard ----

    @Test
    @DisplayName("IMP-1030-ORG-F3 guard: a transfer made by an org command needs a merge in progress; a pending merge, a non-merge command and a missing command are refused; a transfer that itself awaits countersign passes")
    void mergeMustBeInProgress() {
        merge(MERGE, "PENDING");
        refused("needs a merge in progress", transferSql(EMPLOYEE, MERGE, "PENDING"));
        dissolve(OTHER_MERGE);
        refused("needs a merge in progress", transferSql(EMPLOYEE, OTHER_MERGE, "PENDING"));
        refused("fk_personnel_commands_org_structure_command_id", transferSql(EMPLOYEE, "gen_random_uuid()", "PENDING"));
        jdbc.sql(transferSql(EMPLOYEE, MERGE, "AWAITING_ATTRIBUTION_COUNTERSIGN")).update();
    }

    @Test
    @DisplayName("IMP-1030-ORG-F3 guard: a merge in progress with an open, rejected or lapsed change request, or with basis rows but no change requests, refuses the transfer; all approved, or nothing to countersign, passes")
    void mergeNeedsEveryChangeApproved() {
        merge(MERGE, "IN_PROGRESS");
        jdbc.sql(transferSql(EMPLOYEE, MERGE, "PENDING")).update();
        jdbc.sql("delete from " + PC + " where employee_id = " + EMPLOYEE).update();
        basis(MERGE, EMPLOYEE, ENTITY_A, ENTITY_B);
        refused("needs its attribution countersigns", transferSql(EMPLOYEE, MERGE, "PENDING"));
        change(MERGE, ENTITY_A, null);
        refused("every attribution countersign of the merge approved", transferSql(EMPLOYEE, MERGE, "PENDING"));
        jdbc.sql("delete from " + CS).update();
        change(MERGE, ENTITY_A, "APPROVED");
        change(MERGE, ENTITY_B, "REJECTED");
        refused("every attribution countersign of the merge approved", transferSql(EMPLOYEE, MERGE, "PENDING"));
        jdbc.sql("delete from " + CS).update();
        change(MERGE, ENTITY_A, "APPROVED");
        change(MERGE, ENTITY_B, "LAPSED");
        refused("every attribution countersign of the merge approved", transferSql(EMPLOYEE, MERGE, "PENDING"));
        jdbc.sql("delete from " + CS).update();
        change(MERGE, ENTITY_A, "APPROVED");
        change(MERGE, ENTITY_B, "APPROVED");
        jdbc.sql(transferSql(EMPLOYEE, MERGE, "PENDING")).update();
        assertThat(jdbc.sql("select count(*) from " + PC + " where org_structure_command_id = " + MERGE).query(Integer.class).single()).isEqualTo(1);
    }
}
