package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CallerPermissions;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** RULE-385. Documents that already carry a legal entity keep it; correcting history never reaches back into them. */
@Service
public class CorrectLegalEntityAttribution {
    static final String PERMISSION = "organization.workforce:legal-entity-attribution-correction:create";

    private final LegalEntityAttributionRepository attributions;
    private final AttributionSubjects subjects;
    private final DomainEvents events;
    private final CallerPermissions caller;
    private final AuditLog audit;
    private final OwnAttributionGuard ownAttribution;

    CorrectLegalEntityAttribution(LegalEntityAttributionRepository attributions, AttributionSubjects subjects, DomainEvents events,
            CallerPermissions caller, AuditLog audit, OwnAttributionGuard ownAttribution) {
        this.ownAttribution = ownAttribution;
        this.attributions = attributions;
        this.subjects = subjects;
        this.events = events;
        this.caller = caller;
        this.audit = audit;
    }

    /** A refusal for want of the permission is audited in its own transaction, so the entry survives the refusal. */
    @Transactional
    public LegalEntityAttributionView execute(AttributionKind kind, UUID subjectId, LocalDate periodFrom, LocalDate newEffectiveTo, String reasonText,
                                              UserId correctedBy, ExpectedVersion expected) {
        if (!caller.holds(PERMISSION)) {
            audit.recordIndependently(new AuditEntry("organization.workforce.legal-entity-attribution.correction-refused", correctedBy, null,
                    "legal-entity-attribution:" + kind + ":" + subjectId, "the caller lacks " + PERMISSION)
                    .withTrace(null, AuditOutcome.FAILED, null));
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_FORBIDDEN, ErrorKind.FORBIDDEN,
                    "correcting an attribution needs " + PERMISSION);
        }
        Reason reason = Reason.ofNullable(reasonText);
        if (reason == null) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED, ErrorKind.UNPROCESSABLE,
                    "a correction needs the reason");
        }
        if (periodFrom == null || newEffectiveTo == null) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID, ErrorKind.UNPROCESSABLE,
                    "a correction names the period and its new end");
        }
        AttributionSubject subject = subjects.require(kind, subjectId);
        ownAttribution.requireNotOwn(subject, correctedBy);
        LegalEntityAttribution attribution = attributions.find(subject).orElseThrow(() -> new DomainException(
                WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_FOUND, ErrorKind.NOT_FOUND, "nothing is attributed to this subject"));
        expected.check(attribution.version());
        attribution.correct(periodFrom, newEffectiveTo, reason, correctedBy);
        if (attribution.hasPendingEvents()) {
            attributions.save(attribution);
            events.publish(attribution);
        }
        return LegalEntityAttributionView.of(attribution);
    }
}
