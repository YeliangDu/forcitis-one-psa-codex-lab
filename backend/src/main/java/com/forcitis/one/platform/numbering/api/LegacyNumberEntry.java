package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.Optional;

/**
 * The document id is the UUID of the document in this system, which is what the legacy link table stores; any other id cannot be linked. legalEntity is empty
 * when the import could not work it out: the original number stays searchable.
 */
@ValueObject
public record LegacyNumberEntry(DocumentReference document, String legacyNumber, Optional<LegalEntityId> legalEntity, String importBatch) {
    public static final int MAX_LENGTH = 128;

    public LegacyNumberEntry {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(legacyNumber, "legacyNumber");
        Objects.requireNonNull(legalEntity, "legalEntity");
        Objects.requireNonNull(importBatch, "importBatch");
        try {
            Uuids.parseCanonical(document.documentId().value());
        } catch (IllegalArgumentException notAUuid) {
            throw new IllegalArgumentException("A legacy link needs the UUID of the document: " + document.documentId().value(), notAUuid);
        }
        if (legacyNumber.isBlank() || !legacyNumber.equals(legacyNumber.strip()) || legacyNumber.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Legacy number must be 1.." + MAX_LENGTH + " characters without surrounding blanks");
        }
        if (importBatch.isBlank() || importBatch.length() > 64) {
            throw new IllegalArgumentException("Import batch must be 1..64 characters");
        }
    }
}
