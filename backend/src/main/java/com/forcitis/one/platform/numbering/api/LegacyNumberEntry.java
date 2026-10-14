package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/**
 * Link between a document of the new system and the number it carried before the unified format (the read-only "original
 * number"). Unique per (document type, legacy number).
 */
@ValueObject
public record LegacyNumberEntry(DocumentType documentType, String legacyNumber, DocumentId documentId, LegalEntityId legalEntity,
        String importBatch) {

    public static final int MAX_LENGTH = 128;

    public LegacyNumberEntry {
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(legacyNumber, "legacyNumber");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(legalEntity, "legalEntity");
        Objects.requireNonNull(importBatch, "importBatch");
        if (legacyNumber.isBlank() || !legacyNumber.equals(legacyNumber.strip()) || legacyNumber.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Legacy number must be 1.." + MAX_LENGTH + " characters without surrounding blanks");
        }
        if (importBatch.isBlank() || importBatch.length() > 64) {
            throw new IllegalArgumentException("Import batch must be 1..64 characters");
        }
    }
}
