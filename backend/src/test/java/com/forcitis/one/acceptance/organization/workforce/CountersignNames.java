package com.forcitis.one.acceptance.organization.workforce;

import java.util.List;

/**
 * #894 (IMP-1030-FC-C): every name the legal-entity change and finance countersign implementation must match, in one place so that a
 * glossary rename changes only this file. Sources: ADR-0073 item 3 (lines 54 to 75), the data design sign-off of #893 (2026-10-30; table and
 * constraint names), the design note of #878 sections 4 to 7 and the user's answers of 2026-10-30 (Q1 to Q4). Names the sign-offs do not fix
 * are marked 暂名 and listed as doubts in the card report. The glossary proposal of #898 names AttributionCountersign, AwaitingCountersign,
 * CostLegalEntity, ChangeLegalEntity and EmployeeLegalEntityChanged.
 */
final class CountersignNames {

    private CountersignNames() {
    }

    // The countersign use cases have no HTTP surface either (like PersonnelCommands): a bean named AttributionCountersigns in the same api package (暂名).
    static final String API_TYPE = "AttributionCountersigns";
    static final String APPROVE_METHOD = "approve";
    static final String REJECT_METHOD = "reject";
    static final String APPROVE_PARAMETER = "ApproveAttributionCountersign";
    static final String REJECT_PARAMETER = "RejectAttributionCountersign";
    /** Components: the request row and the reason (optional on approve, required on reject). The decider is the signed-in caller, never a component. */
    static final String COUNTERSIGN_ID = "attributionCountersignId";

    // Command side (#893 sign-off section 1).
    static final String TARGET_LEGAL_ENTITY_ID = "targetLegalEntityId";
    static final String CHANGE_LEGAL_ENTITY = "CHANGE_LEGAL_ENTITY";
    static final String CORRECTION = "CORRECTION";
    static final String AWAITING = "AWAITING_ATTRIBUTION_COUNTERSIGN";
    static final String BASIS_PREVIOUS = "countersign_previous_legal_entity_id";
    static final String BASIS_NEW = "countersign_new_legal_entity_id";
    static final String TARGET_COLUMN = "target_legal_entity_id";

    // Countersign table (#893 sign-off section 2).
    static final String COUNTERSIGNS = "attribution_countersigns";
    static final String AGGREGATE_COUNTERSIGN = "AttributionCountersign";
    static final String OPEN = "OPEN";
    static final String APPROVED = "APPROVED";
    static final String REJECTED = "REJECTED";
    static final String LAPSED = "LAPSED";
    static final String KIND_CHANGE = "CHANGE";
    static final String KIND_WITHDRAWAL = "WITHDRAWAL";
    static final String NOT_DRAFTER_CHECK = "ck_attribution_countersigns_not_drafter";

    // Permission and roles (note section 6; user answers Q1 and Q3).
    static final String APPROVE_PERMISSION = "organization.workforce:legal-entity-attribution:approve";
    static final String FINANCE_HEAD_ROLE = "legal_entity_finance_head";
    /** Q3 (user 2026-10-30): after go-live the escalated person is the business administrator, who may countersign any request. */
    static final String ESCALATED_ROLE = "business_admin";
    /**
     * Ruling of one-design (W2/data owner, 2026-10-30): the finance head of a legal entity is a versioned column on the workforce table
     * legal_entity_versions (FK to the employee, valid by date like the other version columns, same pattern as org_unit_versions.head_employee_id).
     * A decider must hold the permission and be this column's employee in the version valid today; the fixture writes it, nothing else is asserted.
     */
    static final String LEGAL_ENTITY_VERSIONS = "legal_entity_versions";
    static final String FINANCE_HEAD_COLUMN = "finance_head_employee_id";
    /** Ruling (user Q3, ADR-0073 line 74): the escalated role may decide a row only from this escalation level on; levels 0 and 1 are refused. */
    static final int ESCALATED_FROM_LEVEL = 2;

    // Audit (note section 7) and event.
    static final String AUDIT_REQUESTED = "organization.workforce.attribution-countersign.requested";
    static final String AUDIT_APPROVED = "organization.workforce.attribution-countersign.approved";
    static final String AUDIT_REJECTED = "organization.workforce.attribution-countersign.rejected";
    static final String AUDIT_LAPSED = "organization.workforce.attribution-countersign.lapsed";
    static final String EVENT_LEGAL_ENTITY_CHANGED = "EmployeeLegalEntityChanged";
    static final List<String> EVENT_PAYLOAD = List.of("personnelCommandId", "employeeId", "effectiveDate", "previousLegalEntityId", "newLegalEntityId");

    /** A component with one of these words (case-insensitive) would let the caller choose what workforce computes (ADR-0073 item 3.4, line 58). */
    static final List<String> CALLER_MUST_NOT_SUPPLY_COST = List.of("costlegalentity", "previouslegalentity", "countersign", "basis");
}
