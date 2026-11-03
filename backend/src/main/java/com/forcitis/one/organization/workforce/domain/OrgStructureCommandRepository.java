package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.ActorType;
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
     * Locks the root row of the org unit for the rest of the transaction; false when there is no such unit. Everything that changes what the unit's people
     * are, a merge's start and each person it moves, takes this lock before any employee row (lock order: org unit root, then employee root).
     */
    boolean lockOrgUnit(OrgUnitId orgUnit);

    boolean orgUnitExists(OrgUnitId orgUnit);

    boolean positionExists(PositionId position);

    boolean employeeExists(EmployeeId employee);

    boolean legalEntityExists(LegalEntityId legalEntity);

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

    /** The snapshot row of a new org unit, in the command's transaction; its versions follow. */
    void insertOrgUnit(OrgStructureCommand command, Instant at);

    void insertPosition(OrgStructureCommand command, Instant at);

    /** Writes the initial legal entity of a new unit: the attribution root and its first open period, from the command's day. */
    void insertInitialAttribution(OrgUnitId orgUnit, LegalEntityId legalEntity, LocalDate from, OrgStructureCommandId command, AuditEntryId audit, Instant at);

    /** Takes the period the command wrote out again (a withdrawal): superseded, not deleted. */
    void updateInitialAttributionVoided(OrgUnitId orgUnit, OrgStructureCommandId command, Instant at);

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

    /** Whether somebody is employed and holds the position on some day from the given one on. */
    boolean hasHolderFrom(PositionId position, LocalDate day);
}
