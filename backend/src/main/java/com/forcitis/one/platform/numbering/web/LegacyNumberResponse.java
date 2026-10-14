package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import org.jspecify.annotations.Nullable;

record LegacyNumberResponse(DocumentType documentType, String legacyNumber, String documentId, @Nullable String legalEntityId, String importBatch) {

    static LegacyNumberResponse from(LegacyNumberEntry e) {
        return new LegacyNumberResponse(e.document().documentType(), e.legacyNumber(), e.document().documentId().value(),
                e.legalEntity().map(le -> le.value().toString()).orElse(null), e.importBatch());
    }
}
