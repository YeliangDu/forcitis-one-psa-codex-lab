package com.forcitis.one.accounting.ledger.application;

import com.forcitis.one.accounting.ledger.domain.LedgerBasis;
import com.forcitis.one.accounting.ledger.domain.LedgerRepository;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gives a legal entity its ACCRUAL and CASH books in one transaction; the books it already has are left as they are (S-R2). */
@Service
public class EnsureLedgers {
    private final LedgerRepository ledgers;
    private final Clock clock;

    EnsureLedgers(LedgerRepository ledgers, Clock clock) {
        this.ledgers = ledgers;
        this.clock = clock;
    }

    /** The number of books created: 2 for a new entity, 0 for a redelivery. */
    @Transactional
    public int execute(LegalEntityId legalEntityId) {
        int created = 0;
        for (LedgerBasis basis : LedgerBasis.values()) {
            if (ledgers.insertIfAbsent(legalEntityId, basis, clock.instant())) {
                created++;
            }
        }
        return created;
    }
}
