package com.forcitis.one.accounting.period.api;

import com.forcitis.one.kernel.ErrorCode;

public enum AccountingPeriodError implements ErrorCode {
    ACCOUNTING_PERIOD_LEGAL_ENTITY_UNKNOWN,
    ACCOUNTING_PERIOD_YEAR_OUT_OF_RANGE,
    ACCOUNTING_PERIOD_YEAR_TOO_FAR_AHEAD,
    ACCOUNTING_PERIOD_YEAR_BEFORE_LATEST,
    ACCOUNTING_PERIOD_YEAR_ALREADY_OPENED
}
