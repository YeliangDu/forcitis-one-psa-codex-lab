package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
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
     * The pending commands whose effective date is not after the day, oldest first. A separation that was never held counts too: it is judged
     * on its day (#967). A held separation is not pending until it is released.
     */
    List<PersonnelCommandId> pendingDue(LocalDate day);

    boolean orgUnitExists(OrgUnitId orgUnit);

    /**
     * Takes the root row of the org unit in share mode for the rest of the transaction, before the employee row (lock order: org unit root, then employee root).
     * It excludes a merge of the unit from entering, starting or finishing while this personnel command is checked and written, so a person cannot be placed in a unit
     * that a concurrent merge is revoking.
     */
    void lockOrgUnitForEntry(OrgUnitId orgUnit);

    /**
     * Whether a placement in the unit on the day would be left in a unit a merge revokes: the merge is in progress (its people are counted, whatever the day), or it is
     * pending with an effective date on or before the day.
     */
    boolean hasOpenMergeFor(OrgUnitId orgUnit, LocalDate day);

    boolean positionExists(PositionId position);

    boolean employeeExists(EmployeeId employee);

    /** Whether the legal entity exists and is active: the only target a command may name (ADR-0073 item 3.2). */
    boolean legalEntityIsActive(LegalEntityId legalEntity);

    /** The root of a hired person. The version rows follow from the command. */
    void insertEmployee(NewEmployee employee, Instant at);

    boolean employeeCodeTaken(String employeeCode);

    /** Whether any employee other than {@code except} (none: anyone) holds the WeCom user id, separated ones included (W2 section 3.1 N-05). */
    boolean wecomUserIdHeld(WecomUserId wecomUserId, @Nullable EmployeeId except);

    Optional<PersonalProfile> profileOf(EmployeeId employee);

    /** Writes the profile columns of the root; the code is never among them. */
    void updateProfile(EmployeeId employee, PersonalProfile profile, Instant at);

    /** The employee's event sequence, which a command that takes effect continues. */
    long eventSequence(EmployeeId employee);

    PersonnelHistory load(EmployeeId employee);

    /** Writes a declared employment relationship (ADR-0086) before the command that names it; the employee row is locked. */
    void insertRelationship(EmploymentRelationshipId id, EmployeeId employee, LegalEntityId employer, @Nullable EmploymentRelationshipId predecessor, Instant at);

    /** Whether the employee already has a relationship with the employer (the pair is unique, appendix A). */
    boolean relationshipExists(EmployeeId employee, LegalEntityId employer);

    /** The employee's one primary post root, made when the employee has none yet (the employee row is locked). */
    AssignmentRoot primaryRoot(EmployeeId employee, Instant at);

    /** Makes the root of a concurrent post, in the transaction of the command that starts it. */
    void insertConcurrentRoot(EmployeeId employee, AssignmentId root, Instant at);

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
