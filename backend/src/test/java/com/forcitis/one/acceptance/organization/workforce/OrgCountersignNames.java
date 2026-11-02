package com.forcitis.one.acceptance.organization.workforce;

/**
 * #910 acceptance part (spec of #909, IMP-1030-ORG-F4a): the names the org countersign implementation (F4b) must match, in one place so
 * that a glossary rename changes only this file. Sources: the DD sign-offs of #893 (attribution_countersigns, finance head column, status
 * AWAITING_ATTRIBUTION_COUNTERSIGN) and #908 (attribution_countersign_bases, the org parent column, the merge-transfer guard), the design note
 * org-structure-commands.md section 5 and the DD amendments A to E on #910. The countersign api and permission repeat what the #894
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

    // The merge-transfer guard (#908 DD section 3).
    static final String GUARD_TRIGGER = "tg_personnel_commands_merge_transfer";
}
