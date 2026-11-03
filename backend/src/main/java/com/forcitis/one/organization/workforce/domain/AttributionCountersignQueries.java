package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Reading side of the countersign requests, scoped to what a decider may see (every method but {@link #sameCommand} and {@link #employeeNames} filters by it). */
public interface AttributionCountersignQueries {

    /** What one caller may see: the requests of the entities they head on {@code today}, and, when {@code escalated}, those of any entity from level 2 on. */
    record Visible(EmployeeId head, boolean escalated, LocalDate today) {
    }

    /** One request with the facts of its command; the command is a personnel command or an org-structure command, so the employee and legal entity basis may be empty. */
    record Row(UUID id, @Nullable PersonnelCommandId personnelCommandId, String commandKind, String commandState, LocalDate effectiveDate, @Nullable EmployeeId employeeId,
               @Nullable String employeeDisplayName, @Nullable String employeeNumber, String requestKind, int requestSequenceNumber, CountersignStatus status,
               LegalEntityId legalEntityId, String legalEntityName, @Nullable LegalEntityId previousLegalEntityId, @Nullable String previousLegalEntityName,
               @Nullable LegalEntityId newLegalEntityId, @Nullable String newLegalEntityName, Instant requestedAt, UUID requestedBy, LocalDate dueDate, int escalationLevel,
               @Nullable OrgUnitId previousOrgUnitId, @Nullable String previousOrgUnitName, @Nullable OrgUnitId newOrgUnitId, @Nullable String newOrgUnitName,
               @Nullable String requestReason, @Nullable Instant decidedAt, @Nullable UUID decidedBy, @Nullable String decisionReason) {
    }

    /** Due date ascending, then id; {@code after} is the id of the last row of the page before and only counts when that row is visible too. At most {@code limit} rows. */
    List<Row> list(Visible visible, CountersignStatus status, @Nullable UUID after, int limit);

    Optional<Row> find(Visible visible, AttributionCountersignId id);

    /** Every request of the command the given one belongs to, the given one included, whatever its legal entity. */
    List<Row> sameCommand(AttributionCountersignId id);

    /** Display names by employee id; an unknown id has no entry. */
    Map<UUID, String> employeeNames(Collection<UUID> employeeIds);
}
