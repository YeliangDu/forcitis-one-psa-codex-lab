package com.forcitis.one.accounting.period.web;

import com.forcitis.one.accounting.period.application.OpenedFiscalYear;
import java.util.List;
import java.util.UUID;

record OpenedFiscalYearResponse(UUID legalEntityId, int fiscalYear, List<OpenedPeriodResponse> periods) {

    record OpenedPeriodResponse(UUID fiscalPeriodId, int periodNumber) {
    }

    static OpenedFiscalYearResponse of(OpenedFiscalYear year) {
        return new OpenedFiscalYearResponse(year.legalEntityId().value(), year.fiscalYear(),
                year.periods().stream().map(p -> new OpenedPeriodResponse(p.fiscalPeriodId(), p.periodNumber())).toList());
    }
}
