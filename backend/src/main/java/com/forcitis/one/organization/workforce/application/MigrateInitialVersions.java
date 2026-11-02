package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.InitialVersionMigrationRepository;
import com.forcitis.one.organization.workforce.domain.InitialVersionPlan;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one-time initial migration of the sync snapshot into the version tables (#960, one-design data design): every employee who has no employee
 * version yet gets the current state as MIGRATION versions, and a person with an assignment but no assignment version gets one. People who hold any
 * version are left as they are, which makes a rerun write nothing. One transaction, each person's root row locked first; no event, no notification.
 * The audit entry carries counts only. The employment phase of employed people is the user's fallback answer 「一律记正式」.
 */
@Component
class MigrateInitialVersions {

    /** The scheduler runs every five minutes; a command in another process cannot see it, so a stopped sync is a sync that has left no run for three cycles. */
    private static final Duration SYNC_QUIET_PERIOD = Duration.ofMinutes(15);

    private final InitialVersionMigrationRepository migration;
    private final AuditLog audit;
    private final Clock clock;
    private final BusinessZone zone;

    MigrateInitialVersions(InitialVersionMigrationRepository migration, AuditLog audit, Clock clock, BusinessZone zone) {
        this.migration = migration;
        this.audit = audit;
        this.clock = clock;
        this.zone = zone;
    }

    /** @throws DomainException when the sync is not stopped, since the snapshot would go on changing */
    @Transactional
    public String execute(WorkforceOperationAttestation attestation) {
        Instant now = clock.instant();
        if (migration.syncRunRecent(now.minus(SYNC_QUIET_PERIOD))) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_INITIAL_VERSION_MIGRATION_REFUSED, ErrorKind.UNPROCESSABLE,
                    "the sync is not stopped: a run is running or started within " + SYNC_QUIET_PERIOD + ", so the snapshot would change under the migration");
        }
        LocalDate today = zone.dateOf(now);
        long skipped = migration.countEmployeesWithVersions();
        int employedPeople = 0;
        int separatedPeople = 0;
        int assignmentsBackfilled = 0;
        for (InitialVersionPlan.SnapshotPerson person : migration.lockEmployeesWithoutVersions()) {
            EmployeeId employee = new EmployeeId(person.id());
            for (InitialVersionPlan.Row row : InitialVersionPlan.rowsFor(person, today)) {
                migration.insertEmployeeVersion(employee, row, now);
            }
            if (person.missingAssignmentVersion()) {
                migration.insertAssignmentVersionFromSnapshot(employee, InitialVersionPlan.firstDay(person, today), now);
                assignmentsBackfilled++;
            }
            if (person.employed()) {
                employedPeople++;
            } else {
                separatedPeople++;
            }
        }
        long differing = migration.countAssignmentConflicts(today);
        String summary = "employedMigrated=" + employedPeople + " separatedMigrated=" + separatedPeople + " assignmentVersionsBackfilled="
                + assignmentsBackfilled + " skippedWithVersions=" + skipped + " assignmentsDifferingFromSnapshot=" + differing;
        audit.record(new AuditEntry("organization.workforce.initial-versions.migrated", null, null, AuditSubject.without(AuditSubjectKind.OPERATION),
                "operatorId=" + attestation.operator() + " witnessId=" + attestation.witness() + " " + summary));
        return summary;
    }
}
