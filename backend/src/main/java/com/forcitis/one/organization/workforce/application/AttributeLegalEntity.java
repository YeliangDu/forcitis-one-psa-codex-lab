package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.AttributionId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionRepository;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries;
import com.forcitis.one.organization.workforce.domain.LegalEntityStatus;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** From the given day on the department or the employee belongs to the legal entity; the period before it is closed on that day. */
@Service
public class AttributeLegalEntity {
    private final LegalEntityAttributionRepository attributions;
    private final LegalEntityQueries legalEntities;
    private final AttributionSubjects subjects;
    private final DomainEvents events;
    private final OwnAttributionGuard ownAttribution;
    private final IdGenerator ids;
    private final CurrentUser currentUser;

    AttributeLegalEntity(LegalEntityAttributionRepository attributions, LegalEntityQueries legalEntities, AttributionSubjects subjects,
                         DomainEvents events, OwnAttributionGuard ownAttribution, IdGenerator ids, CurrentUser currentUser) {
        this.currentUser = currentUser;
        this.ids = ids;
        this.ownAttribution = ownAttribution;
        this.attributions = attributions;
        this.legalEntities = legalEntities;
        this.subjects = subjects;
        this.events = events;
    }

    @Transactional
    public LegalEntityAttributionView execute(AttributionKind kind, UUID subjectId, LegalEntityId target, LocalDate effectiveFrom, ExpectedVersion expected) {
        AttributionSubject subject = subjects.require(kind, subjectId);
        ownAttribution.requireNotOwn(subject, currentUser.userId());
        if (target == null || legalEntities.find(target).filter(e -> e.status() == LegalEntityStatus.ACTIVE).isEmpty()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND, ErrorKind.UNPROCESSABLE,
                    "legal entity " + (target == null ? null : target.value()) + " does not exist or is not active");
        }
        LegalEntityAttribution stored = attributions.find(subject).orElse(null);
        expected.check(stored == null ? null : stored.version());
        LegalEntityAttribution attribution = stored != null ? stored : LegalEntityAttribution.empty(new AttributionId(ids.next()), subject);
        attribution.attribute(target, effectiveFrom);
        if (attribution.hasPendingEvents()) {
            attributions.save(attribution);
            events.publish(attribution);
        }
        return LegalEntityAttributionView.of(attribution);
    }
}
