package com.forcitis.one.accounting.period.api;

/** The answer of {@link PeriodGate#periodStatus}: the stored statuses, plus the two answers for a period that has no row. */
public enum PeriodStatus {
    OPEN,
    CLOSING,
    CLOSED,
    /** The year has no rows for the entity. */
    NOT_OPENED,
    /** Period 13 (adjustment period) is not enabled in the spine (ADR-0087 5.2). */
    NOT_ENABLED
}
