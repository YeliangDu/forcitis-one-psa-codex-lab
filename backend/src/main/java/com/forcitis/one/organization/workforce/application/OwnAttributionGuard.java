package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Nobody changes which legal entity they belong to: not their own entry, and not the entry of their department or any department
 * above it, which they inherit (ADR-0027 section 2). The refusal is audited in its own transaction, so the attempt stays on record.
 */
@Component
class OwnAttributionGuard {
    private final OrgSnapshotQueries snapshot;
    private final AuditLog audit;

    OwnAttributionGuard(OrgSnapshotQueries snapshot, AuditLog audit) {
        this.snapshot = snapshot;
        this.audit = audit;
    }

    void requireNotOwn(AttributionSubject subject, UserId actor) {
        if (!concerns(subject, actor)) {
            return;
        }
        audit.recordIndependently(new AuditEntry("organization.workforce.legal-entity-attribution.refused", actor, null,
                "legal-entity-attribution:" + subject.kind().name().toLowerCase(Locale.ROOT) + ":" + subject.subjectKey(),
                "own legal entity attribution"));
        throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN, ErrorKind.FORBIDDEN,
                "the legal entity of your own employee entry, department or a department above it cannot be changed by you");
    }

    private boolean concerns(AttributionSubject subject, UserId actor) {
        if (subject.kind() == AttributionKind.EMPLOYEE) {
            return subject.subjectKey() == actor.value();
        }
        List<OrgUnitId> own = new ArrayList<>();
        snapshot.findStaff(actor).ifPresent(staff -> {
            own.add(staff.assignment().orgUnitId());
            own.addAll(snapshot.findAncestors(staff.assignment().orgUnitId()));
        });
        return own.contains(new OrgUnitId(subject.subjectKey()));
    }
}
