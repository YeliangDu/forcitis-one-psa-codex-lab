package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.LegalEntityNumberUsage;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A null code leaves the code alone; a different one is a correction and is refused once numbers carry the old code. */
@Service
public class UpdateLegalEntity {
    private final LegalEntityRepository legalEntities;
    private final LegalEntityNumberUsage numberUsage;
    private final DomainEvents events;

    UpdateLegalEntity(LegalEntityRepository legalEntities, LegalEntityNumberUsage numberUsage, DomainEvents events) {
        this.legalEntities = legalEntities;
        this.numberUsage = numberUsage;
        this.events = events;
    }

    @Transactional
    public LegalEntityView execute(long legalEntityId, String code, String name, String status) {
        if (legalEntityId <= 0) {
            throw notFound(legalEntityId);
        }
        LegalEntityId id = new LegalEntityId(legalEntityId);
        LegalEntity legalEntity = legalEntities.find(id).orElseThrow(() -> notFound(legalEntityId));
        if (code != null) {
            var corrected = CreateLegalEntity.parse(code);
            legalEntity.correctCode(corrected, !corrected.equals(legalEntity.code()) && numberUsage.hasIssuedNumbers(id));
        }
        legalEntity.rename(name);
        legalEntity.declareStatus(status);
        legalEntities.save(legalEntity);
        events.publish(legalEntity);
        return LegalEntityView.of(legalEntity);
    }

    private static DomainException notFound(long legalEntityId) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND, ErrorKind.NOT_FOUND,
                "no legal entity " + legalEntityId);
    }
}
