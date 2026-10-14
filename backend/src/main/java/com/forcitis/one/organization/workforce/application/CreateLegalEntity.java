package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateLegalEntity {
    private final LegalEntityRepository legalEntities;
    private final DomainEvents events;

    CreateLegalEntity(LegalEntityRepository legalEntities, DomainEvents events) {
        this.legalEntities = legalEntities;
        this.events = events;
    }

    @Transactional
    public LegalEntityView execute(String code, String name) {
        LegalEntity saved = legalEntities.insert(LegalEntity.register(parse(code), name));
        events.publish(saved);
        return LegalEntityView.of(saved);
    }

    static LegalEntityCode parse(String code) {
        try {
            return new LegalEntityCode(code);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_INVALID, ErrorKind.UNPROCESSABLE,
                    "a legal entity code is 2 to 10 upper-case letters");
        }
    }
}
