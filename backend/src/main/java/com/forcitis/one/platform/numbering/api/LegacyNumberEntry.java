package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** The numeric part of the document id is what the legacy link table stores; a non-numeric id cannot be linked. */
@ValueObject
public record LegacyNumberEntry(DocumentReference document, String legacyNumber, LegalEntityId legalEntity, String importBatch) {
    public static final int MAX_LENGTH = 128;

    public LegacyNumberEntry {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(legacyNumber, "legacyNumber");
        Objects.requireNonNull(legalEntity, "legalEntity");
        Objects.requireNonNull(importBatch, "importBatch");
        if (!document.documentId().value().matches("[1-9][0-9]{0,17}")) {
            throw new IllegalArgumentException("A legacy link needs a positive numeric document id: " + document.documentId().value());
        }
        if (legacyNumber.isBlank() || !legacyNumber.equals(legacyNumber.strip()) || legacyNumber.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Legacy number must be 1.." + MAX_LENGTH + " characters without surrounding blanks");
        }
        if (importBatch.isBlank() || importBatch.length() > 64) {
            throw new IllegalArgumentException("Import batch must be 1..64 characters");
        }
    }
}
