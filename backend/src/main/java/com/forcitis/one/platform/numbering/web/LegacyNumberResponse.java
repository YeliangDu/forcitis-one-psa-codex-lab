package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import org.jspecify.annotations.Nullable;

record LegacyNumberResponse(String documentType, String legacyNumber, String documentId, @Nullable String legalEntityId, String importBatch) {

    static LegacyNumberResponse from(LegacyNumberEntry e) {
        return new LegacyNumberResponse(e.document().documentType().name(), e.legacyNumber(), e.document().documentId().value(),
                e.legalEntity().map(le -> Long.toString(le.value())).orElse(null), e.importBatch());
    }
}
