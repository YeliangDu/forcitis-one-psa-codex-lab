package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;

/** Ids are strings in JSON (no precision loss in browsers). */
record LegacyNumberResponse(String documentType, String legacyNumber, String documentId, String legalEntityId, String importBatch) {

    static LegacyNumberResponse from(LegacyNumberEntry e) {
        return new LegacyNumberResponse(e.documentType().name(), e.legacyNumber(), Long.toString(e.documentId().value()),
                Long.toString(e.legalEntity().value()), e.importBatch());
    }
}
