package com.forcitis.one.platform.file.domain;

import java.util.Objects;

/** What the file service reads from the content itself, never from the name or the declared type (ADR-0070 section 5 item 1). */
public record ContentFacts(String mediaType, boolean encrypted, boolean containsMacros) {

    public ContentFacts {
        Objects.requireNonNull(mediaType, "mediaType");
    }
}
