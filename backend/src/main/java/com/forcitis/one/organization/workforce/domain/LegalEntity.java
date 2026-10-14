package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import java.util.Objects;

public record LegalEntity(LegalEntityId legalEntityId, LegalEntityCode code, String name, LegalEntityStatus status) {

    public static final int MAX_NAME_LENGTH = 100;

    public LegalEntity {
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(status, "status");
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("a legal entity name is 1 to " + MAX_NAME_LENGTH + " characters");
        }
        name = name.trim();
    }

    public LegalEntity update(String newName, LegalEntityStatus newStatus) {
        return new LegalEntity(legalEntityId, code, newName, newStatus);
    }
}
