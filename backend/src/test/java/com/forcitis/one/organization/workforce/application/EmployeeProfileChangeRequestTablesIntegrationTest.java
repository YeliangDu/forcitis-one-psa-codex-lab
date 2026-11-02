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

/** #980: the second-person review request of an employee profile change, as the database enforces it (design PR #996, section 4). */
class EmployeeProfileChangeRequestTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String EMPLOYEE = "'019a0000-0000-7000-8000-0000000009e1'";
    static final String OTHER_EMPLOYEE = "'019a0000-0000-7000-8000-0000000009e2'";
    static final String REQUESTER = "'019a0000-0000-7000-8000-0000000009a1'";
    static final String CONFIRMER = "'019a0000-0000-7000-8000-0000000009a2'";
    static final String TABLE = "organization_workforce.employee_profile_change_requests";

    @BeforeEach
    void employees() {
        jdbc.sql("delete from " + TABLE).update();
        for (String id : new String[] {EMPLOYEE, OTHER_EMPLOYEE}) {
            jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + id + ", 'E-" + id.substring(34, 37)
                    + "', 'name', 'EMPLOYED') on conflict do nothing").update();
        }
    }

    /** A pending rebind of the employee's WeCom id; the extra columns override the defaults. */
    private String insert(String employee, String status, String extraColumns, String extraValues) {
        return "insert into " + TABLE + " (employee_profile_change_request_id, employee_id, requested_by, requested_at, base_display_name, base_wecom_user_id, display_name,"
                + " wecom_user_id, request_status, created_at, updated_at" + extraColumns + ") values (gen_random_uuid(), " + employee + ", " + REQUESTER + ", now(), 'name', 'old', 'name',"
                + " 'new', '" + status + "', now(), now()" + extraValues + ")";
    }

    private void pending() {
        jdbc.sql(insert(EMPLOYEE, "PENDING", "", "")).update();
    }

    private void refused(String constraint, String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update()).hasMessageContaining(constraint);
    }

    @Test
    @DisplayName("#980 a pending request, and one decided by another person with its reason columns, are accepted; the requester may decide only by rejecting")
    void statusAndDecisionColumns() {
        pending();
        jdbc.sql(insert(EMPLOYEE, "CONFIRMED", ", decided_by, decided_at", ", " + CONFIRMER + ", now()")).update();
        jdbc.sql(insert(EMPLOYEE, "REJECTED", ", decided_by, decided_at, decision_reason", ", " + REQUESTER + ", now(), 'withdrawn'")).update();
        jdbc.sql(insert(EMPLOYEE, "VOIDED", ", decided_by, decided_at, void_reason_kind", ", " + CONFIRMER + ", now(), 'BASE_CHANGED'")).update();
        refused("ck_employee_profile_change_requests_status", insert(EMPLOYEE, "APPLIED", ", decided_by, decided_at", ", " + CONFIRMER + ", now()"));
        refused("ck_employee_profile_change_requests_decision", insert(EMPLOYEE, "CONFIRMED", "", ""));
        refused("ck_employee_profile_change_requests_decision", insert(EMPLOYEE, "PENDING", ", decided_by, decided_at", ", " + CONFIRMER + ", now()"));
        refused("ck_employee_profile_change_requests_two_people", insert(EMPLOYEE, "CONFIRMED", ", decided_by, decided_at", ", " + REQUESTER + ", now()"));
        refused("ck_employee_profile_change_requests_two_people", insert(EMPLOYEE, "VOIDED", ", decided_by, decided_at, void_reason_kind", ", " + REQUESTER + ", now(), 'BASE_CHANGED'"));
        assertThat(jdbc.sql("select count(*) from " + TABLE).query(Integer.class).single()).isEqualTo(4);
    }

    @Test
    @DisplayName("#980 the WeCom id must move; a void names its reason, a rejection its text")
    void contentColumns() {
        refused("ck_employee_profile_change_requests_wecom_moves", insert(EMPLOYEE, "PENDING", "", "").replace("'new'", "'old'"));
        refused("ck_employee_profile_change_requests_wecom_moves", insert(EMPLOYEE, "PENDING", "", "").replace("base_wecom_user_id,", "base_wecom_user_id, base_email_address,").replace("'old',", "'old', 'e',")
                .replace("'new'", "NULL").replace("'name', 'old', 'e'", "'name', NULL, 'e'"));
        jdbc.sql(insert(EMPLOYEE, "PENDING", "", "").replace("'new'", "NULL")).update();
        refused("ck_employee_profile_change_requests_void_reason", insert(EMPLOYEE, "VOIDED", ", decided_by, decided_at", ", " + CONFIRMER + ", now()"));
        refused("ck_employee_profile_change_requests_void_reason", insert(EMPLOYEE, "VOIDED", ", decided_by, decided_at, void_reason_kind", ", " + CONFIRMER + ", now(), 'OTHER'"));
        refused("ck_employee_profile_change_requests_void_reason", insert(EMPLOYEE, "CONFIRMED", ", decided_by, decided_at, void_reason_kind", ", " + CONFIRMER + ", now(), 'BASE_CHANGED'"));
        refused("ck_employee_profile_change_requests_decision_reason", insert(EMPLOYEE, "REJECTED", ", decided_by, decided_at", ", " + CONFIRMER + ", now()"));
        refused("ck_employee_profile_change_requests_decision_reason", insert(EMPLOYEE, "REJECTED", ", decided_by, decided_at, decision_reason", ", " + CONFIRMER + ", now(), ' '"));
        refused("ck_employee_profile_change_requests_decision_reason", insert(EMPLOYEE, "CONFIRMED", ", decided_by, decided_at, decision_reason", ", " + CONFIRMER + ", now(), 'x'"));
        refused("ck_employee_profile_change_requests_display_name", insert(EMPLOYEE, "PENDING", "", "").replace("base_display_name, base_wecom_user_id, display_name", "base_display_name, base_wecom_user_id, display_name").replace("'name', 'old', 'name'", "'name', 'old', ' '"));
        refused("ck_employee_profile_change_requests_display_name", insert(EMPLOYEE, "PENDING", "", "").replace("now(), 'name', 'old', 'name'", "now(), ' ', 'old', 'name'"));
        refused("ck_employee_profile_change_requests_gender_code", insert(EMPLOYEE, "PENDING", ", gender_code", ", 'OTHER'"));
        refused("ck_employee_profile_change_requests_base_gender_code", insert(EMPLOYEE, "PENDING", ", base_gender_code", ", 'OTHER'"));
        refused("ck_employee_profile_change_requests_email", insert(EMPLOYEE, "PENDING", ", email_address", ", ' '"));
        refused("ck_employee_profile_change_requests_email", insert(EMPLOYEE, "PENDING", ", email_address", ", '" + "a".repeat(101) + "'"));
        refused("ck_employee_profile_change_requests_email", insert(EMPLOYEE, "PENDING", ", base_email_address", ", ' '"));
        jdbc.sql(insert(OTHER_EMPLOYEE, "PENDING", ", gender_code, base_gender_code, email_address, base_email_address", ", 'FEMALE', 'MALE', 'a@b.c', NULL")).update();
    }

    @Test
    @DisplayName("#980 one pending request per employee; a decided one frees the employee")
    void onePendingPerEmployee() {
        pending();
        refused("uk_employee_profile_change_requests_one_pending_per_employee", insert(EMPLOYEE, "PENDING", "", ""));
        jdbc.sql(insert(OTHER_EMPLOYEE, "PENDING", "", "")).update();
        jdbc.sql(insert(EMPLOYEE, "REJECTED", ", decided_by, decided_at, decision_reason", ", " + CONFIRMER + ", now(), 'no'")).update();
        jdbc.sql("update " + TABLE + " set request_status = 'REJECTED', decided_by = " + CONFIRMER + ", decided_at = now(), decision_reason = 'no' where employee_id = " + EMPLOYEE
                + " and request_status = 'PENDING'").update();
        pending();
    }

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("#980 the runtime account cannot delete requests; only the table owner may")
    void deleteIsOwnerOnly() throws SQLException {
        pending();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'f1_runtime') then create role f1_runtime; end if; end $$");
            s.execute("grant usage on schema organization_workforce to f1_runtime");
            s.execute("grant select, insert, update, delete on " + TABLE + " to f1_runtime");
            s.execute("set role f1_runtime");
            try {
                assertThatThrownBy(() -> s.execute("delete from " + TABLE)).hasMessageContaining("append-only");
            } finally {
                s.execute("reset role");
            }
        }
        assertThat(jdbc.sql("delete from " + TABLE).update()).isEqualTo(1);
    }

    @Test
    @DisplayName("#980 a pending request moves once to CONFIRMED, REJECTED or VOIDED, the decision columns are set once, the content never changes, nothing is truncated")
    void appendOnly() {
        pending();
        refused("only the status and decision columns", "update " + TABLE + " set wecom_user_id = 'other'");
        jdbc.sql("update " + TABLE + " set request_status = 'CONFIRMED', decided_by = " + CONFIRMER + ", decided_at = now()").update();
        refused("status cannot move", "update " + TABLE + " set request_status = 'REJECTED', decision_reason = 'late'");
        refused("status cannot move", "update " + TABLE + " set request_status = 'PENDING', decided_by = NULL, decided_at = NULL");
        refused("is set once", "update " + TABLE + " set decided_by = " + REQUESTER);
        refused("is set once", "update " + TABLE + " set decided_at = decided_at + interval '1 day' where request_status = 'CONFIRMED'");
        jdbc.sql(insert(OTHER_EMPLOYEE, "REJECTED", ", decided_by, decided_at, decision_reason", ", " + CONFIRMER + ", now(), 'no'")).update();
        refused("is set once", "update " + TABLE + " set decision_reason = 'changed' where request_status = 'REJECTED'");
        refused("truncate", "truncate " + TABLE);
        assertThat(jdbc.sql("select count(*) from pg_trigger where tgname = 'tg_employee_profile_change_requests_append_only_truncate' and (tgtype & 32) <> 0").query(Integer.class).single())
                .isEqualTo(1);
    }
}
