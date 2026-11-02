package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Storage of the one-time initial migration of snapshot people into the version tables. Every method joins the caller's transaction. */
public interface InitialVersionMigrationRepository {

    /** Whether a sync run is still running: the snapshot would still change under the migration. */
    boolean syncRunning();

    /** The employees who have no employee version of any channel or date, their rows locked for the rest of the transaction. */
    List<InitialVersionPlan.SnapshotPerson> lockEmployeesWithoutVersions();

    /** Employees who already hold an employee version and are left as they are. */
    long countEmployeesWithVersions();

    /** Employees whose current assignment version differs from the assignment snapshot; counted for HR, never overwritten. */
    long countAssignmentConflicts(LocalDate today);

    void insertEmployeeVersion(EmployeeId employee, InitialVersionPlan.Row row, Instant at);

    /** Writes the person's assignment snapshot as an open MIGRATION assignment version from the given day. */
    void insertAssignmentVersionFromSnapshot(EmployeeId employee, LocalDate from, Instant at);
}
