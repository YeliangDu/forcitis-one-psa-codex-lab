package com.forcitis.one.accounting.period.domain;

import com.forcitis.one.accounting.period.api.AccountingPeriodError;
import com.forcitis.one.accounting.period.api.FiscalPeriodId;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Opening a fiscal year (S-R1): exactly the twelve monthly periods of one entity and year, all OPEN. */
public final class FiscalYear {
    public static final int FIRST_YEAR = 2000;
    public static final int LAST_YEAR = 2999;
    static final int ORDINARY_PERIODS = 12;

    private FiscalYear() {
    }

    /**
     * Refuses a year outside 2000 to 2999, a year more than one year after the latest year the entity has opened (a typo must not open 2062) and a year
     * before it: an OPEN period below a CLOSED one would capture the routing of late postings (spec 4.1: the spine creates no gaps; history enters as
     * historical periods, ADR-0045 S12). With nothing opened yet any year in range is accepted: the first year is the finance team's choice.
     * The latest year itself passes here and fails as already opened at insert.
     */
    public static List<FiscalPeriod> open(Supplier<FiscalPeriodId> ids, LegalEntityId legalEntityId, int fiscalYear, Optional<Integer> latestOpenedYear, Instant at) {
        if (fiscalYear < FIRST_YEAR || fiscalYear > LAST_YEAR) {
            throw new DomainException(AccountingPeriodError.ACCOUNTING_PERIOD_YEAR_OUT_OF_RANGE, ErrorKind.UNPROCESSABLE,
                    "fiscal year " + fiscalYear + " is outside " + FIRST_YEAR + ".." + LAST_YEAR);
        }
        if (latestOpenedYear.isPresent() && fiscalYear > latestOpenedYear.get() + 1) {
            throw new DomainException(AccountingPeriodError.ACCOUNTING_PERIOD_YEAR_TOO_FAR_AHEAD, ErrorKind.UNPROCESSABLE,
                    "fiscal year " + fiscalYear + " is more than one year after the latest opened year " + latestOpenedYear.get());
        }
        if (latestOpenedYear.isPresent() && fiscalYear < latestOpenedYear.get()) {
            throw new DomainException(AccountingPeriodError.ACCOUNTING_PERIOD_YEAR_BEFORE_LATEST, ErrorKind.UNPROCESSABLE,
                    "fiscal year " + fiscalYear + " is before the latest opened year " + latestOpenedYear.get());
        }
        List<FiscalPeriod> periods = new ArrayList<>(ORDINARY_PERIODS);
        for (int number = 1; number <= ORDINARY_PERIODS; number++) {
            periods.add(FiscalPeriod.open(ids.get(), legalEntityId, fiscalYear, number, at));
        }
        return List.copyOf(periods);
    }

    public static DomainException alreadyOpened(LegalEntityId legalEntityId, int fiscalYear) {
        return new DomainException(AccountingPeriodError.ACCOUNTING_PERIOD_YEAR_ALREADY_OPENED, ErrorKind.CONFLICT,
                "fiscal year " + fiscalYear + " of legal entity " + legalEntityId + " is already opened");
    }
}
