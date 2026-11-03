package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The summary fields of one request plus the org units the command moves the person between (null unless it changes the assignment), the reason a withdrawal
 * request was made with (null for a change request), the reason it was decided with, and every request of the same command, the other legal entities' included.
 */
public record AttributionCountersignDetail(AttributionCountersignId attributionCountersignId, @Nullable PersonnelCommandId personnelCommandId, String commandKind,
                                           String commandStatus, LocalDate effectiveDate, @Nullable EmployeeId employeeId, @Nullable String employeeDisplayName,
                                           @Nullable String employeeNumber, String requestKind, int requestSequenceNumber, AttributionCountersignStatus status,
                                           LegalEntityId legalEntityId, String legalEntityName, @Nullable LegalEntityId previousLegalEntityId,
                                           @Nullable String previousLegalEntityName, @Nullable LegalEntityId newLegalEntityId, @Nullable String newLegalEntityName,
                                           Instant requestedAt, @Nullable String requestedByDisplayName, LocalDate dueDate, int escalationLevel,
                                           @Nullable OrgUnitId previousOrgUnitId, @Nullable String previousOrgUnitName, @Nullable OrgUnitId newOrgUnitId,
                                           @Nullable String newOrgUnitName, @Nullable String requestReason, @Nullable String decisionReason,
                                           List<AttributionCountersignRow> rows) {

    /** One request of the command: who asked when, until when it is due, and how it was decided. */
    public record AttributionCountersignRow(AttributionCountersignId attributionCountersignId, String requestKind, int requestSequenceNumber,
                                            LegalEntityId legalEntityId, String legalEntityName, AttributionCountersignStatus status, Instant requestedAt,
                                            @Nullable String requestedByDisplayName, LocalDate dueDate, int escalationLevel, @Nullable Instant decidedAt,
                                            @Nullable String decidedByDisplayName, @Nullable String decisionReason) {
    }
}
