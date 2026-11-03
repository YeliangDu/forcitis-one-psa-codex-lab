package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.EmployeeVersionQueries;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appoints or clears the finance head of a legal entity, effective today (the countersign rule reads today's version, #895); never back- or future-dated. The head must
 * be employed today and is never the caller. Asking for the head the entity already has saves and audits nothing.
 */
@Service
public class ChangeFinanceHead {
    private final LegalEntityRepository legalEntities;
    private final EmployeeVersionQueries employees;
    private final CurrentUser currentUser;
    private final AuditLog audit;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    ChangeFinanceHead(LegalEntityRepository legalEntities, EmployeeVersionQueries employees, CurrentUser currentUser, AuditLog audit, DomainEvents events, Clock clock,
                      BusinessZone zone) {
        this.legalEntities = legalEntities;
        this.employees = employees;
        this.currentUser = currentUser;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public LegalEntityView execute(LegalEntityId id, ExpectedVersion expected, @Nullable EmployeeId head) {
        LegalEntity legalEntity = legalEntities.findForUpdate(id).orElseThrow(() -> new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND,
                ErrorKind.NOT_FOUND, "no legal entity " + id.value()));
        expected.check(legalEntity.version());
        LocalDate today = zone.today(clock);
        if (head != null) {
            requireAppointable(head, today);
        }
        if (!legalEntity.changeFinanceHead(head)) {
            return LegalEntityView.of(legalEntity);
        }
        legalEntities.save(legalEntity);
        events.publish(legalEntity);
        audit.record(new AuditEntry("organization.workforce.legal-entity.finance-head.changed", currentUser.userId(), null, AuditSubject.of(AuditSubjectKind.LEGAL_ENTITY, id.value()),
                "legal entity " + id.value() + " finance head " + (head == null ? "none" : head.value()) + " effective " + today));
        return LegalEntityView.of(legalEntity);
    }

    private void requireAppointable(EmployeeId head, LocalDate today) {
        if (head.equals(currentUser.employeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_FINANCE_HEAD_SELF_APPOINTMENT_FORBIDDEN, ErrorKind.FORBIDDEN,
                    "nobody appoints themselves as finance head");
        }
        if (employees.employmentOn(head, today).filter(status -> status == EmploymentStatus.EMPLOYED).isEmpty()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_FINANCE_HEAD_EMPLOYEE_INVALID, ErrorKind.INVALID,
                    "the finance head must be an employee in employment today");
        }
    }
}
