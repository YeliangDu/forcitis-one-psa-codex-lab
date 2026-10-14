package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries.LegalEntityRecord;

public record LegalEntityView(long legalEntityId, String code, String name, String status, long version) {
    static LegalEntityView of(LegalEntity legalEntity) {
        return new LegalEntityView(legalEntity.legalEntityId().value(), legalEntity.code().value(), legalEntity.name(),
                legalEntity.status().name(), legalEntity.version());
    }

    static LegalEntityView of(LegalEntityRecord record) {
        return new LegalEntityView(record.legalEntityId().value(), record.code().value(), record.name(), record.status().name(), record.version());
    }
}
