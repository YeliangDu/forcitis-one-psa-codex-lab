package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.DocumentType;

import java.util.List;

public interface FindLegacyNumbersOfDocument {
    /** Oldest first; a document can carry several (e.g. one per revision). */
    List<LegacyNumberEntry> find(DocumentType type, DocumentId documentId);
}
