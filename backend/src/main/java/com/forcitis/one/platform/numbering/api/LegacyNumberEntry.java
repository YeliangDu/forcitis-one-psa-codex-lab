package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.MigrationBatchId;
import java.util.Objects;
import java.util.Optional;

/**
 * The document id is the UUID of the document in this system, which is what the legacy link table stores; any other id cannot be linked. sourceSystem is the
 * system that numbered the document before; legalEntity is empty when the import could not work it out: the original number stays searchable.
 * migrationBatchId names the migration batch that wrote the link.
 */
@ValueObject
public record LegacyNumberEntry(DocumentReference document, ExternalSource sourceSystem, String legacyNumber, Optional<LegalEntityId> legalEntity,
                                MigrationBatchId migrationBatchId) {
    public static final int MAX_LENGTH = 128;

    public LegacyNumberEntry {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(sourceSystem, "sourceSystem");
        Objects.requireNonNull(legacyNumber, "legacyNumber");
        Objects.requireNonNull(legalEntity, "legalEntity");
        Objects.requireNonNull(migrationBatchId, "migrationBatchId");
        if (legacyNumber.isBlank() || !legacyNumber.equals(legacyNumber.strip()) || legacyNumber.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Legacy number must be 1.." + MAX_LENGTH + " characters without surrounding blanks");
        }
    }
}
