package com.forcitis.one.platform.approval.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** When a change happens: the instant, and the business date cut in the business zone. */
public record Moment(Instant at, LocalDate date) {

    public Moment {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(date, "date");
    }
}
