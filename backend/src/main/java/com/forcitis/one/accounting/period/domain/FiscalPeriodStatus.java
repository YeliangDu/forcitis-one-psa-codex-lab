package com.forcitis.one.accounting.period.domain;

/** Equals ck_fiscal_periods_status (ADR-0045 decision 1). The spine writes OPEN only; the closing batch moves the others. */
public enum FiscalPeriodStatus {
    OPEN,
    CLOSING,
    CLOSED
}
