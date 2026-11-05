package com.forcitis.one.accounting.period.application;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.List;
import java.util.UUID;

public record OpenedFiscalYear(LegalEntityId legalEntityId, int fiscalYear, List<OpenedPeriod> periods) {

    public record OpenedPeriod(UUID fiscalPeriodId, int periodNumber) {
    }
}
