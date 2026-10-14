package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries.LegalEntityRecord;
import com.forcitis.one.organization.workforce.domain.LegalEntityStatus;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class LegalEntityDirectoryService implements LegalEntityDirectory {
    private final LegalEntityQueries legalEntities;

    LegalEntityDirectoryService(LegalEntityQueries legalEntities) {
        this.legalEntities = legalEntities;
    }

    @Override
    public Optional<LegalEntityCode> findCode(LegalEntityId legalEntityId) {
        return legalEntities.find(legalEntityId).map(LegalEntityRecord::code);
    }

    @Override
    public Optional<LegalEntityCode> findActiveCode(LegalEntityId legalEntityId) {
        return legalEntities.find(legalEntityId).filter(e -> e.status() == LegalEntityStatus.ACTIVE).map(LegalEntityRecord::code);
    }
}
