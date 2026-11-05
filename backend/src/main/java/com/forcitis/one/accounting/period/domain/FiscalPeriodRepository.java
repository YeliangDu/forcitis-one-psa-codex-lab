package com.forcitis.one.accounting.period.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.List;
import java.util.Optional;

public interface FiscalPeriodRepository {

    /** The latest fiscal year the entity has any period of. */
    Optional<Integer> latestYear(LegalEntityId legalEntityId);

    boolean hasYear(LegalEntityId legalEntityId, int fiscalYear);

    /** Inserts the twelve periods in one statement batch; a year that is opened concurrently meets the unique key and is refused as already opened. */
    void insertAll(List<FiscalPeriod> periods);
}
