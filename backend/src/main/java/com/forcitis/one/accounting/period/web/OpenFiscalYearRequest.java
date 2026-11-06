package com.forcitis.one.accounting.period.web;

import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.LegalEntityId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

record OpenFiscalYearRequest(@Nullable UUID legalEntityId, @JsonDeserialize(using = StrictYear.class) @Nullable Integer fiscalYear) {

    LegalEntityId requiredLegalEntityId() {
        if (legalEntityId == null) {
            throw new InvalidRequestException("legalEntityId is required");
        }
        return new LegalEntityId(legalEntityId);
    }

    int requiredFiscalYear() {
        if (fiscalYear == null) {
            throw new InvalidRequestException("fiscalYear is required");
        }
        return fiscalYear;
    }
}
