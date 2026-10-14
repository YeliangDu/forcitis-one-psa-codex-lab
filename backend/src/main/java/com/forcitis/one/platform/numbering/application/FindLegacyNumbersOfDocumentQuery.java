package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.api.DocumentId;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.platform.numbering.api.FindLegacyNumbersOfDocument;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.platform.numbering.domain.LegacyNumberRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FindLegacyNumbersOfDocumentQuery implements FindLegacyNumbersOfDocument {

    private final LegacyNumberRepository legacyNumbers;

    FindLegacyNumbersOfDocumentQuery(LegacyNumberRepository legacyNumbers) {
        this.legacyNumbers = legacyNumbers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyNumberEntry> find(DocumentType type, DocumentId documentId) {
        return legacyNumbers.findByDocument(type, documentId);
    }
}
