package com.forcitis.one.platform.integration.api;

import java.util.Objects;

/** threatName is set for INFECTED only. */
public record ScanVerdict(ScanOutcome outcome, String threatName, String engineVersion, String definitionsVersion) {

    public ScanVerdict {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(engineVersion, "engineVersion");
        Objects.requireNonNull(definitionsVersion, "definitionsVersion");
        if ((outcome == ScanOutcome.INFECTED) != (threatName != null && !threatName.isBlank())) {
            throw new IllegalArgumentException("A threat name goes with INFECTED and only with it");
        }
    }
}
