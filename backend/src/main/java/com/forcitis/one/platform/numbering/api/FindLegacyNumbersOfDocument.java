package com.forcitis.one.platform.numbering.api;

import java.util.List;

public interface FindLegacyNumbersOfDocument {
    /** Oldest first; a document can carry several (e.g. one per revision). */
    List<LegacyNumberEntry> find(DocumentType type, DocumentId documentId);
}
