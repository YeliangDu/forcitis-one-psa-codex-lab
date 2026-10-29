package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.audit.api.AuditSubject;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** RULE-385. Documents that already carry a legal entity keep it; correcting history never reaches back into them. */
@Service
public class CorrectLegalEntityAttribution {
    static final String PERMISSION = "organization.workforce:legal-entity-attribution-correction:create";
    static final int MAX_REASON_LENGTH = 200;
    private static final Logger LOG = LoggerFactory.getLogger(CorrectLegalEntityAttribution.class);

    private final LegalEntityAttributionRepository attributions;
    private final AttributionSubjects subjects;
    private final DomainEvents events;
    private final CallerPermissions caller;
    private final AuditLog audit;
    private final OwnAttributionGuard ownAttribution;
    private final CurrentUser currentUser;

    CorrectLegalEntityAttribution(LegalEntityAttributionRepository attributions, AttributionSubjects subjects, DomainEvents events,
            CallerPermissions caller, AuditLog audit, OwnAttributionGuard ownAttribution, CurrentUser currentUser) {
        this.currentUser = currentUser;
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
                                              ExpectedVersion expected) {
        UserId correctedBy = currentUser.userId();
        if (!caller.holds(PERMISSION)) {
            audit.recordIndependently(new AuditEntry("organization.workforce.legal-entity-attribution.correction-refused", correctedBy, null,
                    AuditSubject.of(AttributionAuditKinds.of(kind), subjectId), "the caller lacks " + PERMISSION)
                    .withTrace(null, AuditOutcome.FAILED, null));
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_FORBIDDEN, ErrorKind.FORBIDDEN,
                    "correcting an attribution needs " + PERMISSION);
        }
        Reason reason = Reason.ofNullable(reasonText);
        if (reason == null) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED, ErrorKind.UNPROCESSABLE,
                    "a correction needs the reason");
        }
        if (reason.value().codePointCount(0, reason.value().length()) > MAX_REASON_LENGTH) {
            LOG.error("ALERT organization.workforce the reason to correct an attribution of {} {} by user {} exceeds {} characters: refused", kind, subjectId,
                    correctedBy.value(), MAX_REASON_LENGTH);
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED, ErrorKind.UNPROCESSABLE,
                    "a reason of 1.." + MAX_REASON_LENGTH + " characters is required");
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
