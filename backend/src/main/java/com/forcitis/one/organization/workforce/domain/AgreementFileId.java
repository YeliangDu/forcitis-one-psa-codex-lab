package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** The signed separation agreement file, kept by platform.file; workforce holds only its id. */
@ValueObject
public record AgreementFileId(UUID value) {

    public AgreementFileId {
        value = Uuids.requireKey(value, "AgreementFileId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
