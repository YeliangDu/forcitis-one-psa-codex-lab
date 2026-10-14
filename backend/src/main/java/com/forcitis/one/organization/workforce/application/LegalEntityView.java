package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.LegalEntity;

public record LegalEntityView(long legalEntityId, String code, String name, String status) {
    static LegalEntityView of(LegalEntity legalEntity) {
        return new LegalEntityView(legalEntity.legalEntityId().value(), legalEntity.code().value(), legalEntity.name(),
                legalEntity.status().name());
    }
}
