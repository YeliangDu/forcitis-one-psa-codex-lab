package com.forcitis.one.organization.payroll.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.organization.payroll.api.JurisdictionKey;
import com.forcitis.one.organization.payroll.api.PayrollRuleKind;
import com.forcitis.one.organization.payroll.api.PayrollRuleVersionId;
import java.time.LocalDate;

/** Analytics only: a payroll rule version became effective from the given date, which is also the envelope's businessDate. */
@NotAudited(reason = "the publication is audited by its own record in the same transaction (organization.payroll.rule-version.published)")
public record PayrollRuleVersionBecameEffective(EventEnvelope envelope, PayrollRuleVersionId payrollRuleVersionId, PayrollRuleKind ruleKind,
        JurisdictionKey jurisdictionKey, LocalDate effectiveFromDate) implements DomainEvent {
}
