package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentType;

import java.util.Optional;

public interface FindDocumentByLegacyNumber {
    Optional<LegacyNumberEntry> find(DocumentType type, String legacyNumber);

}
