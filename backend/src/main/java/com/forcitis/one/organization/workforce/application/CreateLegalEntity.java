package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateLegalEntity {

    private final LegalEntityRepository legalEntities;
    private final AuditLog audit;

    CreateLegalEntity(LegalEntityRepository legalEntities, AuditLog audit) {
        this.legalEntities = legalEntities;
        this.audit = audit;
    }

    @Transactional
    public LegalEntityOutcome execute(String code, String name, long actorUserId) {
        LegalEntityCode legalEntityCode;
        try {
            legalEntityCode = new LegalEntityCode(code);
            if (name == null || name.isBlank() || name.length() > LegalEntity.MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("a legal entity name is 1 to " + LegalEntity.MAX_NAME_LENGTH + " characters");
            }
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return new LegalEntityOutcome.Rejected(invalid.getMessage());
        }
        Optional<LegalEntity> created = legalEntities.insert(legalEntityCode, name);
        if (created.isEmpty()) {
            return new LegalEntityOutcome.CodeTaken();
        }
        LegalEntity legalEntity = created.get();
        audit.record(new AuditEntry("organization.workforce.legal-entity.created", new UserId(actorUserId), null,
                "legal-entity:" + legalEntity.legalEntityId().value(), "created " + legalEntity.code().value() + " " + legalEntity.name()));
        return new LegalEntityOutcome.Saved(LegalEntityView.of(legalEntity));
    }
}
