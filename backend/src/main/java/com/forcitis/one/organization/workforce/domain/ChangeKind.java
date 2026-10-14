package com.forcitis.one.organization.workforce.domain;

/** ISSUE rows are data exceptions reported to data stewards; the others are real changes of the snapshot. */
public enum ChangeKind {
    CREATED,
    UPDATED,
    SEPARATED,
    REHIRED,
    REVOKED,
    REVIVED,
    ISSUE
}
