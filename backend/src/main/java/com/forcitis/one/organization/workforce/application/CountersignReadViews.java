package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.CountersignCommandKind;
import com.forcitis.one.organization.workforce.api.CountersignCommandStatus;
import com.forcitis.one.organization.workforce.api.CountersignDecisionKind;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What the countersign read paths answer (#910 amendment B, "columns shown, and only these"): no level-3 data, no contact data, no other record of the employee. */
public final class CountersignReadViews {

    private CountersignReadViews() {
    }

    public record Person(UUID employeeId, String displayName, String employeeCode) {
    }

    /** The command a request belongs to: exactly one of the two ids, the employee only for a personnel command, the org unit only for an org-structure command. */
    public record Command(@Nullable UUID orgStructureCommandId, @Nullable UUID personnelCommandId, CountersignCommandKind commandKind, LocalDate effectiveDate, CountersignCommandStatus status,
                          @Nullable String withdrawalReason, @Nullable Person employee, @Nullable UUID previousLegalEntityId, @Nullable String previousLegalEntityName,
                          @Nullable UUID newLegalEntityId, @Nullable String newLegalEntityName, @Nullable UUID orgUnitId, @Nullable String orgUnitName) {
    }

    public record Request(UUID attributionCountersignId, CountersignRequestKind requestKind, int requestSequenceNumber, UUID legalEntityId, @Nullable String legalEntityName, Instant requestedAt,
                          @Nullable String requestedByDisplayName, LocalDate dueOnDate, int escalationLevel, @Nullable CountersignDecisionKind decisionKind, @Nullable Instant decidedAt,
                          @Nullable String decidedByDisplayName, @Nullable String decisionReason) {
    }

    public record Row(Command command, List<Request> requests) {
    }

    public record BasisLine(UUID employeeId, String displayName, String employeeCode, UUID previousLegalEntityId, @Nullable String previousLegalEntityName, UUID newLegalEntityId, @Nullable String newLegalEntityName) {
    }

    public record MergeLine(UUID employeeId, String displayName, String employeeCode, UUID orgUnitId, @Nullable String orgUnitName, @Nullable UUID previousLegalEntityId, @Nullable String previousLegalEntityName,
                            @Nullable UUID newLegalEntityId, @Nullable String newLegalEntityName, boolean needsAttributionCountersign, boolean primaryInUnit, int concurrentAssignmentCount) {
    }
}
