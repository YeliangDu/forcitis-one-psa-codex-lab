package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.AttributionCountersignStatus;
import com.forcitis.one.organization.workforce.api.AttributionCountersignSummary;
import com.forcitis.one.organization.workforce.api.CountersignCommandKind;
import com.forcitis.one.organization.workforce.api.CountersignCommandStatus;
import com.forcitis.one.organization.workforce.api.CountersignDecisionKind;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.organization.workforce.application.CountersignReadViews;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The bodies of the countersign read paths (#910 amendment B): only the columns the ruling lists. */
final class CountersignReadResponses {

    private CountersignReadResponses() {
    }

    record PersonResponse(UUID employeeId, String displayName, String employeeCode) {
        static PersonResponse of(CountersignReadViews.Person p) {
            return new PersonResponse(p.employeeId(), p.displayName(), p.employeeCode());
        }
    }

    record CommandResponse(@Nullable UUID orgStructureCommandId, @Nullable UUID personnelCommandId, CountersignCommandKind commandKind, LocalDate effectiveDate, CountersignCommandStatus status,
                           @Nullable String withdrawalReason, @Nullable PersonResponse employee, @Nullable UUID previousLegalEntityId, @Nullable String previousLegalEntityName,
                           @Nullable UUID newLegalEntityId, @Nullable String newLegalEntityName, @Nullable UUID orgUnitId, @Nullable String orgUnitName) {
        static CommandResponse of(CountersignReadViews.Command c) {
            return new CommandResponse(c.orgStructureCommandId(), c.personnelCommandId(), c.commandKind(), c.effectiveDate(), c.status(), c.withdrawalReason(),
                    c.employee() == null ? null : PersonResponse.of(c.employee()), c.previousLegalEntityId(), c.previousLegalEntityName(), c.newLegalEntityId(),
                    c.newLegalEntityName(), c.orgUnitId(), c.orgUnitName());
        }
    }

    record RequestResponse(UUID attributionCountersignId, CountersignRequestKind requestKind, int requestSequenceNumber, UUID legalEntityId, @Nullable String legalEntityName, Instant requestedAt,
                           @Nullable String requestedByDisplayName, LocalDate dueOnDate, int escalationLevel, @Nullable CountersignDecisionKind decisionKind, @Nullable Instant decidedAt,
                           @Nullable String decidedByDisplayName, @Nullable String decisionReason) {
        static RequestResponse of(CountersignReadViews.Request r) {
            return new RequestResponse(r.attributionCountersignId(), r.requestKind(), r.requestSequenceNumber(), r.legalEntityId(), r.legalEntityName(), r.requestedAt(),
                    r.requestedByDisplayName(), r.dueOnDate(), r.escalationLevel(), r.decisionKind(), r.decidedAt(), r.decidedByDisplayName(), r.decisionReason());
        }
    }

    /** One request of the inbox: the person by name only, the employee number stays in the detail read. */
    record SummaryResponse(UUID attributionCountersignId, @Nullable UUID personnelCommandId, String commandKind, String commandStatus, LocalDate effectiveDate, @Nullable UUID employeeId,
                           @Nullable String employeeDisplayName, String requestKind, int requestSequenceNumber, AttributionCountersignStatus status, UUID legalEntityId,
                           String legalEntityName, @Nullable UUID previousLegalEntityId, @Nullable String previousLegalEntityName, @Nullable UUID newLegalEntityId,
                           @Nullable String newLegalEntityName, Instant requestedAt, @Nullable String requestedByDisplayName, LocalDate dueDate, int escalationLevel) {
        static SummaryResponse of(AttributionCountersignSummary s) {
            return new SummaryResponse(s.attributionCountersignId().value(), s.personnelCommandId() == null ? null : s.personnelCommandId().value(), s.commandKind(), s.commandStatus(),
                    s.effectiveDate(), s.employeeId() == null ? null : s.employeeId().value(), s.employeeDisplayName(), s.requestKind(), s.requestSequenceNumber(), s.status(),
                    s.legalEntityId().value(), s.legalEntityName(), s.previousLegalEntityId() == null ? null : s.previousLegalEntityId().value(), s.previousLegalEntityName(),
                    s.newLegalEntityId() == null ? null : s.newLegalEntityId().value(), s.newLegalEntityName(), s.requestedAt(), s.requestedByDisplayName(), s.dueDate(), s.escalationLevel());
        }
    }

    record RowResponse(CommandResponse command, List<RequestResponse> requests) {
        static RowResponse of(CountersignReadViews.Row row) {
            return new RowResponse(CommandResponse.of(row.command()), row.requests().stream().map(RequestResponse::of).toList());
        }
    }

    record BasisLineResponse(UUID employeeId, String displayName, String employeeCode, UUID previousLegalEntityId, @Nullable String previousLegalEntityName,
                             UUID newLegalEntityId, @Nullable String newLegalEntityName) {
        static BasisLineResponse of(CountersignReadViews.BasisLine l) {
            return new BasisLineResponse(l.employeeId(), l.displayName(), l.employeeCode(), l.previousLegalEntityId(), l.previousLegalEntityName(), l.newLegalEntityId(),
                    l.newLegalEntityName());
        }
    }

    record MergeLineResponse(UUID employeeId, String displayName, String employeeCode, UUID orgUnitId, @Nullable String orgUnitName, @Nullable UUID previousLegalEntityId,
                             @Nullable String previousLegalEntityName, @Nullable UUID newLegalEntityId, @Nullable String newLegalEntityName, boolean needsAttributionCountersign, boolean primaryInUnit,
                             int concurrentAssignmentCount) {
        static MergeLineResponse of(CountersignReadViews.MergeLine l) {
            return new MergeLineResponse(l.employeeId(), l.displayName(), l.employeeCode(), l.orgUnitId(), l.orgUnitName(), l.previousLegalEntityId(), l.previousLegalEntityName(),
                    l.newLegalEntityId(), l.newLegalEntityName(), l.needsAttributionCountersign(), l.primaryInUnit(), l.concurrentAssignmentCount());
        }
    }

    record MergePreviewResponse(List<MergeLineResponse> items) {
    }
}
