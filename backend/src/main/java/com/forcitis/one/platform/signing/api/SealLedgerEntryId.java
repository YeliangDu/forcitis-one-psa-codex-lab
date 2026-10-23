package com.forcitis.one.platform.signing.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record SealLedgerEntryId(UUID value) {

    public SealLedgerEntryId {
        value = Uuids.requireKey(value, "SealLedgerEntryId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
