package com.forcitis.one.platform.signing.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.ValueObject;
import java.time.LocalDate;
import java.util.Objects;

/** Half-open validity [effectiveFrom, effectiveTo); effectiveTo is null while the version is current. */
@ValueObject
public record SealLedgerEntryVersionView(SealLedgerEntryVersionId id, SealLedgerEntryId entry, LegalEntityId legalEntity,
                                         SealTypeCode sealType, SealCarrier carrier, boolean active,
                                         LocalDate effectiveFrom, LocalDate effectiveTo) {

    public SealLedgerEntryVersionView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(legalEntity, "legalEntity");
        Objects.requireNonNull(sealType, "sealType");
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be after effectiveFrom");
        }
    }
}
