package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Nobody changes which legal entity they belong to: not their own entry, and not the entry of their department or any department
 * above it, which they inherit (ADR-0027 section 2). The refusal is audited in its own transaction, so the attempt stays on record.
 * The subject is an employee or a department, so the actor is set against it as the employee behind the account.
 */
@Component
class OwnAttributionGuard {
    private static final Logger LOG = LoggerFactory.getLogger(OwnAttributionGuard.class);

    private final OrgSnapshotQueries snapshot;
    private final AuditLog audit;
    private final AccountEmployees accountEmployees;

    OwnAttributionGuard(OrgSnapshotQueries snapshot, AuditLog audit, AccountEmployees accountEmployees) {
        this.snapshot = snapshot;
        this.audit = audit;
        this.accountEmployees = accountEmployees;
    }

    void requireNotOwn(AttributionSubject subject, UserId actor) {
        Optional<EmployeeId> actingEmployee = accountEmployees.employeeOf(actor);
        if (actingEmployee.isEmpty()) {
            LOG.error("ALERT organization.workforce account {} names no employee: its legal entity attribution change is refused", actor.value());
            refuse(subject, actor, "the account names no employee");
        }
        if (concerns(subject, actingEmployee.get())) {
            refuse(subject, actor, "own legal entity attribution");
        }
    }

    private void refuse(AttributionSubject subject, UserId actor, String detail) {
        audit.recordIndependently(new AuditEntry("organization.workforce.legal-entity-attribution.refused", actor, null,
                "legal-entity-attribution:" + subject.kind().name().toLowerCase(Locale.ROOT) + ":" + subject.subjectKey(), detail));
        throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN, ErrorKind.FORBIDDEN,
                "the legal entity of your own employee entry, department or a department above it cannot be changed by you");
    }

    private boolean concerns(AttributionSubject subject, EmployeeId actingEmployee) {
        if (subject.kind() == AttributionKind.EMPLOYEE) {
            return subject.employeeId().equals(actingEmployee);
        }
        List<OrgUnitId> own = new ArrayList<>();
        snapshot.findStaff(actingEmployee).ifPresent(staff -> {
            own.add(staff.assignment().orgUnitId());
            own.addAll(snapshot.findAncestors(staff.assignment().orgUnitId()));
        });
        return own.contains(subject.orgUnitId());
    }
}
