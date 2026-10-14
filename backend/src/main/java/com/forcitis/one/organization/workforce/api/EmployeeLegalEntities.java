package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import java.time.LocalDate;

/**
 * The legal entity an employee belongs to on a day: the employee's own attribution first, else the nearest attribution up the
 * employee's department chain as it was that day. Nothing is guessed.
 */
public interface EmployeeLegalEntities {

    /** @throws com.forcitis.one.kernel.DomainException ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED (422) when none applies */
    LegalEntityId on(UserId employee, LocalDate date);
}
