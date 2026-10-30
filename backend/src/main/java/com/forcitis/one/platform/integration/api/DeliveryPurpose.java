package com.forcitis.one.platform.integration.api;

/** Why a card is sent. A purpose that can recur for one object (a task transferred twice) is told apart by the event that caused it. */
public enum DeliveryPurpose {
    TASK_ASSIGNED(DeliverySubjectKind.APPROVAL_TASK, false),
    TASK_TRANSFERRED(DeliverySubjectKind.APPROVAL_TASK, true),
    TASK_REASSIGNED(DeliverySubjectKind.APPROVAL_TASK, true),
    TASK_OVERDUE(DeliverySubjectKind.APPROVAL_TASK, false),
    TASK_ESCALATED(DeliverySubjectKind.APPROVAL_TASK, false),
    APPROVAL_COMPLETED(DeliverySubjectKind.APPROVAL_INSTANCE, false),
    SEPARATION_HOLD_NOTICE(DeliverySubjectKind.SEPARATION_HOLD, false),
    UNPAID_LEAVE_START_NOTICE(DeliverySubjectKind.PERSONNEL_COMMAND, false);

    private final DeliverySubjectKind subjectKind;
    private final boolean perEvent;

    DeliveryPurpose(DeliverySubjectKind subjectKind, boolean perEvent) {
        this.subjectKind = subjectKind;
        this.perEvent = perEvent;
    }

    public DeliverySubjectKind subjectKind() {
        return subjectKind;
    }

    public boolean perEvent() {
        return perEvent;
    }
}
