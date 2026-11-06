package com.forcitis.one.accounting.period.api;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;

/**
 * The only way another module sees fiscal periods (INV-92). Both reads take a shared lock on the period row (SELECT FOR SHARE), so they must run in
 * the posting transaction: a close cannot slip in between the check and the write. Called outside a transaction they fail.
 */
public interface PeriodGate {

    /** Maps a business date to an ordinary period 1 to 12 (a date never reaches period 13, ADR-0046 decision 3.1). */
    PostingPeriod forPosting(LegalEntityId legalEntityId, LocalDate businessDate);

    /** The "not enabled" lookup of ADR-0087 5.2: period 13 is NOT_ENABLED; numbers outside 1 to 13 are a caller defect. */
    PeriodStatus periodStatus(LegalEntityId legalEntityId, int fiscalYear, int periodNumber);
}
