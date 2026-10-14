package com.forcitis.one.kernel;

import java.util.List;
import java.util.Optional;

/**
 * Asked before a sync run turns people into separated ones. Implemented by whoever owns administrator access, so that a wrong
 * source can never take every administrator away at once (ADR-0027 section 1).
 */
public interface SeparationReview {

    /**
     * Why the run waits for a person; empty when the separations may go ahead. releaser is the person releasing a held run, null for a
     * scheduled run: another holder of an administrator role (not one of those leaving) lifts the hold.
     */
    Optional<String> holdReason(List<UserId> separating, UserId releaser);
}
