package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.DocumentTypeCode;
import java.util.Optional;

public interface DocumentTypeRegistry {
    Optional<DocumentTypeEntry> find(DocumentTypeCode code);
}
