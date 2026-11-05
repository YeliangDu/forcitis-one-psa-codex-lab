package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.RegisteredLegalEntities;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries.LegalEntityRecord;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class RegisteredLegalEntitiesService implements RegisteredLegalEntities {
    private final LegalEntityQueries legalEntities;

    RegisteredLegalEntitiesService(LegalEntityQueries legalEntities) {
        this.legalEntities = legalEntities;
    }

    @Override
    public List<LegalEntityId> all() {
        return legalEntities.findAll().stream().map(LegalEntityRecord::legalEntityId).toList();
    }
}
