package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** author is empty only for a seeded default template. effectiveFrom is the author's day: business dates from it on use this version once it is confirmed. */
public record TemplateVersion(NumberingRuleVersionId id, int number, NumberingPattern pattern, long startValue, LocalDate effectiveFrom, @Nullable UserId author, Instant authoredAt,
                              VersionStatus status, @Nullable UserId confirmedBy, @Nullable Instant confirmedAt,
                              @Nullable UserId financeConfirmedBy, @Nullable Instant financeConfirmedAt) {

    public TemplateVersion {
        Objects.requireNonNull(id, "id");
        if (number < 1) {
            throw new IllegalArgumentException("version numbers start at 1");
        }
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(authoredAt, "authoredAt");
        Objects.requireNonNull(status, "status");
    }

    public boolean isInForce() {
        return status == VersionStatus.CONFIRMED;
    }
}
