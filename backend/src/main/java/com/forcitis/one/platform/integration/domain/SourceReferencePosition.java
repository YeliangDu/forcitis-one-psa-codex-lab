package com.forcitis.one.platform.integration.domain;

import java.time.Instant;

/** Where a page of references ended: the moment the row was made and its id, because ids alone do not tell the order the rows were made in. */
public record SourceReferencePosition(Instant createdAt, SourceReferenceId id) {
}
