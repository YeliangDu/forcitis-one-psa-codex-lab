package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.platform.numbering.api.DocumentId;
import com.forcitis.one.platform.numbering.api.DocumentType;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import java.util.List;
import java.util.Optional;

public interface LegacyNumberRepository {

    Optional<LegacyNumberEntry> find(DocumentType type, String legacyNumber);

    List<LegacyNumberEntry> findByDocument(DocumentType type, DocumentId documentId);

    /** Inserts the link unless the (type, legacy number) pair exists; returns whether a row was inserted. */
    boolean insertIfAbsent(LegacyNumberEntry entry);
}
