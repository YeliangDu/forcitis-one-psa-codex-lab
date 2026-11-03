package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One countersign request as its decider sees it in a list. The command is a personnel command or an org-structure command: personnelCommandId and the employee
 * fields are null for the latter, and so are its previous and new legal entity, which differ per employee there. commandKind and commandStatus are the names of the
 * command's own kind and status; requestKind is CHANGE or WITHDRAWAL. Nothing of the person beyond name and number is shown.
 */
public record AttributionCountersignSummary(AttributionCountersignId attributionCountersignId, @Nullable PersonnelCommandId personnelCommandId, String commandKind,
                                            String commandStatus, LocalDate effectiveDate, @Nullable EmployeeId employeeId, @Nullable String employeeDisplayName,
                                            @Nullable String employeeNumber, String requestKind, int requestSequenceNumber, AttributionCountersignStatus status,
                                            LegalEntityId legalEntityId, String legalEntityName, @Nullable LegalEntityId previousLegalEntityId,
                                            @Nullable String previousLegalEntityName, @Nullable LegalEntityId newLegalEntityId, @Nullable String newLegalEntityName,
                                            Instant requestedAt, @Nullable String requestedByDisplayName, LocalDate dueDate, int escalationLevel) {
}
