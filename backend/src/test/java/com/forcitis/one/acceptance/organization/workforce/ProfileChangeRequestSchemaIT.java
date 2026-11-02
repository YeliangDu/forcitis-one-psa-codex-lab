package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #980 acceptance, design note section 6 line 12 and the DD sign-off on #980 (comment 6587943265): the named CHECKs and the
 * one-PENDING-per-employee index of employee_profile_change_requests refuse the bad rows; each refusal has a passing twin.
 */
class ProfileChangeRequestSchemaIT extends ProfileChangeRequestSupport {

    static final TestDatabase postgres = TestPostgres.database(ProfileChangeRequestSchemaIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String CHECK = "23514";
    private static final String UNIQUE = "23505";

    private OffsetDateTime at;
    private UUID employee;
    private UUID requester;
    private UUID confirmer;

    @FunctionalInterface
    private interface Statement {
        void run() throws Exception;
    }

    @BeforeEach
    void aSubject() throws Exception {
        at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        employee = staff().employeeId();
        requester = hr.userId();
        confirmer = UUID.randomUUID();
    }

    /** One request row: base WeCom id "base", proposed as given; status and decision columns as given. */
    private void row(String proposedWecom, String status, UUID decidedBy, String voidReason, String decisionReason) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "insert into " + REQUESTS + " (employee_profile_change_request_id, employee_id, requested_by, requested_at, base_display_name,"
                    + " base_wecom_user_id, display_name, wecom_user_id, request_status, decided_by, decided_at, void_reason_kind, decision_reason, created_at, updated_at)"
                    + " values (?, ?, ?, ?, 'Synthetic base', 'synthetic-base', 'Synthetic proposed', ?, ?, ?, ?, ?, ?, ?, ?)",
                    Uuids.timeOrdered(NOW), stored(employee), requester, at, proposedWecom, status, decidedBy, decidedBy == null ? null : at, voidReason, decisionReason, at, at);
        }
    }

    private static void refusedBy(Statement statement, String sqlState, String constraint) {
        SQLException refused = catchThrowableOfType(SQLException.class, statement::run);
        assertThat((Throwable) refused).as("refused by " + constraint).isNotNull();
        assertThat(refused.getSQLState()).as(refused.getMessage()).isEqualTo(sqlState);
        assertThat(refused.getMessage()).contains("\"" + constraint + "\"");
    }

    @Test
    @DisplayName("#980 完成标准 12：确认人与请求人同一人被 ck_..._two_people 拒绝（REJECTED 即撤回例外，允许）；状态与决定人、决定时间不配对被 ck_..._decision 拒绝")
    void twoPeopleAndDecision() throws Exception {
        refusedBy(() -> row("synthetic-new", "CONFIRMED", requester, null, null), CHECK, "ck_employee_profile_change_requests_two_people");
        row("synthetic-new-1", "REJECTED", requester, null, "synthetic withdrawal");
        refusedBy(() -> row("synthetic-new-2", "PENDING", confirmer, null, null), CHECK, "ck_employee_profile_change_requests_decision");
        refusedBy(() -> row("synthetic-new-3", "CONFIRMED", null, null, null), CHECK, "ck_employee_profile_change_requests_decision");
        row("synthetic-new-4", "CONFIRMED", confirmer, null, null);
    }

    @Test
    @DisplayName("#980 完成标准 12：提议与基准企业微信账号相同被 ck_..._wecom_moves 拒绝；作废原因只在 VOIDED 时有（缺或多都拒）；REJECTED 必须有理由")
    void wecomMovesVoidReasonAndDecisionReason() throws Exception {
        refusedBy(() -> row("synthetic-base", "PENDING", null, null, null), CHECK, "ck_employee_profile_change_requests_wecom_moves");
        refusedBy(() -> row("synthetic-v1", "VOIDED", confirmer, null, null), CHECK, "ck_employee_profile_change_requests_void_reason");
        refusedBy(() -> row("synthetic-v2", "CONFIRMED", confirmer, "BASE_CHANGED", null), CHECK, "ck_employee_profile_change_requests_void_reason");
        row("synthetic-v3", "VOIDED", confirmer, "WECOM_USER_ID_TAKEN", null);
        refusedBy(() -> row("synthetic-r1", "REJECTED", confirmer, null, null), CHECK, "ck_employee_profile_change_requests_decision_reason");
        refusedBy(() -> row("synthetic-s1", "WITHDRAWN", confirmer, null, null), CHECK, "ck_employee_profile_change_requests_status");
    }

    @Test
    @DisplayName("#980 完成标准 12：同一员工第二条 PENDING 被 uk_employee_profile_change_requests_one_pending_per_employee 拒绝；前一条决定后可以再有一条 PENDING")
    void onePendingPerEmployee() throws Exception {
        row("synthetic-p1", "PENDING", null, null, null);
        refusedBy(() -> row("synthetic-p2", "PENDING", null, null, null), UNIQUE, "uk_employee_profile_change_requests_one_pending_per_employee");
        employee = staff().employeeId();
        row("synthetic-p3", "CONFIRMED", confirmer, null, null);
        row("synthetic-p4", "PENDING", null, null, null);
    }
}
