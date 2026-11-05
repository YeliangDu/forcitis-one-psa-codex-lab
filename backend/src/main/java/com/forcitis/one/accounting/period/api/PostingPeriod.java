package com.forcitis.one.accounting.period.api;

import com.forcitis.one.kernel.AccountingPeriod;

/** Where a business date posts (INV-92): the answer of {@link PeriodGate#forPosting}. */
public sealed interface PostingPeriod {

    /** The date's period is open: post into it. */
    record Open(FiscalPeriodId periodId) implements PostingPeriod {
    }

    /** The date's period is being closed: the caller retries later and writes nothing. */
    record Closing() implements PostingPeriod {
    }

    /** The date's period is closed: post into the entity's current open period and record the original period (ADR-0045 decision 2). */
    record ClosedRouteTo(FiscalPeriodId currentOpenPeriodId, AccountingPeriod originalPeriod) implements PostingPeriod {
    }

    /** The date's period is closed and the entity has no open period to route to: a finance action opens the year. */
    record NoOpenPeriod() implements PostingPeriod {
    }

    /** The date's year has no rows: reject. */
    record NotOpened() implements PostingPeriod {
    }
}
