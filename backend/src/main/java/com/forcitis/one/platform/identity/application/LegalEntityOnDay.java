package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The legal entity of an employee on a day, asked in a transaction of its own: a refusal ("unresolved") thrown through the provider's transactional
 * proxy marks the caller's transaction rollback-only even when the caller catches it, and the caller must go on without the answer.
 */
@Component
class LegalEntityOnDay {

    private final EmployeeLegalEntities legalEntities;

    LegalEntityOnDay(EmployeeLegalEntities legalEntities) {
        this.legalEntities = legalEntities;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    LegalEntityId of(EmployeeId employee, LocalDate day) {
        return legalEntities.on(employee, day);
    }
}
