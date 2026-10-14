package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.platform.numbering.api.RegisterLegacyNumbers;
import com.forcitis.one.platform.numbering.domain.LegacyNumberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegisterLegacyNumber implements RegisterLegacyNumbers {

    private final LegacyNumberRepository legacyNumbers;

    RegisterLegacyNumber(LegacyNumberRepository legacyNumbers) {
        this.legacyNumbers = legacyNumbers;
    }

    @Override
    @Transactional
    public void register(LegacyNumberEntry entry) {
        if (legacyNumbers.insertIfAbsent(entry)) {
            return;
        }
        LegacyNumberEntry existing = legacyNumbers.find(entry.documentType(), entry.legacyNumber()).orElseThrow();
        if (!existing.documentId().equals(entry.documentId())) {
            throw new IllegalStateException("Legacy number " + entry.legacyNumber() + " of " + entry.documentType()
                    + " already belongs to document " + existing.documentId().value());
        }
    }
}
