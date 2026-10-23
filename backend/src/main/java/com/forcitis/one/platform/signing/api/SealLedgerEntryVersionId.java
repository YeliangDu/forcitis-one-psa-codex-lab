package com.forcitis.one.platform.signing.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record SealLedgerEntryVersionId(UUID value) {

    public SealLedgerEntryVersionId {
        value = Uuids.requireKey(value, "SealLedgerEntryVersionId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
