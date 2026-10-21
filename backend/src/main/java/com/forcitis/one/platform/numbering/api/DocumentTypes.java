package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentTypeCode;
import java.time.LocalDate;

/** Writers of other modules check a type code here before they store it; there is no foreign key across schemas (ADR-0066 decision 4). */
public interface DocumentTypes {
    /**
     * Throws a DomainException (PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED) when the code is not registered or not in force on the date.
     * The migration channel does not use this check: documents it brings in may be older than the type's effective date, so
     * RegisterLegacyNumbers only requires the code to be registered.
     */
    void requireInForce(DocumentTypeCode code, LocalDate onDate);
}
