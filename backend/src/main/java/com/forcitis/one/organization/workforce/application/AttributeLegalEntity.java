package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
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

/** From the given day on the department or the employee belongs to the legal entity; the period before it is closed on that day. */
@Service
public class AttributeLegalEntity {
    private final LegalEntityAttributionRepository attributions;
    private final LegalEntityQueries legalEntities;
    private final AttributionSubjects subjects;
    private final DomainEvents events;
    private final OwnAttributionGuard ownAttribution;

    AttributeLegalEntity(LegalEntityAttributionRepository attributions, LegalEntityQueries legalEntities, AttributionSubjects subjects,
                         DomainEvents events, OwnAttributionGuard ownAttribution) {
        this.ownAttribution = ownAttribution;
        this.attributions = attributions;
        this.legalEntities = legalEntities;
        this.subjects = subjects;
        this.events = events;
    }

    @Transactional
    public LegalEntityAttributionView execute(AttributionKind kind, long subjectId, long legalEntityId, LocalDate effectiveFrom, UserId actor) {
        AttributionSubject subject = subjects.require(kind, subjectId);
        ownAttribution.requireNotOwn(subject, actor);
        LegalEntityId target = legalEntityId > 0 ? new LegalEntityId(legalEntityId) : null;
        if (target == null || legalEntities.find(target).filter(e -> e.status() == LegalEntityStatus.ACTIVE).isEmpty()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND, ErrorKind.UNPROCESSABLE,
                    "legal entity " + legalEntityId + " does not exist or is not active");
        }
        LegalEntityAttribution attribution = attributions.find(subject).orElseGet(() -> LegalEntityAttribution.empty(subject));
        attribution.attribute(target, effectiveFrom);
        if (attribution.hasPendingEvents()) {
            attributions.save(attribution);
            events.publish(attribution);
        }
        return LegalEntityAttributionView.of(attribution);
    }
}
