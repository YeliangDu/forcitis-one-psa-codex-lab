package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.EmployeeChecks;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.organization.workforce.domain.SourceTerms;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import com.forcitis.one.organization.workforce.domain.SyncDiff;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Doubtful or out-of-scope people answer "unknown": login fails closed until a personnel command or a later login lookup settles them. */
@Component
@SystemActorName("organization.workforce.login-registration")
class LoginMissRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(LoginMissRegistration.class);
    private static final String ACTOR = LoginMissRegistration.class.getAnnotation(SystemActorName.class).value();

    private final OrgSnapshotRepository snapshots;
    private final OrgSnapshotQueries lookups;
    private final PersistRegisteredEmployee persist;

    LoginMissRegistration(OrgSnapshotRepository snapshots, OrgSnapshotQueries lookups, PersistRegisteredEmployee persist) {
        this.snapshots = snapshots;
        this.lookups = lookups;
        this.persist = persist;
    }

    Optional<StaffEmployee> register(List<SourcePerson> found, String keyKind) {
        Optional<SourcePerson> chosen = choose(found, keyKind);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        SourcePerson person = chosen.get();
        var orgUnits = snapshots.findOrgUnitIds();
        var positions = snapshots.findPositionIds();
        Optional<SyncDiff> isolation = EmployeeChecks.isolation(person, orgUnits::contains, positions::contains);
        if (isolation.isPresent()) {
            LOG.warn("organization.workforce login-miss person {} not registered: {}", person.personId().value(),
                    isolation.get().detail());
            return Optional.empty();
        }
        EmployeeId employeeId = SourceTerms.person(person.personId());
        StaffEmployee staff = new StaffEmployee(EmployeeChecks.toEmployee(person, true),
                new Assignment(employeeId, SourceTerms.unit(person.unitId()), SourceTerms.position(person.positionId()), validSupervisor(person)));
        // The login request has no signed-in user yet, so the registration acts as SYSTEM (P2-F1).
        UUID correlationId = WorkContext.correlationId().orElseThrow(() -> new IllegalStateException("login registration needs a correlation id"));
        try (WorkContext.Scope scope = WorkContext.enter(correlationId, WorkContext.causationId().orElse(null), ACTOR)) {
            persist.execute(staff);
        }
        return Optional.of(staff);
    }

    /** Several rows for one key are ambiguous unless exactly one of them is employed. */
    private static Optional<SourcePerson> choose(List<SourcePerson> found, String keyKind) {
        if (found.isEmpty()) {
            return Optional.empty();
        }
        List<SourcePerson> employed = found.stream().filter(m -> EmployeeChecks.employmentOf(m) == EmploymentStatus.EMPLOYED).toList();
        if (employed.size() == 1) {
            return Optional.of(employed.getFirst());
        }
        if (employed.isEmpty() && found.size() == 1) {
            return Optional.of(found.getFirst());
        }
        LOG.warn("organization.workforce login-miss lookup is ambiguous ({} rows); the {} is not trusted", found.size(), keyKind);
        return Optional.empty();
    }

    private EmployeeId validSupervisor(SourcePerson person) {
        EmployeeId leader = SourceTerms.person(person.supervisorId());
        if (leader == null || leader.equals(SourceTerms.person(person.personId()))) {
            return null;
        }
        return lookups.findStaff(leader)
                .filter(s -> s.employee().employmentStatus() == EmploymentStatus.EMPLOYED)
                .map(StaffEmployee::employeeId)
                .orElse(null);
    }
}
