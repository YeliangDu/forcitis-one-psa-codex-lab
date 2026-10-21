package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentTypeCode;

/**
 * Throws the DomainException of DocumentTypes (PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED) when the code is not registered, whatever the date.
 * For writers that continue a document an earlier, date-checked step accepted (an approval started after submission): a type retired
 * in between must not strand it.
 */
public interface RegisteredDocumentTypes {
    void requireRegistered(DocumentTypeCode code);
}
