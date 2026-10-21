package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.numbering.api.DocumentTypes;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.domain.DocumentTypeRegistry;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
class CheckDocumentType implements DocumentTypes {

    private final DocumentTypeRegistry registry;

    CheckDocumentType(DocumentTypeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void requireInForce(DocumentTypeCode code, LocalDate onDate) {
        if (registry.find(code).filter(entry -> entry.isInForceOn(onDate)).isEmpty()) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED, ErrorKind.UNPROCESSABLE,
                    "document type " + code + " is not registered or not in force on " + onDate);
        }
    }
}
