package com.forcitis.one.organization.payroll.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.payroll.api.EffectivePayrollRuleVersion;
import com.forcitis.one.organization.payroll.api.JurisdictionKey;
import com.forcitis.one.organization.payroll.api.PayrollError;
import com.forcitis.one.organization.payroll.api.PayrollRuleKind;
import com.forcitis.one.organization.payroll.api.PayrollRuleVersions;
import com.forcitis.one.organization.payroll.domain.PayrollRuleVersionRepository;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
class PayrollRuleVersionsService implements PayrollRuleVersions {
    private final PayrollRuleVersionRepository versions;

    PayrollRuleVersionsService(PayrollRuleVersionRepository versions) {
        this.versions = versions;
    }

    @Override
    public EffectivePayrollRuleVersion effectiveOn(PayrollRuleKind ruleKind, JurisdictionKey jurisdictionKey, LocalDate date) {
        Objects.requireNonNull(ruleKind, "ruleKind");
        Objects.requireNonNull(jurisdictionKey, "jurisdictionKey");
        Objects.requireNonNull(date, "date");
        return versions.effectiveOn(ruleKind, jurisdictionKey, date).orElseThrow(() -> new DomainException(
                PayrollError.ORGANIZATION_PAYROLL_NO_EFFECTIVE_RULE_VERSION, ErrorKind.NOT_FOUND,
                "no " + ruleKind + " rule version of " + jurisdictionKey + " is effective on " + date));
    }
}
