package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetLegalEntity {
    private final LegalEntityQueries legalEntities;

    GetLegalEntity(LegalEntityQueries legalEntities) {
        this.legalEntities = legalEntities;
    }

    @Transactional(readOnly = true)
    public LegalEntityView execute(LegalEntityId legalEntityId) {
        return legalEntities.find(legalEntityId).map(LegalEntityView::of)
                .orElseThrow(() -> new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND, ErrorKind.NOT_FOUND,
                        "no legal entity " + legalEntityId.value()));
    }
}
