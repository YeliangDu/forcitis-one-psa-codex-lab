package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One request for the finance head of a legal entity to countersign (ADR-0073 item 3.6, design note of #878 section 5.2). The row is append-only: the decision
 * columns are written once together, the escalation level only rises. The decider is never the command's drafter or the person who asked (the table holds that too).
 */
public final class AttributionCountersign {
    public static final int MAX_REASON_LENGTH = 500;
    /** From this escalation level on a business administrator may decide the row (user answer Q3 of 2026-10-30). */
    public static final int ESCALATED_FROM_LEVEL = 2;

    /** The last escalation level: after the 4th working day past the due date; there is no level 4. */
    public static final int LAST_ESCALATION_LEVEL = 3;

    private final AttributionCountersignId id;
    private final @Nullable PersonnelCommandId commandId;
    private final @Nullable OrgStructureCommandId orgCommandId;
    private final @Nullable EmployeeId employeeId;
    private final UserId draftedBy;
    private final CountersignRequestKind requestKind;
    private final int requestSequenceNumber;
    private final LegalEntityId legalEntityId;
    private final Instant requestedAt;
    private final UserId requestedBy;
    private final LocalDate dueOn;
    private int escalationLevel;
    private final @Nullable String requestReason;
    private final long version;
    private CountersignStatus status;
    private @Nullable Instant decidedAt;
    private @Nullable UserId decidedBy;
    private @Nullable String decisionReason;

    private AttributionCountersign(AttributionCountersignId id, @Nullable PersonnelCommandId commandId, @Nullable OrgStructureCommandId orgCommandId,
                                   @Nullable EmployeeId employeeId, UserId draftedBy, CountersignRequestKind requestKind,
                                   int requestSequenceNumber, LegalEntityId legalEntityId, Instant requestedAt, UserId requestedBy, LocalDate dueOn, int escalationLevel,
                                   @Nullable String requestReason, long version, CountersignStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        if ((commandId == null) == (orgCommandId == null) || (commandId == null) != (employeeId == null)) {
            throw new IllegalArgumentException("a request belongs to a personnel command and its employee, or to an org-structure command");
        }
        this.commandId = commandId;
        this.orgCommandId = orgCommandId;
        this.employeeId = employeeId;
        this.draftedBy = Objects.requireNonNull(draftedBy, "draftedBy");
        this.requestKind = Objects.requireNonNull(requestKind, "requestKind");
        this.requestSequenceNumber = requestSequenceNumber;
        this.legalEntityId = Objects.requireNonNull(legalEntityId, "legalEntityId");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.dueOn = Objects.requireNonNull(dueOn, "dueOn");
        this.escalationLevel = escalationLevel;
        this.requestReason = requestReason;
        this.version = version;
        this.status = Objects.requireNonNull(status, "status");
    }

    /** A new open request of a command, asked of the finance head of one legal entity; a withdrawal carries the requester's reason (1 to 500 characters, not blank), a change none. */
    public static AttributionCountersign open(AttributionCountersignId id, PersonnelCommand command, CountersignRequestKind kind, int sequence, LegalEntityId legalEntity,
                                              UserId requestedBy, Instant at, LocalDate dueOn, @Nullable String requestReason) {
        if ((kind == CountersignRequestKind.WITHDRAWAL) != (requestReason != null)) {
            throw new IllegalArgumentException("a withdrawal request carries the requester's reason and a change request none");
        }
        if (requestReason != null) {
            requireReason(requestReason);
        }
        return new AttributionCountersign(id, command.id(), null, command.employeeId(), command.createdBy(), kind, sequence, legalEntity, at, requestedBy, dueOn, 0,
                requestReason, 0, CountersignStatus.OPEN);
    }

    /** As {@link #open} for an org-structure command: the employees it touches are in its basis, not on the request. */
    public static AttributionCountersign openForOrg(AttributionCountersignId id, OrgStructureCommand command, CountersignRequestKind kind, int sequence, LegalEntityId legalEntity,
                                                    UserId requestedBy, Instant at, LocalDate dueOn, @Nullable String requestReason) {
        if ((kind == CountersignRequestKind.WITHDRAWAL) != (requestReason != null)) {
            throw new IllegalArgumentException("a withdrawal request carries the requester's reason and a change request none");
        }
        if (requestReason != null) {
            requireReason(requestReason);
        }
        return new AttributionCountersign(id, null, command.id(), null, command.createdBy(), kind, sequence, legalEntity, at, requestedBy, dueOn, 0,
                requestReason, 0, CountersignStatus.OPEN);
    }

    public static AttributionCountersign restore(AttributionCountersignId id, @Nullable PersonnelCommandId commandId, @Nullable OrgStructureCommandId orgCommandId,
                                                 @Nullable EmployeeId employeeId, UserId draftedBy,
                                                 CountersignRequestKind requestKind, int requestSequenceNumber, LegalEntityId legalEntityId, Instant requestedAt,
                                                 UserId requestedBy, LocalDate dueOn, int escalationLevel, @Nullable String requestReason, long version,
                                                 @Nullable String decision,
                                                 @Nullable Instant decidedAt, @Nullable UserId decidedBy, @Nullable String decisionReason) {
        AttributionCountersign row = new AttributionCountersign(id, commandId, orgCommandId, employeeId, draftedBy, requestKind, requestSequenceNumber, legalEntityId, requestedAt,
                requestedBy, dueOn, escalationLevel, requestReason, version,
                decision == null ? CountersignStatus.OPEN : CountersignStatus.valueOf(decision));
        row.decidedAt = decidedAt;
        row.decidedBy = decidedBy;
        row.decisionReason = decisionReason;
        return row;
    }

    /** The reason is optional on approval; when given it is 1 to 500 characters and not blank. */
    public void approve(UserId by, @Nullable String reason, Instant at) {
        if (reason != null) {
            requireReason(reason);
        }
        decide(CountersignStatus.APPROVED, by, reason, at);
    }

    public void reject(UserId by, @Nullable String reason, Instant at) {
        requireReason(reason);
        decide(CountersignStatus.REJECTED, by, reason, at);
    }

    /** Closed without a decision because the command left the state that needed it. */
    public void close(Instant at) {
        status = CountersignStatus.MACHINE.require(status, CountersignStatus.LAPSED, WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_OPEN);
        decidedAt = Objects.requireNonNull(at, "at");
    }

    /** The level only rises, only while the request is open, and stops at the last level. */
    public void escalateTo(int level) {
        if (!isOpen()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_OPEN, ErrorKind.CONFLICT, "only an open countersign request escalates");
        }
        if (level <= escalationLevel || level > LAST_ESCALATION_LEVEL) {
            throw new IllegalArgumentException("escalation level " + level + " does not rise from " + escalationLevel + " within 1.." + LAST_ESCALATION_LEVEL);
        }
        escalationLevel = level;
    }

    private void decide(CountersignStatus next, UserId by, @Nullable String reason, Instant at) {
        status = CountersignStatus.MACHINE.require(status, next, WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_OPEN);
        decidedAt = Objects.requireNonNull(at, "at");
        decidedBy = Objects.requireNonNull(by, "by");
        decisionReason = reason;
    }

    private static void requireReason(@Nullable String reason) {
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_REASON_INVALID, ErrorKind.UNPROCESSABLE,
                    "a reason is 1 to " + MAX_REASON_LENGTH + " characters and not blank");
        }
    }

    public boolean isOpen() {
        return status == CountersignStatus.OPEN;
    }

    public AttributionCountersignId id() {
        return id;
    }

    /** The personnel command of a request about one employee's entry; an org-structure command's request answers {@link #orgCommandId()}. */
    public PersonnelCommandId commandId() {
        return Objects.requireNonNull(commandId, "the request belongs to an org-structure command");
    }

    public @Nullable OrgStructureCommandId orgCommandId() {
        return orgCommandId;
    }

    public boolean isOrg() {
        return orgCommandId != null;
    }

    public EmployeeId employeeId() {
        return Objects.requireNonNull(employeeId, "the request belongs to an org-structure command");
    }

    public UserId draftedBy() {
        return draftedBy;
    }

    public CountersignRequestKind requestKind() {
        return requestKind;
    }

    public int requestSequenceNumber() {
        return requestSequenceNumber;
    }

    public LegalEntityId legalEntityId() {
        return legalEntityId;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public UserId requestedBy() {
        return requestedBy;
    }

    public LocalDate dueOn() {
        return dueOn;
    }

    public int escalationLevel() {
        return escalationLevel;
    }

    public @Nullable String requestReason() {
        return requestReason;
    }

    public long version() {
        return version;
    }

    public CountersignStatus status() {
        return status;
    }

    public @Nullable Instant decidedAt() {
        return decidedAt;
    }

    public @Nullable UserId decidedBy() {
        return decidedBy;
    }

    public @Nullable String decisionReason() {
        return decisionReason;
    }
}
