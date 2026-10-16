package com.forcitis.one.acceptance;

/**
 * The names R-1b P4 introduces, in one place so that a change of wording in the glossary is one edit here. They are the names the
 * glossary gate settled (card section 11); the card's wording is the source of every value.
 */
public final class P4Names {

    private P4Names() {
    }

    public static final String APPROVAL_SCHEMA = "platform_approval";
    public static final String INTEGRATION_SCHEMA = "platform_integration";

    /** Pre-sales hours node: id, shown name, form field, and the names it replaced. */
    public static final String ORG_UNIT_HEAD_NODE = "orgUnitHeadApproval";
    public static final String ORG_UNIT_HEAD_NODE_NAME = "部门负责人审批";
    public static final String ORG_UNIT_HEAD_FIELD = "orgUnitHeadId";
    public static final String REGIONAL_HEAD_NODE = "regionalHeadReview";
    public static final String REGIONAL_HEAD_FIELD = "regionalHeadId";
    public static final String PRE_SALES_FLAG = "preSalesHours";
    public static final String PROJECT_MANAGER_FIELD = "projectManagerId";
    public static final String PROJECT_HOURS_FLAG = "projectHours";

    /** Columns. */
    public static final String DOCUMENT_ID = "document_id";
    public static final String APPROVAL_REQUEST_ID = "approval_request_id";
    public static final String RECORD_NUMBER = "record_number";
    public static final String PROCESS_ARCHIVE_SKIP_ID = "process_archive_skip_id";
    public static final String SUBJECT_KIND = "subject_kind";
    public static final String SUBJECT_ID = "subject_id";
    public static final String PURPOSE_KIND = "purpose_kind";
    public static final String TRIGGER_EVENT_ID = "trigger_event_id";
    public static final String TARGET_DELIVERY_ID = "target_delivery_id";

    /** Constraints and indexes the card adds or keeps (names unchanged by C10). */
    public static final String UK_REQUEST_CURRENT_INSTANCE = "uk_approval_requests_current_instance_id";
    public static final String UQ_REQUEST_DOCUMENT = "uq_approval_requests_document";
    public static final String UK_ROUND_INSTANCE = "uk_start_rounds_instance_id";
    public static final String UQ_ROUND = "uq_start_rounds_round";
    public static final String UK_RECORD_NUMBER = "uk_approval_task_records_record_number";
    public static final String UK_SKIP_INSTANCE = "uk_process_archive_skips_process_instance_id";
    public static final String IX_TASKS_OPEN = "ix_approval_tasks_open";
    public static final String IX_TASKS_ESCALATE_AT = "ix_approval_tasks_escalate_at";
    public static final String UK_DELIVERY_CARD = "uk_outbound_deliveries_card";
    public static final String UK_DELIVERY_TARGET = "uk_outbound_deliveries_target_delivery_id";
    public static final String FK_DELIVERY_TARGET = "fk_outbound_deliveries_target_delivery_id";
    public static final String CK_SUBJECT_KIND = "ck_outbound_deliveries_subject_kind";
    public static final String CK_PURPOSE_KIND = "ck_outbound_deliveries_purpose_kind";
    public static final String HANDLED_NOTE_TARGET = "outbound_deliveries_handled_note_target";

    /** Values of the delivery subject and purpose. */
    public static final String SUBJECT_APPROVAL_TASK = "APPROVAL_TASK";
    public static final String SUBJECT_APPROVAL_INSTANCE = "APPROVAL_INSTANCE";
    public static final String PURPOSE_ASSIGNED = "TASK_ASSIGNED";
    public static final String PURPOSE_TRANSFERRED = "TASK_TRANSFERRED";
    public static final String PURPOSE_REASSIGNED = "TASK_REASSIGNED";
    public static final String PURPOSE_OVERDUE = "TASK_OVERDUE";
    public static final String PURPOSE_ESCALATED = "TASK_ESCALATED";
    public static final String PURPOSE_COMPLETED = "APPROVAL_COMPLETED";

    /** The structured delivery key in the integration api (record of kind, id, purpose, trigger event id), and the card that carries it. */
    public static final String DELIVERY_SUBJECT_TYPE = "com.forcitis.one.platform.integration.api.DeliverySubject";
    public static final String WECOM_CARD_TYPE = "com.forcitis.one.platform.integration.api.WecomCard";
}
