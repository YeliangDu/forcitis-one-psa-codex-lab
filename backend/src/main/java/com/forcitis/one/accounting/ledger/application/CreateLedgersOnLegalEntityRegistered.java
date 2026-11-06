package com.forcitis.one.accounting.ledger.application;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** A new legal entity gets its two books. A redelivery finds them and writes nothing: the unique key on entity and basis is the dedupe. */
@SystemActorName("accounting.ledger.book-creation")
@Component
class CreateLedgersOnLegalEntityRegistered {
    private final EnsureLedgers ensure;

    CreateLedgersOnLegalEntityRegistered(EnsureLedgers ensure) {
        this.ensure = ensure;
    }

    @ApplicationModuleListener
    void on(LegalEntityRegistered event) {
        ensure.execute(event.legalEntityId());
    }
}
