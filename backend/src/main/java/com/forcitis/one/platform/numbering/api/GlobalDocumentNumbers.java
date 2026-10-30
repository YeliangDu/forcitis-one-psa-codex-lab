package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentTypeCode;
import java.time.LocalDate;

/** For a number that does not depend on a legal entity (an employee code); {@link DocumentNumbers#next} is the entry for the numbers that do. */
public interface GlobalDocumentNumbers {
    /**
     * MUST run in the caller's transaction, as {@link DocumentNumbers#next}: a rollback also rolls back the counter (gapless, unique). The rule's template must have no {LE};
     * one that has is refused with PLATFORM_NUMBERING_RULE_INVALID. The counter is the global one. Until the template is confirmed it issues nothing (PLATFORM_NUMBERING_RULE_NOT_CONFIRMED).
     */
    IssuedNumber nextGlobal(DocumentTypeCode type, LocalDate businessDate);
}
