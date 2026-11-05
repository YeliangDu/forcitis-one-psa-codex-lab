package com.forcitis.one.organization.payroll.domain;

import com.forcitis.one.organization.payroll.api.EffectivePayrollRuleVersion;
import com.forcitis.one.organization.payroll.api.JurisdictionKey;
import com.forcitis.one.organization.payroll.api.PayrollRuleKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollRuleVersionRepository {

    Optional<PayrollRuleVersion> find(UUID id);

    /** The version of the category and jurisdiction that is EFFECTIVE and has no end yet: the one a newly published version closes. */
    Optional<PayrollRuleVersion> findOpenEffective(PayrollRuleKind ruleKind, JurisdictionKey jurisdictionKey);

    List<PayrollRuleRowBound> rowsOf(UUID versionId);

    /** Writes the status, the end date and the approval of a version that was read; refuses a version changed since it was read. */
    void save(PayrollRuleVersion version);

    Optional<EffectivePayrollRuleVersion> effectiveOn(PayrollRuleKind ruleKind, JurisdictionKey jurisdictionKey, LocalDate date);
}
