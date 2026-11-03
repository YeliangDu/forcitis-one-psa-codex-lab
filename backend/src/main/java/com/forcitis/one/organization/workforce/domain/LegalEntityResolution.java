package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The employee's own attribution wins; otherwise the nearest department attribution up the chain as the chain was that day. */
public final class LegalEntityResolution {
    private LegalEntityResolution() {
    }

    public static Optional<LegalEntityId> resolve(LegalEntityResolutionSources sources, EmployeeId employee, LocalDate date) {
        return resolve(sources, employee, date, null, null);
    }

    /** As {@link #resolve(LegalEntityResolutionSources, EmployeeId, LocalDate)} but as if the employee's own attribution and department were the ones given (a command not yet written); null keeps what is stored. */
    public static Optional<LegalEntityId> resolve(LegalEntityResolutionSources sources, EmployeeId employee, LocalDate date, @Nullable LegalEntityId ownOverride,
                                                  @Nullable OrgUnitId unitOverride) {
        Optional<LegalEntityId> own = ownOverride != null ? Optional.of(ownOverride) : sources.employeeAttribution(employee, date);
        if (own.isPresent()) {
            return own;
        }
        Optional<OrgUnitId> unit = unitOverride != null ? Optional.of(unitOverride) : sources.assignedUnit(employee, date);
        Set<OrgUnitId> visited = new HashSet<>();
        while (unit.isPresent() && visited.add(unit.get())) {
            Optional<LegalEntityId> attributed = sources.orgUnitAttribution(unit.get(), date);
            if (attributed.isPresent()) {
                return attributed;
            }
            unit = sources.parentUnit(unit.get(), date);
        }
        return Optional.empty();
    }
}
