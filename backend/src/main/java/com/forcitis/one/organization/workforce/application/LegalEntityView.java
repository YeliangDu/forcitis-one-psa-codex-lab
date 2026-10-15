package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries.LegalEntityRecord;

public record LegalEntityView(LegalEntityId legalEntityId, String code, String name, String status, Long accountingOrganizationNumber, long version) {
    static LegalEntityView of(LegalEntity legalEntity) {
        return new LegalEntityView(legalEntity.legalEntityId(), legalEntity.code().value(), legalEntity.name(),
                legalEntity.status().name(),
                legalEntity.accountingOrganizationNumber() == null ? null : legalEntity.accountingOrganizationNumber().value(), legalEntity.version());
    }

    static LegalEntityView of(LegalEntityRecord record) {
        return new LegalEntityView(record.legalEntityId(), record.code().value(), record.name(), record.status().name(), record.accountingOrganizationNumber(), record.version());
    }
}
