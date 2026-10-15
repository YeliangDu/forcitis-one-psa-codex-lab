package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record LegacyNumberResponse(DocumentType documentType, String legacyNumber, UUID documentId, @Nullable UUID legalEntityId, String importBatch) {

    static LegacyNumberResponse from(LegacyNumberEntry e) {
        return new LegacyNumberResponse(e.document().documentType(), e.legacyNumber(), Uuids.parseCanonical(e.document().documentId().value()),
                e.legalEntity().map(le -> le.value()).orElse(null), e.importBatch());
    }
}
