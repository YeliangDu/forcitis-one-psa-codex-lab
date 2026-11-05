package com.forcitis.one.organization.payroll.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.payroll.api.PayrollError;
import com.forcitis.one.organization.payroll.api.PayrollRuleVersionId;
import com.forcitis.one.organization.payroll.domain.PayrollRuleVersion;
import com.forcitis.one.organization.payroll.domain.PayrollRuleVersionRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PRC-180: makes a draft rule version effective and, in the same transaction, closes the version it follows at the day the new one starts.
 * The predecessor is saved first because the database refuses two versions of one category and jurisdiction that share a day.
 */
@Service
public class PublishPayrollRuleVersion {
    private final PayrollRuleVersionRepository versions;
    private final AuditLog audit;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final Clock clock;

    PublishPayrollRuleVersion(PayrollRuleVersionRepository versions, AuditLog audit, DomainEvents events, CurrentUser currentUser, Clock clock) {
        this.versions = versions;
        this.audit = audit;
        this.events = events;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public void execute(PayrollRuleVersionId id) {
        PayrollRuleVersion version = versions.findForUpdate(id.value()).orElseThrow(() -> new DomainException(PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_NOT_FOUND,
                ErrorKind.NOT_FOUND, "no payroll rule version " + id));
        UserId approver = currentUser.userId();
        version.publish(approver, clock.instant(), versions.rowsOf(id.value()));
        versions.findOpenEffective(version.ruleKind(), version.jurisdictionKey()).ifPresent(predecessor -> {
            predecessor.supersede(version.effectiveFromDate());
            versions.save(predecessor);
        });
        audit.record(new AuditEntry("organization.payroll.rule-version.published", approver, null, AuditSubject.without(AuditSubjectKind.OPERATION),
                "payrollRuleVersionId=" + id + " ruleKind=" + version.ruleKind() + " jurisdictionKey=" + version.jurisdictionKey() + " effectiveFromDate="
                        + version.effectiveFromDate()));
        versions.save(version);
        events.publish(version);
    }
}
