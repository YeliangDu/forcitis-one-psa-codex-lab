package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;

record LegacyNumberResponse(String documentType, String legacyNumber, String documentId, String legalEntityId, String importBatch) {

    static LegacyNumberResponse from(LegacyNumberEntry e) {
        return new LegacyNumberResponse(e.document().documentType().name(), e.legacyNumber(), e.document().documentId().value(),
                Long.toString(e.legalEntity().value()), e.importBatch());
    }
}
