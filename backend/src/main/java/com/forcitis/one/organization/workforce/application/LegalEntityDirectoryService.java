package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class LegalEntityDirectoryService implements LegalEntityDirectory {

    private final LegalEntityRepository legalEntities;

    LegalEntityDirectoryService(LegalEntityRepository legalEntities) {
        this.legalEntities = legalEntities;
    }

    @Override
    public Optional<LegalEntityCode> findCode(LegalEntityId legalEntityId) {
        return legalEntities.find(legalEntityId).map(LegalEntity::code);
    }
}
