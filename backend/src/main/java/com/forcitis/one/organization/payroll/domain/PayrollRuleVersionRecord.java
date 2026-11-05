package com.forcitis.one.organization.payroll.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.payroll.api.JurisdictionKey;
import com.forcitis.one.organization.payroll.api.PayrollRuleKind;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A stored version as the repository read it. */
public record PayrollRuleVersionRecord(java.util.UUID id, PayrollRuleKind ruleKind, JurisdictionKey jurisdictionKey, LocalDate effectiveFromDate,
        @Nullable LocalDate effectiveToDate, PayrollRuleVersionStatus status, UserId draftedBy, @Nullable UserId approvedBy,
        @Nullable Instant publishedAt, long version) {
}
