package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.KnownLegalEntities;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries;
import org.springframework.stereotype.Component;

@Component
class KnownLegalEntitiesService implements KnownLegalEntities {
    private final LegalEntityQueries legalEntities;

    KnownLegalEntitiesService(LegalEntityQueries legalEntities) {
        this.legalEntities = legalEntities;
    }

    @Override
    public boolean isKnown(LegalEntityId legalEntityId) {
        return legalEntities.find(legalEntityId).isPresent();
    }
}
