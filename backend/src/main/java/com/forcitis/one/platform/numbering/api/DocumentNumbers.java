package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentTypeCode;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;

public interface DocumentNumbers {
    /** MUST run in the caller's document-insert transaction: a rollback also rolls back the counter (gapless, unique). */
    IssuedNumber next(DocumentTypeCode type, LegalEntityId legalEntity, LocalDate businessDate);

    /**
     * For a number that does not depend on a legal entity (an employee code): MUST run in the caller's transaction as {@link #next}. The rule's template must have no {LE};
     * one that has is refused with PLATFORM_NUMBERING_RULE_INVALID. The counter is the global one, so a number is never issued twice, and a rolled-back transaction gives it back.
     * Until the template is confirmed it issues nothing (PLATFORM_NUMBERING_RULE_NOT_CONFIRMED), as {@link #next} does.
     */
    IssuedNumber nextGlobal(DocumentTypeCode type, LocalDate businessDate);
}
