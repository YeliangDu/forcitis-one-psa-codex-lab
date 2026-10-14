package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One request per (process type, document); each round has its own engine instance and the status changes only through the methods below. */
public final class ApprovalRequest {

    private static final String AGGREGATE_TYPE = "ApprovalRequest";
    private static final String EVENT_TYPE = "platform.approval.ApprovalCompleted";

    private static final Map<ApprovalAction, Map<ApprovalStatus, ApprovalStatus>> TRANSITIONS = new EnumMap<>(ApprovalAction.class);

    static {
        allow(ApprovalAction.RESTART, ApprovalStatus.RUNNING, ApprovalStatus.RETURNED, ApprovalStatus.CANCELLED);
        allow(ApprovalAction.APPROVE, ApprovalStatus.APPROVED, ApprovalStatus.RUNNING);
        allow(ApprovalAction.REJECT, ApprovalStatus.REJECTED, ApprovalStatus.RUNNING);
        allow(ApprovalAction.RETURN, ApprovalStatus.RETURNED, ApprovalStatus.RUNNING);
        allow(ApprovalAction.WITHDRAW, ApprovalStatus.RETURNED, ApprovalStatus.RUNNING);
        allow(ApprovalAction.CANCEL, ApprovalStatus.CANCELLED, ApprovalStatus.RUNNING);
    }

    private static void allow(ApprovalAction action, ApprovalStatus target, ApprovalStatus... from) {
        Map<ApprovalStatus, ApprovalStatus> row = new EnumMap<>(ApprovalStatus.class);
        for (ApprovalStatus status : from) {
            row.put(status, target);
        }
        TRANSITIONS.put(action, row);
    }

    private final ProcessKey processKey;
    private final DocumentReference document;
    private final LegalEntityId legalEntityId;
    private final UserId initiator;
    private final long loadedVersion;
    private final ApprovalStatus loadedStatus;
    private final int loadedRound;
    private final boolean isNew;
    private int round;
    private ApprovalInstanceId instanceId;
    private ApprovalStatus status;
    private String resultReason;
    private long eventSequence;
    private final List<PendingEvent> events = new ArrayList<>();

    private ApprovalRequest(ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId, UserId initiator,
            int round, ApprovalInstanceId instanceId, ApprovalStatus status, String resultReason, long eventSequence, long version,
            boolean isNew) {
        this.processKey = Objects.requireNonNull(processKey, "processKey");
        this.document = Objects.requireNonNull(document, "document");
        this.legalEntityId = Objects.requireNonNull(legalEntityId, "legalEntityId");
        this.initiator = Objects.requireNonNull(initiator, "initiator");
        this.round = round;
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.status = Objects.requireNonNull(status, "status");
        this.resultReason = resultReason;
        this.eventSequence = eventSequence;
        this.loadedVersion = version;
        this.loadedStatus = status;
        this.loadedRound = round;
        this.isNew = isNew;
    }

    public static ApprovalRequest begin(ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId,
            UserId initiator, ApprovalInstanceId instanceId) {
        return new ApprovalRequest(processKey, document, legalEntityId, initiator, 1, instanceId, ApprovalStatus.RUNNING, null, 0, 0, true);
    }

    public static ApprovalRequest restore(ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId,
            UserId initiator, int round, ApprovalInstanceId instanceId, ApprovalStatus status, String resultReason, long eventSequence,
            long version) {
        return new ApprovalRequest(processKey, document, legalEntityId, initiator, round, instanceId, status, resultReason, eventSequence,
                version, false);
    }

    public void restart(UserId by, ApprovalInstanceId newInstance) {
        ApprovalStatus target = target(ApprovalAction.RESTART);
        if (!initiator.equals(by)) {
            throw new ApprovalTransitionException(ApprovalTransitionException.Violation.NOT_INITIATOR,
                    "Only the initiator of the previous round may start the next one");
        }
        status = target;
        round++;
        instanceId = Objects.requireNonNull(newInstance, "newInstance");
        resultReason = null;
    }

    public void approve(LocalDate businessDate) {
        status = target(ApprovalAction.APPROVE);
        complete(ApprovalOutcome.APPROVED, null, businessDate);
    }

