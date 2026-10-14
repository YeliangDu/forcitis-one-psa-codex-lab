package com.forcitis.one.platform.numbering.api;

import java.util.List;

public interface FindLegacyNumbersOfDocument {

    /** Every legacy number of the document (a document can carry several, e.g. one per revision), oldest first. */
    List<LegacyNumberEntry> find(DocumentType type, DocumentId documentId);
}
