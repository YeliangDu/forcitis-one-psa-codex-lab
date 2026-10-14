package com.forcitis.one.platform.numbering.api;

import java.util.Optional;

public interface FindDocumentByLegacyNumber {

    /** The document that carried the legacy number. */
    Optional<LegacyNumberEntry> find(DocumentType type, String legacyNumber);
}
