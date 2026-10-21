package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.platform.numbering.api.RegisterLegacyNumbers;
import com.forcitis.one.platform.numbering.domain.DocumentTypeRegistry;
import com.forcitis.one.platform.numbering.domain.LegacyNumberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegisterLegacyNumber implements RegisterLegacyNumbers {

    private final LegacyNumberRepository legacyNumbers;
    private final DocumentTypeRegistry registry;

    RegisterLegacyNumber(LegacyNumberRepository legacyNumbers, DocumentTypeRegistry registry) {
        this.legacyNumbers = legacyNumbers;
        this.registry = registry;
    }

    @Override
    @Transactional
    public void register(LegacyNumberEntry entry) {
        if (registry.find(entry.document().documentType()).isEmpty()) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED, ErrorKind.UNPROCESSABLE,
                    "document type " + entry.document().documentType() + " is not registered");
        }
        if (legacyNumbers.insertIfAbsent(entry)) {
            return;
        }
        LegacyNumberEntry existing = legacyNumbers.find(entry.sourceSystem(), entry.document().documentType(), entry.legacyNumber()).orElseThrow();
        if (!existing.document().equals(entry.document())) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_LEGACY_NUMBER_TAKEN, ErrorKind.CONFLICT, "Legacy number " + entry.legacyNumber() + " of " + entry.document().documentType() + " from " + entry.sourceSystem()
                    + " already belongs to document " + existing.document().documentId().value());
        }
    }
}
