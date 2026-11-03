package com.forcitis.one.acceptance.organization.workforce;

import java.util.List;

/**
 * #906 (IMP-1030-ORG-F2a): every name the org-structure command implementation (F2b) must match, in one place so that a glossary rename
 * changes only this file. There is no REST endpoint and no due-date job in this phase (ADR-0068 item 2.5), so the entry point is a Spring
 * bean whose type, or one of whose interfaces, is {@link #API_TYPE} in {@link #API_PACKAGE}; see {@link OrgStructureCommandsProbe}.
 * Names that are not in an ADR or the glossary proposal (#911) are agreed here and listed as doubts in the card report.
 */
final class OrgStructureCommandNames {

    private OrgStructureCommandNames() {
    }

    // The entry point (the workforce api package, next to PersonnelCommands).
    static final String API_PACKAGE = PersonnelCommandNames.API_PACKAGE;
    static final String API_TYPE = "OrgStructureCommands";
    static final String ENTER_METHOD = "enter";
    static final String WITHDRAW_METHOD = "withdraw";
    /** ADR-0068 item 4: a rehearsal that returns the affected employees and writes nothing. */
    static final String PREVIEW_MERGE_METHOD = "previewMerge";
    /**
     * Runs a PENDING merge whose effective date has come: the due-date step of the implementation, outside F2b's entry, so the test
     * drives it by name. One parameter: an OrgStructureCommandId, a UUID, or a record holding orgStructureCommandId. Repeatable: running
     * it again continues where an interrupted run stopped.
     */
    static final String EXECUTE_MERGE_METHOD = "executeMerge";
    /** Simple names of the single record parameter of each method. */
    static final String ENTER_PARAMETER = "EnterOrgStructureCommand";
    static final String WITHDRAW_PARAMETER = "WithdrawOrgStructureCommand";
    static final String PREVIEW_PARAMETER = "PreviewOrgUnitMerge";

    // Record components (the columns of org_structure_commands, camel-cased; ADR-0068 attachment).
    static final String TARGET_KIND = "targetKind";
    static final String COMMAND_KIND = "commandKind";
    static final String TARGET_ORG_UNIT_ID = "targetOrgUnitId";
    static final String TARGET_POSITION_ID = "targetPositionId";
    static final String EFFECTIVE_DATE = "effectiveDate";
    static final String TARGET_CODE = "targetCode";
    static final String TARGET_NAME = "targetName";
    static final String TARGET_SHORT_NAME = "targetShortName";
    static final String TARGET_REGION_KIND = "targetRegionKind";
    static final String TARGET_IS_DELIVERY_UNIT = "targetIsDeliveryUnit";
    static final String TARGET_PARENT_ORG_UNIT_ID = "targetParentOrgUnitId";
    static final String TARGET_HEAD_EMPLOYEE_ID = "targetHeadEmployeeId";
    static final String TARGET_RECEIVABLES_MANAGER_EMPLOYEE_ID = "targetReceivablesManagerEmployeeId";
    static final String MERGE_INTO_ORG_UNIT_ID = "mergeIntoOrgUnitId";
    static final String TARGET_LEGAL_ENTITY_ID = "targetLegalEntityId";
    static final String ORG_STRUCTURE_COMMAND_ID = "orgStructureCommandId";
    static final String REASON = "reason";

    // Kinds, statuses (#905 DD; ADR-0068 item 4, ADR-0073 item 2).
    static final String ORG_UNIT = "ORG_UNIT";
    static final String POSITION = "POSITION";
    static final String CREATE = "CREATE";
    static final String DISSOLVE = "DISSOLVE";
    static final String SET_ATTRIBUTES = "SET_ATTRIBUTES";
    static final String MERGE = "MERGE";
    static final String REPARENT = "REPARENT";
    static final String ASSIGN_HEAD = "ASSIGN_HEAD";
    static final String ASSIGN_RECEIVABLES_MANAGER = "ASSIGN_RECEIVABLES_MANAGER";
    static final String DISABLE = "DISABLE";
    static final String IN_PROGRESS = "IN_PROGRESS";
    static final String ACTIVE = "ACTIVE";
    static final String DISABLED = "DISABLED";
    static final String REVOKED = "REVOKED";
    static final String HEADQUARTERS = "HEADQUARTERS";
    static final String REGIONAL = "REGIONAL";

    // Permission and roles (ADR-0073 item 2.4 line 45, ADR-0027 item 2.1; code format bc:resource:action, name agreed by the card).
    static final String PERMISSION = "organization.workforce:org-structure-command:create";
    static final String ORG_DATA_ADMIN_ROLE = "org_data_admin";

    // Audit (ADR-0073 item 2.3: field by field before and after).
    static final String AUDIT_ENTERED = "organization.workforce.org-structure-command.entered";
    static final String AUDIT_WITHDRAWN = "organization.workforce.org-structure-command.withdrawn";

    // Tables of the workforce schema asserted on (#905, #904 DD sign-offs).
    static final String ORG_COMMANDS = "org_structure_commands";
    static final String ORG_UNITS = "org_units";
    static final String ORG_UNIT_VERSIONS = "org_unit_versions";
    static final String ORG_UNIT_LEGAL_ENTITIES = "org_unit_legal_entities";
    static final String POSITIONS = "positions";
    static final String POSITION_VERSIONS = "position_versions";
    static final String AGGREGATE_ORG_COMMAND = "OrgStructureCommand";

    // Events published when a command takes effect (#913 glossary proposal). Their payload mentions orgStructureCommandId.
    static final String EVENT_ORG_UNIT_CREATED = "OrgUnitCreated";
    static final String EVENT_ORG_UNIT_DISSOLVED = "OrgUnitDissolved";
    static final String EVENT_ATTRIBUTES_SET = "OrgUnitAttributesSet";
    static final String EVENT_MERGED = "OrgUnitMerged";
    static final String EVENT_REPARENTED = "OrgUnitReparented";
    static final String EVENT_HEAD_ASSIGNED = "OrgUnitHeadAssigned";
    static final String EVENT_RECEIVABLES_MANAGER_ASSIGNED = "OrgUnitReceivablesManagerAssigned";
    static final String EVENT_POSITION_CREATED = "PositionCreated";
    static final String EVENT_POSITION_DISABLED = "PositionDisabled";
    static final List<String> ALL_ORG_EVENTS = List.of(EVENT_ORG_UNIT_CREATED, EVENT_ORG_UNIT_DISSOLVED, EVENT_ATTRIBUTES_SET, EVENT_MERGED,
            EVENT_REPARENTED, EVENT_HEAD_ASSIGNED, EVENT_RECEIVABLES_MANAGER_ASSIGNED, EVENT_POSITION_CREATED, EVENT_POSITION_DISABLED,
            "OrgUnitLegalEntityAssigned");
}
