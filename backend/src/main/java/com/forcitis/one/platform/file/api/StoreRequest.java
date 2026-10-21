package com.forcitis.one.platform.file.api;

import java.io.InputStream;
import java.util.Objects;

/** declaredMediaType is a hint only: the registered media type is judged from the content. */
public record StoreRequest(InputStream content, String originalName, String declaredMediaType, FileSubject uploader, Lineage lineage) {

    public StoreRequest {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(originalName, "originalName");
        Objects.requireNonNull(declaredMediaType, "declaredMediaType");
        Objects.requireNonNull(uploader, "uploader");
        Objects.requireNonNull(lineage, "lineage");
        if (originalName.isBlank()) {
            throw new IllegalArgumentException("A file needs its original name");
        }
    }
}
