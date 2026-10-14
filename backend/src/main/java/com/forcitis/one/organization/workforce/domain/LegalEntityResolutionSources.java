package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.time.LocalDate;
import java.util.Optional;

/** What the organisation and the attributions looked like on a day. */
public interface LegalEntityResolutionSources {
    Optional<LegalEntityId> employeeAttribution(UserId employee, LocalDate date);

    Optional<OrgUnitId> assignedUnit(UserId employee, LocalDate date);

    Optional<LegalEntityId> orgUnitAttribution(OrgUnitId orgUnit, LocalDate date);

    Optional<OrgUnitId> parentUnit(OrgUnitId orgUnit, LocalDate date);
}
