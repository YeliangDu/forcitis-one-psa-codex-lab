package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class EmployeeLegalEntityService implements EmployeeLegalEntities {
    private final LegalEntityResolutionSources sources;

    EmployeeLegalEntityService(LegalEntityResolutionSources sources) {
        this.sources = sources;
    }

    @Override
    @Transactional(readOnly = true)
    public LegalEntityId on(EmployeeId employee, LocalDate date) {
        return find(employee, date).orElseThrow(() -> new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED,
                ErrorKind.UNPROCESSABLE, "no legal entity applies to employee " + employee.value() + " on " + date));
    }

    private Optional<LegalEntityId> find(EmployeeId employee, LocalDate date) {
        return LegalEntityResolution.resolve(sources, employee, date);
    }
}
