package com.forcitis.one.organization.workforce.api;

/**
 * What an org-structure command does (ADR-0068 item 4, ADR-0073 item 2); the names are the values of org_structure_commands.command_kind. Org units take
 * every kind but DISABLE, positions only CREATE and DISABLE. ASSIGN_LEGAL_ENTITY comes with the countersign line.
 */
public enum OrgStructureCommandKind {
    CREATE,
    DISSOLVE,
    SET_ATTRIBUTES,
    MERGE,
    REPARENT,
    ASSIGN_HEAD,
    ASSIGN_RECEIVABLES_MANAGER,
    DISABLE
}
