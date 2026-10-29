package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** From the day it starts up to, not including, the day it ends; open while effectiveTo is null. */
public record AttributionPeriod(LegalEntityId legalEntityId, LocalDate effectiveFrom, @Nullable LocalDate effectiveTo) {
    public AttributionPeriod {
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("a period ends after it starts");
        }
    }

    public boolean isOpen() {
        return effectiveTo == null;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveTo == null || date.isBefore(effectiveTo));
    }

    AttributionPeriod closedOn(LocalDate end) {
        return new AttributionPeriod(legalEntityId, effectiveFrom, end);
    }
}
