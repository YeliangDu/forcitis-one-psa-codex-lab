package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Storage of the countersign requests of personnel commands and of org-structure commands. Every method joins the caller's transaction. */
public interface AttributionCountersignRepository {

    Optional<AttributionCountersign> find(AttributionCountersignId id);

    /** Every request of the command, oldest first, decided ones included. */
    List<AttributionCountersign> ofCommand(PersonnelCommandId command);

    /** The requests of the personnel command, locked for the rest of the transaction and read after the lock, so a decision or escalation that committed meanwhile is seen. */
    List<AttributionCountersign> lockOfCommand(PersonnelCommandId command);

    /** As {@link #ofCommand} for an org-structure command. */
    List<AttributionCountersign> ofOrgCommand(OrgStructureCommandId command);

    /** The requests of the org-structure command, locked for the rest of the transaction and read after the lock, so a decision or escalation that committed meanwhile is seen. */
    List<AttributionCountersign> lockOfOrgCommand(OrgStructureCommandId command);

    void insert(AttributionCountersign request);

    /** Records the request's own state change; {@code parent} is the personnel or org-structure command that needs the countersign (the transition table keeps it as a plain id). */
    void insertTransition(UUID request, long sequence, @Nullable String from, String to, ActorType actorType, UUID actorId, LocalDate day, Instant at, UUID parent,
                          UUID correlation);

    /** Writes the decision of an open request with the audit entry that recorded it, if the stored version is still the one it was loaded with. */
    void saveDecision(AttributionCountersign request, AuditEntryId decisionAudit, Instant at, long loadedVersion);

    /** The open requests whose due date lies before the day and that are below the last escalation level: the only ones the deadline job may still raise. */
    List<AttributionCountersignId> overdueBelowLastLevel(LocalDate day);

    /** Writes the raised escalation level of an open request, if the stored version is still the one it was loaded with. */
    void saveEscalation(AttributionCountersign request, Instant at, long loadedVersion);

    /** The finance head of the legal entity in the version of the entity that holds on the day; empty when the version names none. */
    Optional<EmployeeId> financeHeadOn(LegalEntityId legalEntity, LocalDate day);

    /** Whether the employee is the finance head of the legal entity in the version of the entity that holds on the day. */
    boolean isFinanceHead(LegalEntityId legalEntity, EmployeeId employee, LocalDate day);
}
