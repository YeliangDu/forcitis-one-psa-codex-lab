package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.DocumentTypeCode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One registered document type (ADR-0066 decision 4); numberCode is the {TYPE} value of the unified number format, empty while the type is not part of it. */
public record DocumentTypeEntry(UUID id, DocumentTypeCode code, Optional<String> numberCode, String ownerBcCode,
        boolean financeConfirmationRequired, LocalDate effectiveFrom, Optional<LocalDate> retiredOn) {

    public DocumentTypeEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(numberCode, "numberCode");
        Objects.requireNonNull(ownerBcCode, "ownerBcCode");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(retiredOn, "retiredOn");
    }

    public boolean isInForceOn(LocalDate date) {
        return !date.isBefore(effectiveFrom) && retiredOn.map(end -> date.isBefore(end)).orElse(true);
    }
}
