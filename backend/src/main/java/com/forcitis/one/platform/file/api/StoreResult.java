package com.forcitis.one.platform.file.api;

import java.util.Objects;

/** status is AVAILABLE or QUARANTINED; refused content (too large, encrypted, scan unavailable) gets no file and throws instead. */
public record StoreResult(FileId id, ContentHash contentHash, long size, String mediaType, FileStatus status, boolean containsMacros) {

    public StoreResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(status, "status");
    }
}
