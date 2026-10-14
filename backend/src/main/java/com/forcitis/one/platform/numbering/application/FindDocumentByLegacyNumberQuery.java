package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.api.FindDocumentByLegacyNumber;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.platform.numbering.domain.LegacyNumberRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FindDocumentByLegacyNumberQuery implements FindDocumentByLegacyNumber {

    private final LegacyNumberRepository legacyNumbers;

    FindDocumentByLegacyNumberQuery(LegacyNumberRepository legacyNumbers) {
        this.legacyNumbers = legacyNumbers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LegacyNumberEntry> find(DocumentType type, String legacyNumber) {
        return legacyNumbers.find(type, legacyNumber);
    }
}
