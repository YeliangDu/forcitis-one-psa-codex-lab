package com.forcitis.one.platform.approval.domain;

import java.time.Instant;
import java.util.Objects;

/** dueAt: the deadline; remindAt: when the holders are reminded; escalateAt: when the task goes up (D-1a-110..113). */
public record DeadlinePlan(Instant dueAt, Instant remindAt, Instant escalateAt) {

    public DeadlinePlan {
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(remindAt, "remindAt");
        Objects.requireNonNull(escalateAt, "escalateAt");
        if (dueAt.isAfter(remindAt) || remindAt.isAfter(escalateAt)) {
            throw new IllegalArgumentException("a deadline plan runs due, then reminder, then escalation");
        }
    }
}
