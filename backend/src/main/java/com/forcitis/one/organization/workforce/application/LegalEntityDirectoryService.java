package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries.LegalEntityRecord;
import com.forcitis.one.organization.workforce.domain.LegalEntityStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
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

    /** The share lock lasts to the end of the caller's transaction; without one it would protect nothing, so none is started here. */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<LegalEntityCode> lockActiveCode(LegalEntityId legalEntityId) {
        return legalEntities.findSharedLocked(legalEntityId).filter(e -> e.status() == LegalEntityStatus.ACTIVE).map(LegalEntityRecord::code);
    }

    @Override
    public List<LegalEntityId> allIds() {
        return legalEntities.findAll().stream().map(LegalEntityRecord::legalEntityId).toList();
    }
}
