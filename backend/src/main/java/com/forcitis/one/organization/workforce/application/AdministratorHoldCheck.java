package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.SeparationHoldCheck;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * A separation is held when the leaver is an available super_admin or security auditor holder (ADR-0028 decision 1 as kept by ADR-0055 decision 3
 * items 3 and 4; lead ruling on #873). The decision is made under the separation lock the caller already holds: it reads the held set, asks the
 * review with the leaver and that set, and holds when the leaver is among the available administrators. Anyone already held, or released and waiting for the date, is not counted
 * as available for the remainder (ADR-0068 item 2.2), so two concurrent separations of the last administrators both end held.
 */
@Component
class AdministratorHoldCheck implements SeparationHoldCheck {
    private final SeparationReview review;
    private final PersonnelCommandRepository commands;

    AdministratorHoldCheck(SeparationReview review, PersonnelCommandRepository commands) {
        this.review = review;
        this.commands = commands;
    }

    @Override
    public boolean holds(PersonnelCommand separation) {
        Set<EmployeeId> leaving = commands.heldOrReleasedEmployees();
        return !review.review(List.of(separation.employeeId()), leaving, null).separatedAdministrators().isEmpty();
    }
}
