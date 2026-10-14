package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityRepository;
import com.forcitis.one.organization.workforce.domain.LegalEntityStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The code is never changed: issued document numbers carry it. */
@Service
public class UpdateLegalEntity {
    private final LegalEntityRepository legalEntities;
    private final AuditLog audit;

    UpdateLegalEntity(LegalEntityRepository legalEntities, AuditLog audit) {
        this.legalEntities = legalEntities;
        this.audit = audit;
    }

    @Transactional
    public LegalEntityOutcome execute(long legalEntityId, String name, String status, long actorUserId) {
        if (legalEntityId <= 0) {
            return new LegalEntityOutcome.NotFound();
        }
        Optional<LegalEntity> found = legalEntities.find(new LegalEntityId(legalEntityId));
        if (found.isEmpty()) {
            return new LegalEntityOutcome.NotFound();
        }
        LegalEntity updated;
        try {
            updated = found.get().update(name, LegalEntityStatus.valueOf(status));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return new LegalEntityOutcome.Rejected("name must be 1 to " + LegalEntity.MAX_NAME_LENGTH + " characters and status ACTIVE or INACTIVE");
        }
        legalEntities.update(updated);
        audit.record(new AuditEntry("organization.workforce.legal-entity.updated", new UserId(actorUserId), null,
                "legal-entity:" + legalEntityId, updated.code().value() + " now " + updated.status() + " named " + updated.name()));
        return new LegalEntityOutcome.Saved(LegalEntityView.of(updated));
    }
}
