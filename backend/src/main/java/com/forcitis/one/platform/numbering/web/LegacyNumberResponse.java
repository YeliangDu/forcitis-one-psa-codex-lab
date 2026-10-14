package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;

record LegacyNumberResponse(DocumentType documentType, String legacyNumber, String documentId, @org.jspecify.annotations.Nullable String legalEntityId, String importBatch) {

    static LegacyNumberResponse from(LegacyNumberEntry e) {
        return new LegacyNumberResponse(e.document().documentType(), e.legacyNumber(), e.document().documentId().value(),
                e.legalEntity() == null ? null : Long.toString(e.legalEntity().value()), e.importBatch());
    }
}
