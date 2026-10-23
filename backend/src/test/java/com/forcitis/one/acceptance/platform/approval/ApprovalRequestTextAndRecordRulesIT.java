package com.forcitis.one.acceptance.platform.approval;

import com.forcitis.one.acceptance.RowRuleProbe;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DATA-REPAIR-approval AP-14, AP-15, AP-16 (PR-7, one-data ruling 2026-10-16): the approval tables refuse, by named constraint, a
 * document type other than TIMESHEET, a summary that is blank or longer than 200 characters, and handling records whose
 * assignee / on-behalf-of columns do not fit the action. Rows are written with plain SQL against the migrated schema; each refused
 * statement breaks exactly one of the rules.
 */
class ApprovalRequestTextAndRecordRulesIT {

    private static final String REQUEST = "insert into platform_approval.approval_requests (id, process_key, document_id, initiator_id, current_round,"
            + " current_instance_id, status, document_type, legal_entity_id, summary_text) values (?, 'cost_timesheet_approval', ?, ?, 1, ?, 'RUNNING', ?, ?, ?)";

    private static final List<String> ACTIONS_WITHOUT_PEOPLE = List.of("CLAIMED", "RELEASED", "APPROVED", "REJECTED", "RETURNED", "ADVISED",
            "PASSED_AUTOMATICALLY");

    private RowRuleProbe db;
    private UUID request;
    private UUID task;
    private UUID instance;
    private int recordNumber;

    @BeforeEach
    void aRequestWithOneClaimedTask() throws Exception {
        db = new RowRuleProbe(getClass());
        request = UUID.randomUUID();
        instance = UUID.randomUUID();
        task = UUID.randomUUID();
        db.setup(REQUEST, request, UUID.randomUUID(), UUID.randomUUID(), instance, "TIMESHEET", UUID.randomUUID(), "synthetic summary");
        db.setup("insert into platform_approval.start_rounds (id, approval_request_id, round_number, initiator_id, instance_id, trigger_event_id)"
                + " values (?, ?, 1, ?, ?, ?)", UUID.randomUUID(), request, UUID.randomUUID(), instance, UUID.randomUUID());
        db.setup("insert into platform_approval.approval_tasks (task_id, instance_id, approval_request_id, node_key, status, assignee_id, claimed_at, node_name)"
                + " values (?, ?, ?, 'synthetic-node', 'CLAIMED', ?, now(), 'synthetic node')", task, instance, request, UUID.randomUUID());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private void insertRequest(String documentType, String summary, String refusedBy) throws SQLException {
        Object[] parameters = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), documentType, UUID.randomUUID(), summary};
        if (refusedBy == null) {
            db.accepted(REQUEST, parameters);
        } else {
            db.checkRefused(refusedBy, REQUEST, parameters);
        }
    }

    @Test
    @DisplayName("ADR-0066 decision 4, AP-14: a document type code of the wrong shape is refused by ck_approval_requests_document_type; QUOTE and JOURNAL_VOUCHER are refused by the approval write path (ApprovalDocumentTypeIT, PLATFORM_APPROVAL_NO_PROCESS)")
    void malformedDocumentTypeRefused() throws SQLException {
        insertRequest("timesheet", "synthetic summary", "ck_approval_requests_document_type");
        insertRequest("", "synthetic summary", "ck_approval_requests_document_type");
        insertRequest("TIME SHEET", "synthetic summary", "ck_approval_requests_document_type");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-14: an approval request for a TIMESHEET goes through")
    void timesheetRequestAccepted() throws SQLException {
        insertRequest("TIMESHEET", "synthetic summary", null);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-15: a summary of 201 characters is refused by ck_approval_requests_summary_text")
    void summaryTooLongRefused() throws SQLException {
        insertRequest("TIMESHEET", "a".repeat(201), "ck_approval_requests_summary_text");
        insertRequest("TIMESHEET", "字".repeat(201), "ck_approval_requests_summary_text");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-15: an empty or blank summary is refused by ck_approval_requests_summary_text")
    void blankSummaryRefused() throws SQLException {
        for (String blank : List.of("", " ", "   ", "\t", "\n", " \t\n ")) {
            insertRequest("TIMESHEET", blank, "ck_approval_requests_summary_text");
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-15: a summary of 1 and of 200 characters, Latin or Chinese, goes through")
    void summaryWithinLimitAccepted() throws SQLException {
        insertRequest("TIMESHEET", "a", null);
        insertRequest("TIMESHEET", "a".repeat(200), null);
        insertRequest("TIMESHEET", "字".repeat(200), null);
        insertRequest("TIMESHEET", " padded summary ", null);
    }

    private void record(String action, UUID onBehalfOf, UUID assignee, String refusedBy) throws SQLException {
        String reason = List.of("REJECTED", "RETURNED", "TRANSFERRED", "REASSIGNED").contains(action) ? "synthetic: reason" : null;
        String sql = "insert into platform_approval.approval_task_records (record_id, approval_request_id, record_number, round_number, task_id,"
                + " instance_id, node_key, node_name, action_kind, actor_id, on_behalf_of_id, assignee_id, record_reason, decided_at)"
                + " values (?, ?, ?, 1, ?, ?, 'synthetic-node', 'synthetic node', ?, ?, ?, ?, ?, now())";
        Object[] parameters = {UUID.randomUUID(), request, ++recordNumber, task, instance, action, UUID.randomUUID(), onBehalfOf, assignee, reason};
        if (refusedBy == null) {
            db.accepted(sql, parameters);
        } else {
            db.checkRefused(refusedBy, sql, parameters);
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-16: a transfer or reassignment without an assignee is refused by ck_approval_task_records_assignee")
    void transferWithoutAssigneeRefused() throws SQLException {
        record("TRANSFERRED", null, null, "ck_approval_task_records_assignee");
        record("REASSIGNED", null, null, "ck_approval_task_records_assignee");
        record("REASSIGNED", UUID.randomUUID(), null, "ck_approval_task_records_assignee");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-16: every other action carrying an assignee is refused by ck_approval_task_records_assignee")
    void otherActionWithAssigneeRefused() throws SQLException {
        for (String action : ACTIONS_WITHOUT_PEOPLE) {
            record(action, null, UUID.randomUUID(), "ck_approval_task_records_assignee");
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-16: a record naming a represented person on any action but reassignment is refused by ck_approval_task_records_on_behalf_of")
    void onBehalfOfOutsideReassignmentRefused() throws SQLException {
        for (String action : ACTIONS_WITHOUT_PEOPLE) {
            record(action, UUID.randomUUID(), null, "ck_approval_task_records_on_behalf_of");
        }
        record("TRANSFERRED", UUID.randomUUID(), UUID.randomUUID(), "ck_approval_task_records_on_behalf_of");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-16: each of the nine actions with the columns that fit it goes through, a reassignment with or without a former assignee")
    void everyLegalCombinationAccepted() throws SQLException {
        for (String action : ACTIONS_WITHOUT_PEOPLE) {
            record(action, null, null, null);
        }
        record("TRANSFERRED", null, UUID.randomUUID(), null);
        record("REASSIGNED", UUID.randomUUID(), UUID.randomUUID(), null);
        record("REASSIGNED", null, UUID.randomUUID(), null);
    }
}
