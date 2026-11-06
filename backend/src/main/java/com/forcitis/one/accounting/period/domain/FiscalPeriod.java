package com.forcitis.one.accounting.period.domain;

import com.forcitis.one.accounting.period.api.FiscalPeriodId;
import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import java.util.Objects;

/**
 * One period of one legal entity's books (AGG-81): ordinary periods 1 to 12 are calendar months, 13 is the adjustment period (no row in the spine).
 * A period is born OPEN and only ever moves forward; the database refuses every other change (INV-99).
 */
public final class FiscalPeriod extends AggregateRoot {
    private final FiscalPeriodId id;
    private final LegalEntityId legalEntityId;
    private final int fiscalYear;
    private final int periodNumber;
    private final FiscalPeriodStatus status;
    private final Instant createdAt;

    private FiscalPeriod(FiscalPeriodId id, LegalEntityId legalEntityId, int fiscalYear, int periodNumber, FiscalPeriodStatus status, Instant createdAt) {
        super(0, 0);
        this.id = Objects.requireNonNull(id, "id");
        this.legalEntityId = Objects.requireNonNull(legalEntityId, "legalEntityId");
        this.fiscalYear = fiscalYear;
        this.periodNumber = periodNumber;
        this.status = status;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    static FiscalPeriod open(FiscalPeriodId id, LegalEntityId legalEntityId, int fiscalYear, int periodNumber, Instant at) {
        return new FiscalPeriod(id, legalEntityId, fiscalYear, periodNumber, FiscalPeriodStatus.OPEN, at);
    }

    public FiscalPeriodId id() {
        return id;
    }

    public LegalEntityId legalEntityId() {
        return legalEntityId;
    }

    public int fiscalYear() {
        return fiscalYear;
    }

    public int periodNumber() {
        return periodNumber;
    }

    public FiscalPeriodStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
