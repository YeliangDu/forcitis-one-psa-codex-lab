package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Storage of the countersign requests of personnel commands. Every method joins the caller's transaction. */
public interface AttributionCountersignRepository {

    Optional<AttributionCountersign> find(AttributionCountersignId id);

    /** Every request of the command, oldest first, decided ones included. */
    List<AttributionCountersign> ofCommand(PersonnelCommandId command);

    void insert(AttributionCountersign request);

    /** Writes the decision of an open request with the audit entry that recorded it, if the stored version is still the one it was loaded with. */
    void saveDecision(AttributionCountersign request, AuditEntryId decisionAudit, Instant at, long loadedVersion);

    /** Whether the employee is the finance head of the legal entity in the version of the entity that holds on the day. */
    boolean isFinanceHead(LegalEntityId legalEntity, EmployeeId employee, LocalDate day);
}
