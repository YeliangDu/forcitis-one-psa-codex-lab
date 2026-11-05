package com.forcitis.one.accounting.period.application;

import com.forcitis.one.accounting.period.api.PeriodGate;
import com.forcitis.one.accounting.period.api.PeriodStatus;
import com.forcitis.one.accounting.period.api.PostingPeriod;
import com.forcitis.one.accounting.period.domain.FiscalPeriodQueries;
import com.forcitis.one.kernel.AccountingPeriod;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** MANDATORY: a shared lock that is released before the caller writes protects nothing (INV-92). */
@Component
class PeriodGateService implements PeriodGate {
    private static final int ADJUSTMENT_PERIOD = 13;
    private final FiscalPeriodQueries periods;

    PeriodGateService(FiscalPeriodQueries periods) {
        this.periods = periods;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PostingPeriod forPosting(LegalEntityId legalEntityId, LocalDate businessDate) {
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        AccountingPeriod original = AccountingPeriod.containing(Objects.requireNonNull(businessDate, "businessDate"));
        return periods.lockByKey(legalEntityId, original.year(), original.month()).map(stored -> switch (stored.status()) {
            case OPEN -> (PostingPeriod) new PostingPeriod.Open(stored.id());
            case CLOSING -> new PostingPeriod.Closing();
            case CLOSED -> periods.lockCurrentOpen(legalEntityId).<PostingPeriod>map(open -> new PostingPeriod.ClosedRouteTo(open, original))
                    .orElseGet(PostingPeriod.NoOpenPeriod::new);
        }).orElseGet(PostingPeriod.NotOpened::new);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PeriodStatus periodStatus(LegalEntityId legalEntityId, int fiscalYear, int periodNumber) {
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        if (periodNumber < 1 || periodNumber > ADJUSTMENT_PERIOD) {
            throw new IllegalArgumentException("period number must be 1 to 13: " + periodNumber);
        }
        if (periodNumber == ADJUSTMENT_PERIOD) {
            return PeriodStatus.NOT_ENABLED;
        }
        return periods.lockByKey(legalEntityId, fiscalYear, periodNumber).map(stored -> PeriodStatus.valueOf(stored.status().name())).orElse(PeriodStatus.NOT_OPENED);
    }
}
