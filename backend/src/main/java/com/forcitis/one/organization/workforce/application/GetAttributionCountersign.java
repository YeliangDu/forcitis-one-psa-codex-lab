package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.CountersignCommandKind;
import com.forcitis.one.organization.workforce.api.CountersignCommandStatus;
import com.forcitis.one.organization.workforce.api.CountersignDecisionKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.application.CountersignReadViews.Command;
import com.forcitis.one.organization.workforce.application.CountersignReadViews.Person;
import com.forcitis.one.organization.workforce.application.CountersignReadViews.Request;
import com.forcitis.one.organization.workforce.application.CountersignReadViews.Row;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.CountersignReadQueries;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One request with the command it belongs to and the other requests of that command; only its decider reads it (#910 amendment B, ruling comment 6588705997). */
@Service
public class GetAttributionCountersign {
    private final CountersignReadAccess access;
    private final CountersignReadQueries names;
    private final AccountEmployees accountEmployees;

    GetAttributionCountersign(CountersignReadAccess access, CountersignReadQueries names, AccountEmployees accountEmployees) {
        this.access = access;
        this.names = names;
        this.accountEmployees = accountEmployees;
    }

    @Transactional(readOnly = true)
    public Row execute(AttributionCountersignId id) {
        CountersignReadAccess.Readable readable = access.ofRequest(id);
        OrgStructureCommand org = readable.orgCommand();
        PersonnelCommand personnel = readable.personnelCommand();
        return new Row(org != null ? orgCommand(org) : personnelCommand(Objects.requireNonNull(personnel)), requests(readable.requests()));
    }

    private Command orgCommand(OrgStructureCommand command) {
        OrgUnitId unit = command.targetOrgUnitId();
        LegalEntityId target = command.targetLegalEntityId();
        Map<LegalEntityId, String> entities = target == null ? Map.of() : names.legalEntityNames(List.of(target));
        return new Command(command.id().value(), null, CountersignCommandKind.valueOf(command.kind().name()), command.effectiveDate(), command.status().asCountersignCommandStatus(), command.withdrawalReason(), null, null, null,
                target == null ? null : target.value(), target == null ? null : entities.get(target), unit == null ? null : unit.value(),
                unit == null ? null : names.orgUnitName(unit).orElse(null));
    }

    private Command personnelCommand(PersonnelCommand command) {
        LegalEntityId previous = command.countersignPreviousLegalEntityId();
        LegalEntityId next = command.countersignNewLegalEntityId();
        List<LegalEntityId> shown = new ArrayList<>();
        if (previous != null) {
            shown.add(previous);
        }
        if (next != null) {
            shown.add(next);
        }
        Map<LegalEntityId, String> entities = names.legalEntityNames(shown);
        CountersignReadQueries.Person person = names.employees(List.of(command.employeeId())).get(command.employeeId());
        return new Command(null, command.id().value(), CountersignCommandKind.valueOf(command.kind().name()), command.effectiveDate(), command.status().asCountersignCommandStatus(), command.withdrawalReason(),
                new Person(command.employeeId().value(), person == null ? "" : person.displayName(), person == null ? "" : person.employeeCode()),
                previous == null ? null : previous.value(), previous == null ? null : entities.get(previous), next == null ? null : next.value(), next == null ? null : entities.get(next),
                null, null);
    }

    private List<Request> requests(List<AttributionCountersign> set) {
        Map<LegalEntityId, String> entities = names.legalEntityNames(set.stream().map(AttributionCountersign::legalEntityId).distinct().toList());
        List<Request> shown = new ArrayList<>();
        for (AttributionCountersign r : set) {
            shown.add(new Request(r.id().value(), r.requestKind(), r.requestSequenceNumber(), r.legalEntityId().value(), entities.get(r.legalEntityId()), r.requestedAt(),
                    displayNameOf(r.requestedBy()), r.dueOn(), r.escalationLevel(), r.status() == CountersignStatus.OPEN ? null : CountersignDecisionKind.valueOf(r.status().name()), r.decidedAt(),
                    r.decidedBy() == null ? null : displayNameOf(r.decidedBy()), r.decisionReason()));
        }
        return shown;
    }

    private @Nullable String displayNameOf(UserId account) {
        Collection<EmployeeId> employee = accountEmployees.employeeOf(account).map(List::of).orElse(List.of());
        return employee.isEmpty() ? null : Optional.ofNullable(names.employees(employee).get(employee.iterator().next())).map(CountersignReadQueries.Person::displayName).orElse(null);
    }
}
