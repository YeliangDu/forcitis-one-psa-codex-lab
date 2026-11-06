package com.forcitis.one.accounting.ledger.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;

public interface LedgerRepository {
    /** Creates the book unless the entity already has one on this basis; true when a row was written. Safe to repeat and to race. */
    boolean insertIfAbsent(LegalEntityId legalEntityId, LedgerBasis basis, Instant createdAt);
}
