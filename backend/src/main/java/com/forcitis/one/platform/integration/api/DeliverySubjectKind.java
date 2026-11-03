package com.forcitis.one.platform.integration.api;

/** The kind of business object a delivery is about; the values are neutral and name no external system. */
public enum DeliverySubjectKind {
    APPROVAL_TASK,
    APPROVAL_INSTANCE,
    SEPARATION_HOLD,
    PERSONNEL_COMMAND,
    ACCOUNT_ROLE_GRANT,
    USER_ACCOUNT,
    PERMISSION_ADJUSTMENT,
    ATTRIBUTION_COUNTERSIGN
}
