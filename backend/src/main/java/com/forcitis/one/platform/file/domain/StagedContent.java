package com.forcitis.one.platform.file.domain;

import com.forcitis.one.platform.file.api.ContentHash;
import java.nio.file.Path;
import java.util.Objects;

/** The one local copy that is hashed, scanned and uploaded (ADR-0070 section 2 item 3). */
public record StagedContent(Path path, ContentHash contentHash, long size) {

    public StagedContent {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(contentHash, "contentHash");
    }
}
