package com.forcitis.one.platform.numbering.web;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record LegacyNumberResponse(ExternalSource sourceSystem, DocumentTypeCode documentType, String legacyNumber, UUID documentId, @Nullable UUID legalEntityId,
                            UUID migrationBatchId) {

    static LegacyNumberResponse from(LegacyNumberEntry e) {
        return new LegacyNumberResponse(e.sourceSystem(), e.document().documentType(), e.legacyNumber(), e.document().documentId().value(),
                e.legalEntity().map(le -> le.value()).orElse(null), e.migrationBatchId().value());
    }
}
