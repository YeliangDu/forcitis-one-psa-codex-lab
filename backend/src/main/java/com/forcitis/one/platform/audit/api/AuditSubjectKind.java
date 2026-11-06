package com.forcitis.one.platform.audit.api;

import java.util.Locale;

public enum AuditSubjectKind {
    USER_ACCOUNT(true),
    EMPLOYEE(true),
    ORG_UNIT(true),
    EMERGENCY_ADMIN(true),
    ROLE(true),
    APPROVAL_TASK(true),
    APPROVAL_REQUEST(true),
    SYNC_RUN(true),
    LEGAL_ENTITY(true),
    LEGAL_ENTITY_ATTRIBUTION(true),
    ORG_STRUCTURE_COMMAND(true),
    TIMESHEET(true),
    WORK_CALENDAR_YEAR(true),
    STANDARD_HOURS(true),
    PAYROLL_RULE_VERSION(true),
    NUMBERING_RULE(true),
    SOURCE_REFERENCE(true),
    SOURCE_REFERENCE_REFUSAL(true),
    AUTHORIZATION_REQUEST(true),
    PERMISSION_ADJUSTMENT(true),
    STORED_FILE(true),
    REQUEST(false),
    OPERATION(false),
    LOGIN_ATTEMPT(false);

    private final boolean hasObject;

    AuditSubjectKind(boolean hasObject) {
        this.hasObject = hasObject;
    }

    public boolean hasObject() {
        return hasObject;
    }

    /** The kind of an event's aggregate type (UserAccount gives USER_ACCOUNT); an aggregate type with no kind is refused, never guessed. */
    public static AuditSubjectKind ofAggregateType(String aggregateType) {
        String name = aggregateType.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        for (AuditSubjectKind kind : values()) {
            if (kind.hasObject && kind.name().equals(name)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("aggregate type " + aggregateType + " has no audit subject kind");
    }
}
