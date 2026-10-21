package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.integration.api.ExternalSource;
import java.util.List;

public interface FindDocumentsByLegacyPrefix {
    /** Original numbers of one source system that begin with the prefix (at least 2 characters), ordered by number, after the given one; at most limit. */
    List<LegacyNumberEntry> findByPrefix(ExternalSource sourceSystem, DocumentTypeCode type, String prefix, String after, int limit);
}