    public void reject(String reason, LocalDate businessDate) {
        status = target(ApprovalAction.REJECT);
        complete(ApprovalOutcome.REJECTED, reason, businessDate);
    }

    public void returnRound(String reason, LocalDate businessDate) {
        status = target(ApprovalAction.RETURN);
        complete(ApprovalOutcome.RETURNED, reason, businessDate);
    }

    public WithdrawCheck checkWithdraw(UserId actor, boolean cutoffPassed) {
        if (status != ApprovalStatus.RUNNING) {
            return WithdrawCheck.NOT_RUNNING;
        }
        if (!initiator.equals(actor)) {
            return WithdrawCheck.NOT_INITIATOR;
        }
        return cutoffPassed ? WithdrawCheck.CUTOFF_PASSED : WithdrawCheck.ALLOWED;
    }

    public void withdraw(UserId actor, boolean cutoffPassed, String reason, LocalDate businessDate) {
        ApprovalStatus target = target(ApprovalAction.WITHDRAW);
        requireWithdrawable(actor, cutoffPassed, reason);
        status = target;
        complete(ApprovalOutcome.RETURNED, reason, businessDate);
    }

    public void cancel(UserId actor, boolean cutoffPassed, String reason, LocalDate businessDate) {
        ApprovalStatus target = target(ApprovalAction.CANCEL);
        requireWithdrawable(actor, cutoffPassed, reason);
        status = target;
        complete(ApprovalOutcome.CANCELLED, reason, businessDate);
    }

    private void requireWithdrawable(UserId actor, boolean cutoffPassed, String reason) {
        switch (checkWithdraw(actor, cutoffPassed)) {
            case NOT_INITIATOR -> throw new ApprovalTransitionException(ApprovalTransitionException.Violation.NOT_INITIATOR,
                    "Only the initiator may withdraw or cancel");
            case CUTOFF_PASSED -> throw new ApprovalTransitionException(ApprovalTransitionException.Violation.CUTOFF_PASSED,
                    "A cut-off node has already finished a task");
            case NOT_RUNNING, ALLOWED -> {
            }
        }
        if (reason == null || reason.isBlank()) {
            throw new ApprovalTransitionException(ApprovalTransitionException.Violation.REASON_REQUIRED, "A reason is required");
        }
    }

    private ApprovalStatus target(ApprovalAction action) {
        ApprovalStatus target = TRANSITIONS.get(action).get(status);
        if (target == null) {
            throw new ApprovalTransitionException(ApprovalTransitionException.Violation.ILLEGAL_TRANSITION,
                    action + " is not possible while the approval is " + status);
        }
        return target;
    }

    private void complete(ApprovalOutcome outcome, String reason, LocalDate businessDate) {
        resultReason = reason;
        ApprovalInstanceId instance = instanceId;
        int completedRound = round;
        EventDraft draft = new EventDraft(EVENT_TYPE, 1, AGGREGATE_TYPE, aggregateId(), ++eventSequence, legalEntityId, businessDate,
                new EventSource(SourceSystem.ONE, null), List.of());
        events.add(new PendingEvent(draft, envelope -> new ApprovalCompleted(envelope, document, processKey, instance,
                new RoundNumber(completedRound), outcome, Reason.ofNullable(reason))));
    }

    public String aggregateId() {
        return document.documentType().name() + ":" + document.documentId().value();
    }

    public List<PendingEvent> pullEvents() {
        List<PendingEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public ProcessKey processKey() {
        return processKey;
    }

    public DocumentReference document() {
        return document;
    }

    public LegalEntityId legalEntityId() {
        return legalEntityId;
    }

    public UserId initiator() {
        return initiator;
    }

    public int round() {
        return round;
    }

    public ApprovalInstanceId instanceId() {
        return instanceId;
    }

    public ApprovalStatus status() {
        return status;
    }

    public String resultReason() {
        return resultReason;
    }

    public long eventSequence() {
        return eventSequence;
    }

    public long loadedVersion() {
        return loadedVersion;
    }

    public ApprovalStatus loadedStatus() {
        return loadedStatus;
    }

    public int loadedRound() {
        return loadedRound;
    }

    public boolean isNew() {
        return isNew;
    }
}
