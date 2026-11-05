package com.forcitis.one.accounting.ledger.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.RegisteredLegalEntities;
import org.springframework.stereotype.Service;

/** Reconciles the books of the legal entities that exist: creates only what is missing, so it is safe to run at every start. */
@Service
public class BackfillLedgers {
    private final RegisteredLegalEntities registered;
    private final EnsureLedgers ensure;

    BackfillLedgers(RegisteredLegalEntities registered, EnsureLedgers ensure) {
        this.registered = registered;
        this.ensure = ensure;
    }

    /** The number of books created; each entity is its own transaction, so one failure leaves the others' books in place. */
    public int execute() {
        int created = 0;
        for (LegalEntityId id : registered.all()) {
            created += ensure.execute(id);
        }
        return created;
    }
}
