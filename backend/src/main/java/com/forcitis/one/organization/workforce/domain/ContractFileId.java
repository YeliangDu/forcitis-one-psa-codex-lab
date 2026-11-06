package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** The signed contract file, kept by platform.file; workforce holds only its id. */
@ValueObject
public record ContractFileId(UUID value) {

    public ContractFileId {
        value = Uuids.requireKey(value, "ContractFileId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
