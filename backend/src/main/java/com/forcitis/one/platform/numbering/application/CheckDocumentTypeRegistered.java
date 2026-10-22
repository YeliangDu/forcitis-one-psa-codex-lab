package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.RegisteredDocumentTypes;
import com.forcitis.one.platform.numbering.domain.DocumentTypeRegistry;
import org.springframework.stereotype.Service;

@Service
class CheckDocumentTypeRegistered implements RegisteredDocumentTypes {

    private final DocumentTypeRegistry registry;

    CheckDocumentTypeRegistered(DocumentTypeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void requireRegistered(DocumentTypeCode code) {
        if (registry.find(code).isEmpty()) {
            throw new DomainException(NumberingError.PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED, ErrorKind.UNPROCESSABLE,
                    "document type " + code + " is not registered");
        }
    }
}
