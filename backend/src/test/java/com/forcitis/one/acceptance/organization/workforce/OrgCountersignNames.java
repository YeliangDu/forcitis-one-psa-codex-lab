package com.forcitis.one.acceptance.organization.workforce;

/**
 * #910 acceptance part (spec of #909, IMP-1030-ORG-F4a): the names the org countersign implementation (F4b) must match, in one place so
 * that a glossary rename changes only this file. Sources: the DD sign-offs of #893 (attribution_countersigns, finance head column, status
 * AWAITING_ATTRIBUTION_COUNTERSIGN) and #908 (attribution_countersign_bases, the org parent column, the merge-transfer guard), the design note
 * org-structure-commands.md section 5 and the DD amendments A to D on #910 (amendment E moved to #1040). The countersign api and permission repeat what the #894
 * acceptance part agreed (draft #902, CountersignNames); that branch is not on main, so the values are repeated here rather than shared.
 */
final class OrgCountersignNames {

    private OrgCountersignNames() {
    }

    // Org command side (#905 DD, note section 4.1).
    static final String ASSIGN_LEGAL_ENTITY = "ASSIGN_LEGAL_ENTITY";
    static final String AWAITING = "AWAITING_ATTRIBUTION_COUNTERSIGN";

    // The countersign use cases: one request row approved or rejected by the signed-in caller (#894 agreed names, note #878 section 6).
    static final String COUNTERSIGN_API_TYPE = "AttributionCountersigns";
    static final String APPROVE_METHOD = "approve";
    static final String REJECT_METHOD = "reject";
    static final String APPROVE_PARAMETER = "ApproveAttributionCountersign";
    static final String REJECT_PARAMETER = "RejectAttributionCountersign";
    static final String COUNTERSIGN_ID = "attributionCountersignId";

    // Tables and columns (#893, #908 DD sign-offs).
    static final String COUNTERSIGNS = "attribution_countersigns";
    static final String BASES = "attribution_countersign_bases";
    static final String ORG_PARENT_COLUMN = "org_structure_command_id";
    static final String LEGAL_ENTITY_VERSIONS = "legal_entity_versions";
    static final String FINANCE_HEAD_COLUMN = "finance_head_employee_id";
    static final String EMPLOYEE_LEGAL_ENTITIES = "employee_legal_entities";
    static final String AGGREGATE_COUNTERSIGN = "AttributionCountersign";

    // Request and decision kinds (#893 DD section 2).
    static final String KIND_CHANGE = "CHANGE";
    static final String KIND_WITHDRAWAL = "WITHDRAWAL";
    static final String APPROVED = "APPROVED";
    static final String REJECTED = "REJECTED";
    static final String LAPSED = "LAPSED";

    // Deciders (#895 scope addition and amendment 2 of #893; user answer Q3 of 2026-10-30: business_admin only from escalation level 2).
    static final String APPROVE_PERMISSION = "organization.workforce:legal-entity-attribution:approve";
    static final String FINANCE_HEAD_ROLE = "legal_entity_finance_head";
    static final String ESCALATED_ROLE = "business_admin";

    // Events (note section 5 rule 6; #894 agreed name).
    static final String EVENT_LEGAL_ENTITY_CHANGED = "EmployeeLegalEntityChanged";

    /*
     * DD amendment B on #910 (decider read path, lead ruling in the spirit of ADR-0080): the HTTP reads below are 暂名, chosen by the acceptance
     * part from the existing workforce paths (naming-standard 2.6 A-1: read-only computation as GET .../{id}:{action}), sent to one-design for a
     * ruling (lead 2026-11-02, review rv1041a M3). A rename changes only these constants.
     */
    /** One countersign request row with its parent command and the other rows of that command; the decider of the row's entity only. */
    static final String READ_COUNTERSIGN_PATH = "/api/v1/organization/attribution-countersigns/{attributionCountersignId}";
    /** The basis rows of an org command (#908 attribution_countersign_bases): finance heads of a requested entity and the org data admin. */
    static final String READ_BASES_PATH = "/api/v1/organization/org-structure-commands/{orgStructureCommandId}/attribution-countersign-bases";
    /** The merge rehearsal with its columns (amendment B: org data admin, whatever its data scope); query mergeIntoOrgUnitId and effectiveDate. */
    static final String PREVIEW_MERGE_PATH = "/api/v1/organization/org-units/{orgUnitId}:preview-merge";
    /** JSON property names a response of the read path may carry (amendment B "columns shown, and only these"); anything else is a leak. */
    static final java.util.Set<String> READ_PATH_FIELDS = java.util.Set.of(
            // command
            "command", "orgStructureCommandId", "personnelCommandId", "commandKind", "effectiveDate", "status", "withdrawalReason",
            // employee (L2 + PI, ADR-0080 7.1)
            "employee", "employeeId", "displayName", "employeeCode",
            // previous and new cost legal entity, previous and new (or current) org unit
            "previousLegalEntityId", "previousLegalEntityName", "newLegalEntityId", "newLegalEntityName",
            "previousOrgUnitId", "previousOrgUnitName", "newOrgUnitId", "newOrgUnitName", "orgUnitId", "orgUnitName", "needsCountersign",
            // the countersign rows of the same command
            "requests", "attributionCountersignId", "requestKind", "requestSequenceNumber", "legalEntityId", "legalEntityName", "requestedAt",
            "requestedByDisplayName", "dueOnDate", "escalationLevel", "decisionKind", "decidedAt", "decidedByDisplayName", "decisionReason",
            // list envelope (naming-standard 2.6 A-3)
            "items", "nextCursor");

    // The merge-transfer guard (#908 DD section 3).
    static final String GUARD_TRIGGER = "tg_personnel_commands_merge_transfer";
}
