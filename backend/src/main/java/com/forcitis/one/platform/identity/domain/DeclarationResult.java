package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The outcome of reconciling one declared system role, with the highest declaration revision the database held before (empty when none). */
@ValueObject
public record DeclarationResult(DeclarationOutcome outcome, @Nullable Integer storedRevisionNumber) {

    public DeclarationResult {
        Objects.requireNonNull(outcome, "outcome");
    }
}
