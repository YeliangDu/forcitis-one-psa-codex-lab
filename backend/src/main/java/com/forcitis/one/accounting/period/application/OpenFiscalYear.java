package com.forcitis.one.accounting.period.application;

import com.forcitis.one.accounting.period.api.AccountingPeriodError;
import com.forcitis.one.accounting.period.api.FiscalPeriodId;
import com.forcitis.one.accounting.period.domain.FiscalPeriod;
import com.forcitis.one.accounting.period.domain.FiscalPeriodRepository;
import com.forcitis.one.accounting.period.domain.FiscalYear;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.KnownLegalEntities;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opens the twelve monthly periods of one entity and year in one transaction (S-R1): a failure leaves no partial year. Raises no event (spec 4.1). */
@Service
public class OpenFiscalYear {
    private final FiscalPeriodRepository periods;
    private final KnownLegalEntities legalEntities;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;

    OpenFiscalYear(FiscalPeriodRepository periods, KnownLegalEntities legalEntities, AuditLog audit, CurrentUser currentUser, IdGenerator ids, Clock clock) {
        this.periods = periods;
        this.legalEntities = legalEntities;
        this.audit = audit;
        this.currentUser = currentUser;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public OpenedFiscalYear execute(LegalEntityId legalEntityId, int fiscalYear) {
        if (!legalEntities.isKnown(legalEntityId)) {
            throw new DomainException(AccountingPeriodError.ACCOUNTING_PERIOD_LEGAL_ENTITY_UNKNOWN, ErrorKind.INVALID, "unknown legal entity " + legalEntityId);
        }
        if (periods.hasYear(legalEntityId, fiscalYear)) {
            throw FiscalYear.alreadyOpened(legalEntityId, fiscalYear);
        }
        List<FiscalPeriod> opened = FiscalYear.open(() -> new FiscalPeriodId(ids.next()), legalEntityId, fiscalYear, periods.latestYear(legalEntityId), clock.instant());
        periods.insertAll(opened);
        audit.record(new AuditEntry("accounting.period.fiscal-year.opened", currentUser.userId(), null, AuditSubject.without(AuditSubjectKind.OPERATION),
                "legalEntityId=" + legalEntityId + " fiscalYear=" + fiscalYear + " periods=" + opened.size()));
        return new OpenedFiscalYear(legalEntityId, fiscalYear, opened.stream().map(p -> new OpenedFiscalYear.OpenedPeriod(p.id().value(), p.periodNumber())).toList());
    }
}
