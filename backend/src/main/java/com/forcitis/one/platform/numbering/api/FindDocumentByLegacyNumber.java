package com.forcitis.one.platform.numbering.api;

import java.util.Optional;

public interface FindDocumentByLegacyNumber {
    Optional<LegacyNumberEntry> find(DocumentType type, String legacyNumber);
}
