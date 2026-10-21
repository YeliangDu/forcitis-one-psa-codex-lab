package com.forcitis.one.kernel;

import java.util.Objects;

/** A business document across modules (approval, numbering, audit); replaces "type:id" strings. */
@ValueObject
public record DocumentReference(DocumentTypeCode documentType, DocumentId documentId) {

    public DocumentReference {
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(documentId, "documentId");
    }
}
