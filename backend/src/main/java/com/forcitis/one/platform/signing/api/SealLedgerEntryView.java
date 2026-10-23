package com.forcitis.one.platform.signing.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.ValueObject;
import java.time.LocalDate;
import java.util.Objects;

@ValueObject
public record SealLedgerEntryView(SealLedgerEntryId id, LegalEntityId legalEntity, SealTypeCode sealType,
                                  SealLedgerEntryVersionId currentVersion, SealCarrier carrier, LocalDate effectiveFrom) {

    public SealLedgerEntryView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(legalEntity, "legalEntity");
        Objects.requireNonNull(sealType, "sealType");
        Objects.requireNonNull(currentVersion, "currentVersion");
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    }
}
