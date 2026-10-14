package com.forcitis.one.platform.integration.infrastructure.registry;

import java.time.Instant;

/** What the registry holds that nobody has completed, counted for the alerts. */
interface EventPublicationStatistics {

    /** Incomplete publications that have been tried at least maxAttempts times: the republish job no longer takes them. */
    long exhausted(int maxAttempts);

    /** Incomplete publications published before the cutoff. */
    long incompleteBefore(Instant cutoff);
}
