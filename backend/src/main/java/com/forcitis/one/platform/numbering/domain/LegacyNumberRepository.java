package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import java.util.List;
import java.util.Optional;

public interface LegacyNumberRepository {

    Optional<LegacyNumberEntry> find(ExternalSource sourceSystem, DocumentTypeCode type, String legacyNumber);

    List<LegacyNumberEntry> findByPrefix(ExternalSource sourceSystem, DocumentTypeCode type, String prefix, String after, int limit);

    List<LegacyNumberEntry> findByDocument(DocumentReference document);

    /** Inserts the link unless the (source system, type, legacy number) key exists; returns whether a row was inserted. */
    boolean insertIfAbsent(LegacyNumberEntry entry);
}
