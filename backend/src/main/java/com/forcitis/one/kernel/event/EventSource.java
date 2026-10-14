package com.forcitis.one.kernel.event;

import java.util.Objects;

/** documentRef is the original document number of migrated data; required when system is MIGRATION. */
public record EventSource(SourceSystem system, String documentRef) {

    public EventSource {
        Objects.requireNonNull(system, "system");
        if (system == SourceSystem.MIGRATION && (documentRef == null || documentRef.isBlank())) {
            throw new IllegalArgumentException("a migrated event needs the original document reference");
        }
    }

}
