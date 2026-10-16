package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentType;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;

public interface DocumentNumbers {
    /** MUST run in the caller's document-insert transaction: a rollback also rolls back the counter (gapless, unique). */
    IssuedNumber next(DocumentType type, LegalEntityId legalEntity, LocalDate businessDate);
}
