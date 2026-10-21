package com.forcitis.one.platform.file.api;

import java.util.Objects;

/** A rejection is a result, not an exception, so the migration can count it against the source rows (ADR-0036). */
public sealed interface ImportResult {

    record Imported(FileId id, ContentHash contentHash, long size) implements ImportResult {

        public Imported {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(contentHash, "contentHash");
        }
    }

    /** contentHash is null when the content was refused before it was fully read. */
    record ImportRejected(ImportRejectionReason reason, ContentHash contentHash) implements ImportResult {

        public ImportRejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
