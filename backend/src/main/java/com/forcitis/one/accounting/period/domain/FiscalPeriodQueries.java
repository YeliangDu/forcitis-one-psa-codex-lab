package com.forcitis.one.accounting.period.domain;

import com.forcitis.one.accounting.period.api.FiscalPeriodId;
import com.forcitis.one.kernel.LegalEntityId;
import java.util.Optional;

/** The reads of PeriodGate. Each takes a shared row lock (FOR SHARE) in the caller's transaction. */
public interface FiscalPeriodQueries {

    Optional<StoredPeriod> lockByKey(LegalEntityId legalEntityId, int fiscalYear, int periodNumber);

    /** The lowest-numbered OPEN period of the entity (earliest year first). */
    Optional<FiscalPeriodId> lockCurrentOpen(LegalEntityId legalEntityId);

    record StoredPeriod(FiscalPeriodId id, FiscalPeriodStatus status) {
    }
}
