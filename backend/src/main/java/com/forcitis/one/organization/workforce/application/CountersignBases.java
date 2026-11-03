package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.domain.CommandEffects;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Whether a command changes the employee's cost legal entity (ADR-0073 item 3.4, DD amendment D of #895): the entity on the effective date before and after the command,
 * each the employee's own attribution first, else the first attribution up the chain from the department of the assignment. Only a transfer, a correction and a change
 * of legal entity can change it. An employee who is not employed on the day has none, so such a command never needs a countersign.
 */
@Component
class CountersignBases {
    private final LegalEntityResolutionSources sources;

    CountersignBases(LegalEntityResolutionSources sources) {
        this.sources = sources;
    }

    record Basis(LegalEntityId previous, LegalEntityId next) {
    }

    Optional<Basis> of(PersonnelCommand command, PersonnelHistory history) {
        switch (command.kind()) {
            case TRANSFER, CORRECTION, CHANGE_LEGAL_ENTITY -> {
            }
            default -> {
                return Optional.empty();
            }
        }
        if (command.isOnConcurrentPost()) {
            return Optional.empty();
        }
        LocalDate day = command.effectiveDate();
        boolean employed = history.currentEmployeeRows().stream().anyMatch(row -> row.covers(day) && "EMPLOYED".equals(row.values().get(CommandEffects.EMPLOYMENT)));
        if (!employed) {
            return Optional.empty();
        }
        Optional<LegalEntityId> before = LegalEntityResolution.resolve(sources, command.employeeId(), day);
        Optional<LegalEntityId> after = LegalEntityResolution.resolve(sources, command.employeeId(), day, command.targetLegalEntityId(), command.targetOrgUnitId());
        if (before.isEmpty() || after.isEmpty() || before.get().equals(after.get())) {
            return Optional.empty();
        }
        return Optional.of(new Basis(before.get(), after.get()));
    }
}
