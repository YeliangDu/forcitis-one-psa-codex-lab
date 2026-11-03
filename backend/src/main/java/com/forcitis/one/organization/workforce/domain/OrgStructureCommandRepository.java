package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.api.BlockingArrival;
import com.forcitis.one.organization.workforce.api.HeldSeparation;
import com.forcitis.one.organization.workforce.api.HeldSeparation;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Storage of org-structure commands, the version rows they write, their transitions and the people a merge moves. Every method joins the caller's transaction. */
public interface OrgStructureCommandRepository {

    /**
     * Locks the root row of the org unit for the rest of the transaction (FOR NO KEY UPDATE: it excludes other such locks but not the key-share lock a foreign key check takes when a personnel command writes an assignment in the unit, so a personnel command, which locks the employee first, cannot deadlock with a merge); false when there is no such unit. Everything that changes what the unit's people
     * are, a merge's start and each person it moves, takes this lock before any employee row (lock order: org unit root, then employee root).
     */
    boolean lockOrgUnit(OrgUnitId orgUnit);

    /** Locks the employee root row for the rest of the transaction (after the org unit root); false when there is no such employee. */
    boolean lockEmployee(EmployeeId employee);

    /**
     * Takes the transaction-level advisory lock every write of the org tree takes first (entering and withdrawing a command, starting a merge): the tree rules
     * read rows of other units, so two commands that are fine alone but wrong together would otherwise both pass. Order: this lock, then the unit root, then the employee root.
     */
    void lockOrgTree();

    boolean orgUnitExists(OrgUnitId orgUnit);

    boolean positionExists(PositionId position);

    boolean employeeExists(EmployeeId employee);

    boolean legalEntityExists(LegalEntityId legalEntity);

    /** Whether the unit has an attribution period that starts on or after the day, or a closed one that ends after it: appending a period from that day would need the W2 void-and-append split, which is not built. */
    boolean hasAttributionAfter(OrgUnitId orgUnit, LocalDate day);

    /** Whether any position, current or not, ever had the code (codes are company-unique and never reused). */
    boolean positionCodeTaken(String code);

    Optional<OrgStructureCommand> find(OrgStructureCommandId id);

    void insert(OrgStructureCommand command);

    /** Writes the status, the progress, the application time and the withdrawal columns the aggregate holds, if the stored version is still the one it was loaded with. */
    void update(OrgStructureCommand command, Instant at, long loadedVersion);

    void insertTransition(OrgStructureCommandId command, long sequence, @Nullable String from, String to, ActorType actorType, UUID actorId, LocalDate day, Instant at,
                          UUID correlation);

    /** Whether the target already has an open command for the day (one open command per target and day). */
    boolean hasOpenCommand(@Nullable OrgUnitId orgUnit, @Nullable PositionId position, LocalDate day);

    /** Whether a merge of the unit is pending or in progress, whatever its day: nothing may be placed below a unit that is about to disappear. */
    boolean hasOpenMerge(OrgUnitId orgUnit);

    /** Whether a merge into the unit is pending or in progress with an effective date on or before the day: its people will arrive by then. */
    boolean hasOpenMergeInto(OrgUnitId orgUnit, LocalDate onOrBefore);

    /** Whether a merge of the unit away is in progress, or pending (or awaiting its countersign) with an effective date on or before the day: a unit that is about to disappear cannot receive a merge's people. */
    boolean hasMergeAwayOnOrBefore(OrgUnitId orgUnit, LocalDate day);

    /** Whether a merge into the unit is open (pending, awaiting its countersign or in progress) with an effective date on or after the day: its people arrive on the day or later, so the unit cannot be merged away by then. */
    boolean hasMergeIntoOnOrAfter(OrgUnitId orgUnit, LocalDate day);

    /** The merges that are pending or in progress, in the order their unit rows are locked in (merge day, merged unit, id). */
    List<OrgStructureCommandId> openMerges();

    /** The snapshot row of a new org unit, in the command's transaction; its versions follow. */
    void insertOrgUnit(OrgStructureCommand command, Instant at);

    void insertPosition(OrgStructureCommand command, Instant at);

    /** Writes the initial legal entity of a new unit: the attribution root and its first open period, from the command's day. */
    void insertInitialAttribution(OrgUnitId orgUnit, LegalEntityId legalEntity, LocalDate from, OrgStructureCommandId command, AuditEntryId audit, Instant at);

    /** From the day on the unit belongs to the legal entity: closes the open period at the day and appends one, both from the command; the unit's attribution root is created if the unit never had one. */
    void attributeUnit(OrgUnitId orgUnit, LegalEntityId legalEntity, LocalDate from, OrgStructureCommandId command, AuditEntryId audit, Instant at);

    /**
     * Takes back every period the command wrote (a withdrawal): superseded, not deleted; a period the command closed is superseded and appended again as it was, up to where the command's own period ended (open when no later command followed),
     * so the unit belongs to the entity it did before the command.
     */
    void voidAttribution(OrgUnitId orgUnit, OrgStructureCommandId command, AuditEntryId audit, Instant at);

    /** The countersign basis of the command, one row per employee whose cost legal entity it changes. Written once, at entry. */
    void insertBasis(OrgStructureCommandId command, List<OrgCountersignBasis> basis, Instant at);

    List<OrgCountersignBasis> basisOf(OrgStructureCommandId command);

    /** The org-structure commands that wait for the attribution countersign, whatever their day. */
    List<OrgStructureCommand> awaitingCountersign();


    OrgTimeline unitTimeline(OrgUnitId orgUnit);

    OrgTimeline positionTimeline(PositionId position);

    /** The current version rows of every org unit, for the tree rules. */
    Map<UUID, List<VersionRow>> currentUnitRows();

    /** Supersedes the rows of the plan and appends its rows, in that order; the rows are superseded by {@code command}. */
    void insertUnitVersions(OrgUnitId orgUnit, VersionPlan plan, OrgStructureCommandId command, Instant at);

    void insertPositionVersions(PositionId position, VersionPlan plan, OrgStructureCommandId command, Instant at);

    /** The people employed on the day whose assignment on that day is in the unit, in a stable order. */
    List<EmployeeId> employedIn(OrgUnitId orgUnit, LocalDate day);

    /** Whether somebody is employed and in the unit on some day from the given one on, whatever the entered commands will make of them. */
    boolean hasEmployedFrom(OrgUnitId orgUnit, LocalDate day);

    /**
     * The employed people who join the unit after the day (a transfer into it dated later, or a person who joined after a back-dated day), with the command that placed them.
     * A person who is in the unit just before such a row (a later position or manager change) is a resident, not an arrival.
     */
    List<BlockingArrival> arrivalsAfter(OrgUnitId orgUnit, LocalDate day);

    /** The people employed in the unit on the day whose separation is held, with the held command: a hold keeps them where they are. */
    List<HeldSeparation> heldSeparationsIn(OrgUnitId orgUnit, LocalDate day);

    /** The merges in progress whose merged unit holds the employee's primary post on the merge day, ordered by merge day, merged unit, then id (the order their unit rows are locked in). */
    List<OrgStructureCommandId> mergesInProgressOf(EmployeeId employee);

    /** Whether the merge already generated a transfer for the employee, whatever became of it. */
    boolean hasGeneratedTransfer(OrgStructureCommandId merge, EmployeeId employee);

    /** Whether somebody is employed and holds the position on some day from the given one on. */
    boolean hasHolderFrom(PositionId position, LocalDate day);
}
