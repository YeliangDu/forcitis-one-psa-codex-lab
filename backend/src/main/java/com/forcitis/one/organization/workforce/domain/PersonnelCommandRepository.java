package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.kernel.event.ActorType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Storage of personnel commands, their version rows, their transitions and the hold a held separation carries. Every method joins the caller's transaction. */
public interface PersonnelCommandRepository {

    /** Locks the employee row for the rest of the transaction; false when there is no such employee. */
    boolean lockEmployee(EmployeeId employee);

    /**
     * Takes the transaction-level advisory lock every part of the administrator-separation decision takes first: entering a separation, releasing a
     * hold, withdrawing a held command (ADR-0068 item 2.2). It is taken before any employee row, so all of them lock in one order.
     */
    void lockSeparationDecisions();

    /**
     * The one count of who has already gone when the remaining available administrators are counted (user decision 2026-10-30 「统一都算已走」, ADR-0068
     * items 2.2 and 2.3): those whose separation is held, those whose separation was released and still waits as pending, whatever the date, and those
     * whose unpaid-leave start was entered and has not started. They are no available administrators and not retained (read under the lock).
     */
    Set<EmployeeId> excludedEmployees();

    /** The employees whose separation is held at this moment (read under the lock). */
    Set<EmployeeId> heldEmployees();

    /**
     * The pending commands whose effective date is not after the day, oldest first. A separation counts only with a released hold: an unreleased one is
     * not judged again on its day yet (card #967), so the job leaves it alone. A held separation is not pending until it is released.
     */
    List<PersonnelCommandId> pendingDue(LocalDate day);

    boolean orgUnitExists(OrgUnitId orgUnit);

    boolean positionExists(PositionId position);

    boolean employeeExists(EmployeeId employee);

    /** The employee's event sequence, which a command that takes effect continues. */
    long eventSequence(EmployeeId employee);

    PersonnelHistory load(EmployeeId employee);

    Optional<PersonnelCommand> find(PersonnelCommandId id);

    void insert(PersonnelCommand command);

    /** Writes the status, the application time and the withdrawal columns the aggregate holds, if the stored version is still the one it was loaded with. */
    void update(PersonnelCommand command, Instant at, long loadedVersion);

    /**
     * Supersedes the rows of the plan and appends its rows, in that order; closed_at of an appended row that has an end is the given instant.
     * The rows are superseded by {@code command} (the one that caused the plan).
     */
    void insertVersions(EmployeeId employee, VersionTable table, VersionPlan plan, PersonnelCommandId command, Instant at);

    Optional<SeparationHold> holdOf(PersonnelCommandId command);

    Optional<SeparationHold> findHold(SeparationHoldId id);

    void insertHold(SeparationHold hold, Instant at);

    /** Writes the status and the release columns the hold holds, if the stored version is still the one it was loaded with. */
    void updateHold(SeparationHold hold, Instant at, long loadedVersion);

    void insertTransition(String aggregateType, UUID aggregateId, long sequence, @Nullable String from, String to, ActorType actorType, UUID actorId, LocalDate day, Instant at,
                          PersonnelCommandId command, UUID correlation);

    void saveEventSequence(EmployeeId employee, long sequence);
}
