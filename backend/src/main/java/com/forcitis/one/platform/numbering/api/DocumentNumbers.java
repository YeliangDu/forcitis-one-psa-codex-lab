package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;

public interface DocumentNumbers {

    /**
     * Next number for the document. MUST run inside the caller's transaction (the one that inserts the document):
     * a rollback also rolls back the counter, so numbers are gapless and unique per legal entity and period.
     */
    DocumentNumber next(DocumentType type, LegalEntityId legalEntity, LocalDate businessDate);
}
