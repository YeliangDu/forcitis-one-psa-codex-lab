package com.forcitis.one.acceptance;

import java.util.List;
import java.util.Set;

/**
 * The names R-1b P5 introduces, in one place so that a change of wording in the glossary is one edit here. The values are the ones the
 * glossary gate settled (card sections 11 and 13).
 */
public final class P5Names {

    private P5Names() {
    }

    public static final String AUDIT_SCHEMA = "platform_audit";
    public static final String AUDIT_ENTRIES = "audit_entries";
    public static final String ARCHIVE_BATCHES = "audit_archive_batches";

    /** Columns. */
    public static final String SUBJECT_KEY_OLD = "subject_key";
    public static final String SUBJECT_KIND = "subject_kind";
    public static final String SUBJECT_ID = "subject_id";
    public static final String FIRST_ENTRY_CREATED_AT = "first_entry_created_at";
    public static final String LAST_ENTRY_CREATED_AT = "last_entry_created_at";

    /** Constraints and indexes. */
    public static final String IX_SUBJECT = "audit_entries_subject_idx";
    public static final String CK_SUBJECT_KIND = "ck_audit_entries_subject_kind";
    public static final String FK_SUBJECT_KIND = "fk_audit_entries_subject_kind";
    public static final String CK_SUBJECT_ID = "ck_audit_entries_subject_id";

    /**
     * The words both comments share with outbound_deliveries since P4. The card says "one sentence" in 3.1 and gives a shorter wording for
     * the kind in section 13, so the test asks for the shared part and for the empty-id wording only on subject_id.
     */
    public static final String SUBJECT_COLUMN_COMMENT = "被记录或被投递的业务对象";
    public static final String EMPTY_ID_COMMENT = "为空";

    /** Audit subject kinds (type AuditSubjectKind): the ones with an object, and the three without. */
    public static final String USER_ACCOUNT = "USER_ACCOUNT";
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String ROLE = "ROLE";
    public static final String APPROVAL_REQUEST = "APPROVAL_REQUEST";
    public static final String TIMESHEET = "TIMESHEET";
    public static final String LEGAL_ENTITY = "LEGAL_ENTITY";
    public static final String LEGAL_ENTITY_ATTRIBUTION = "LEGAL_ENTITY_ATTRIBUTION";
    public static final List<String> KINDS_WITH_OBJECT = List.of("USER_ACCOUNT", "EMPLOYEE", "ORG_UNIT", "EMERGENCY_ADMIN", "ROLE", "APPROVAL_TASK",
            "APPROVAL_REQUEST", "SYNC_RUN", "LEGAL_ENTITY", "LEGAL_ENTITY_ATTRIBUTION", "TIMESHEET", "WORK_CALENDAR_YEAR", "NUMBERING_RULE",
            "SOURCE_REFERENCE", "SOURCE_REFERENCE_REFUSAL", "AUTHORIZATION_REQUEST", "PERMISSION_ADJUSTMENT", "STORED_FILE");
    public static final List<String> KINDS_WITHOUT_OBJECT = List.of("REQUEST", "OPERATION", "LOGIN_ATTEMPT");

    /** The new and the retired names of the two renamed approval events (one-glossary-b2). */
    public static final String OVERDUE_EVENT = "platform.approval.ApprovalTaskBecameOverdue";
    public static final String HANDLED_EVENT = "platform.approval.TaskHandled";
    public static final String OLD_OVERDUE_EVENT = "ApprovalTaskOverdue";
    public static final String OLD_HANDLED_EVENT = "TaskResulted";

    /** The new and the retired names of the source-reference refusal and the timesheet cancellation events (B2-B05, B2-B07). */
    public static final String REFUSED_EVENT = "platform.integration.SourceReferenceRefused";
    public static final String WITHDRAWN_EVENT = "cost.timesheet.TimesheetApprovalWithdrawn";
    public static final String OLD_REFUSED_EVENT = "SourceReferenceRegistrationRefused";
    public static final String OLD_CANCELLED_EVENT = "TimesheetCancelled";

    /** The five tables whose primary key a framework owns (ADR-0033 section 1 item 8). */
    public static final Set<String> FRAMEWORK_TABLES = Set.of("platform_identity.sessions", "platform_identity.sessions_attributes",
            "platform_integration.event_publication", "platform_integration.event_publication_archive", "platform_scheduling.scheduler_locks");

    /** schema, table, and the columns of the key the table had before RULING-P5-D12 (now a named unique constraint). */
    public static final List<String[]> REKEYED_TABLES = List.<String[]>of(
            // role_permissions is retired by ADR-0034-IMPL PR-2a (card 7.2 "删除整张 role_permissions"); role_versions takes its place:
            // single uuid key role_version_id (card 7.1, pk_role_versions) and the named unique constraint on (role_id, version_number).
            new String[] {"platform_identity", "role_versions", "role_id,version_number"});
    // sync_run_held_separations, also rekeyed by RULING-P5-D12, was dropped with the old sync by #934 (ADR-0068 section 2.5 D2): the ruling
    // covers "the old tables that are still there".

    /** RULING-P5-1 (D-13): the ids the OpenAPI guard still lets through as "other", by full name. */
    public static final Set<String> OTHER_ID_NAMES = Set.of("InboxItemResponse.taskId", "SyncDiffResponse.subjectKey");

    public static final String PROBLEM_CODE_INVALID_REQUEST = "PLATFORM_WEB_REQUEST_INVALID";
}
