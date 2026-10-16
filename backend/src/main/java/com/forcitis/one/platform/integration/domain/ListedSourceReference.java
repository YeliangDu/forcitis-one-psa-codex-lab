package com.forcitis.one.platform.integration.domain;

import java.time.Instant;

public record ListedSourceReference(SourceReference reference, Instant createdAt) {

    public SourceReferencePosition position() {
        return new SourceReferencePosition(createdAt, reference.sourceReferenceId());
    }
}
