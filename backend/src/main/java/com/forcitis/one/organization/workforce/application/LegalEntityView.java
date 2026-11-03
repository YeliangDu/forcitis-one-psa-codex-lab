package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries.LegalEntityRecord;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record LegalEntityView(LegalEntityId legalEntityId, String code, String name, String status, long version, @Nullable UUID financeHeadEmployeeId) {
    static LegalEntityView of(LegalEntity legalEntity) {
        return new LegalEntityView(legalEntity.legalEntityId(), legalEntity.code().value(), legalEntity.name(),
                legalEntity.status().name(), legalEntity.version(),
                legalEntity.financeHead() == null ? null : legalEntity.financeHead().value());
    }

    static LegalEntityView of(LegalEntityRecord record) {
        return new LegalEntityView(record.legalEntityId(), record.code().value(), record.name(), record.status().name(), record.version(),
                record.financeHead() == null ? null : record.financeHead().value());
    }
}
