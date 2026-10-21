package com.forcitis.one.platform.file.api;

import java.time.Instant;
import java.util.Objects;

public record StoredFileSummary(FileId id, ContentHash contentHash, long size, String mediaType, String originalName, FileStatus status,
                                boolean containsMacros, FileSubject uploader, Instant storedAt, Lineage lineage) {

    public StoredFileSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(originalName, "originalName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(uploader, "uploader");
        Objects.requireNonNull(storedAt, "storedAt");
        Objects.requireNonNull(lineage, "lineage");
    }
}
