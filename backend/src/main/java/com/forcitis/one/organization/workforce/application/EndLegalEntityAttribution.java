package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class EndLegalEntityAttribution {
    private final LegalEntityAttributionRepository attributions;
    private final AttributionSubjects subjects;
    private final DomainEvents events;
    private final OwnAttributionGuard ownAttribution;
    private final CurrentUser currentUser;

    EndLegalEntityAttribution(LegalEntityAttributionRepository attributions, AttributionSubjects subjects, DomainEvents events,
                              OwnAttributionGuard ownAttribution, CurrentUser currentUser) {
        this.currentUser = currentUser;
        this.ownAttribution = ownAttribution;
        this.attributions = attributions;
        this.subjects = subjects;
        this.events = events;
    }

    @Transactional
    public LegalEntityAttributionView execute(AttributionKind kind, UUID subjectId, LocalDate effectiveTo, ExpectedVersion expected) {
        AttributionSubject subject = subjects.require(kind, subjectId);
        ownAttribution.requireNotOwn(subject, currentUser.userId());
        LegalEntityAttribution attribution = attributions.find(subject).orElseThrow(() -> new DomainException(
                WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_FOUND, ErrorKind.NOT_FOUND, "nothing is attributed to this subject"));
        expected.check(attribution.version());
        attribution.end(effectiveTo);
        attributions.save(attribution);
        events.publish(attribution);
        return LegalEntityAttributionView.of(attribution);
    }
}
