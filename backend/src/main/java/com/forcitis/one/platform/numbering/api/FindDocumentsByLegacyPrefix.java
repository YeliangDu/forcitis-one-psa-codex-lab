package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentType;
import java.util.List;

public interface FindDocumentsByLegacyPrefix {
    /** Original numbers that begin with the prefix (at least 2 characters), ordered by number, after the given one; at most limit. */
    List<LegacyNumberEntry> findByPrefix(DocumentType type, String prefix, String after, int limit);
}
