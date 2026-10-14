package com.forcitis.one.organization.workforce.domain;

/** Data exceptions found while syncing (reported to data stewards, never silently dropped). */
public enum IssueKind {
    NULL_FLAG,
    DUPLICATE_UNIT,
    MULTIPLE_HEADS,
    MULTIPLE_REGIONAL_MANAGERS,
    HEAD_NOT_FOUND,
    REGIONAL_MANAGER_NOT_FOUND,
    UNKNOWN_CATEGORY,
    ISOLATED_MISSING_ORG_UNIT,
    ISOLATED_MISSING_POSITION,
    DUPLICATE_MEMBER,
    DUPLICATE_WECOM_USER_ID,
    SUPERVISOR_IS_SELF,
    SUPERVISOR_NOT_FOUND,
    SUPERVISOR_SEPARATED,
    SUPERVISOR_CYCLE
}
