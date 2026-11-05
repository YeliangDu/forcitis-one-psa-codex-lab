package com.forcitis.one.organization.recruitment.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import com.forcitis.one.organization.recruitment.api.RecruitmentError;
import com.forcitis.one.organization.recruitment.api.RequisitionDocument;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.api.RequisitionStatus;
import com.forcitis.one.organization.recruitment.events.RequisitionApproved;
import com.forcitis.one.organization.recruitment.events.RequisitionClosed;
import com.forcitis.one.organization.recruitment.events.RequisitionSubmitted;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * One position, one org unit, how many people and from when (AGG-65). The requester asks; the approval of one node decides; a closed requisition
 * never changes. The headcount is what the requester states and is checked against nothing (Q26).
 * Events published so far are not stored: at most one approval and one closing exist, so the count follows from the state (see {@link #restore}).
 */
public final class RecruitmentRequisition extends AggregateRoot {

    public static final StateMachine<RequisitionStatus> MACHINE = StateMachine.of(RequisitionStatus.class)
            .allow(RequisitionStatus.DRAFT, RequisitionStatus.SUBMITTED)
            .allow(RequisitionStatus.SUBMITTED, RequisitionStatus.APPROVED, RequisitionStatus.CLOSED)
            .allow(RequisitionStatus.APPROVED, RequisitionStatus.CLOSED)
            .build();

    private static final String APPROVAL_REQUEST = "ApprovalRequest";

    private final RequisitionId id;
    private final @Nullable DocumentNumber number;
    private final PositionId positionId;
    private final OrgUnitId orgUnitId;
    private final LegalEntityId legalEntityId;
    private final EmployeeId requestedBy;
    private final int requestedCount;
    private final @Nullable LocalDate expectedStartDate;
    private final @Nullable String description;
    private final @Nullable ApprovalInstanceId approvalInstanceId;
    private final @Nullable Instant submittedAt;
    private final Instant createdAt;
    private final RequisitionStatus loadedStatus;
    private final boolean isNew;
    private RequisitionStatus status;
    private @Nullable CloseReasonKind closeReason;
    private @Nullable Instant approvedAt;
    private @Nullable Instant closedAt;

    private RecruitmentRequisition(RequisitionId requisitionId, @Nullable DocumentNumber number, PositionId positionId, OrgUnitId orgUnitId,
            LegalEntityId legalEntityId, EmployeeId requestedBy, int requestedCount, @Nullable LocalDate expectedStartDate,
            @Nullable String description, RequisitionStatus status, @Nullable CloseReasonKind closeReason,
            @Nullable ApprovalInstanceId approvalInstanceId, @Nullable Instant submittedAt, @Nullable Instant approvedAt,
            @Nullable Instant closedAt, Instant createdAt, long version, long eventSequence, boolean isNew) {
        super(version, eventSequence);
        this.id = Objects.requireNonNull(requisitionId, "requisitionId");
        this.number = number;
        this.positionId = Objects.requireNonNull(positionId, "positionId");
        this.orgUnitId = Objects.requireNonNull(orgUnitId, "orgUnitId");
        this.legalEntityId = Objects.requireNonNull(legalEntityId, "legalEntityId");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.requestedCount = requestedCount;
        this.expectedStartDate = expectedStartDate;
        this.description = description;
        this.status = Objects.requireNonNull(status, "status");
        this.closeReason = closeReason;
        this.approvalInstanceId = approvalInstanceId;
        this.submittedAt = submittedAt;
        this.approvedAt = approvedAt;
        this.closedAt = closedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.loadedStatus = status;
        this.isNew = isNew;
    }

    /**
     * A requisition created directly in SUBMITTED: the approval has been started and the number taken by the caller in the same transaction. Those two
     * aggregates change in that transaction, so the submitted event names them (counter) and the approval request as related aggregates.
     */
    public static RecruitmentRequisition submit(RequisitionId requisitionId, DocumentNumber number, PositionId positionId, OrgUnitId orgUnitId,
            LegalEntityId legalEntityId, EmployeeId requestedBy, int requestedCount, @Nullable LocalDate expectedStartDate,
            @Nullable String description, ApprovalInstanceId approvalInstanceId, AggregateReference counter, Instant now, LocalDate today) {
        if (requestedCount < 1 || requestedCount > 999) {
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_COUNT_INVALID.refusal("The requested count is 1 to 999, was " + requestedCount);
        }
        if (description != null && (description.isBlank() || description.length() > 500)) {
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_COUNT_INVALID.refusal("The description is 1 to 500 characters when given");
        }
        RecruitmentRequisition created = new RecruitmentRequisition(requisitionId, Objects.requireNonNull(number, "number"), positionId, orgUnitId,
                legalEntityId, requestedBy, requestedCount, expectedStartDate, description, RequisitionStatus.SUBMITTED, null,
                Objects.requireNonNull(approvalInstanceId, "approvalInstanceId"), now, null, null, now, 0, 0, true);
        created.record("organization.recruitment.RequisitionSubmitted", today,
                List.of(new AggregateReference(APPROVAL_REQUEST, approvalInstanceId.value()), counter),
                envelope -> new RequisitionSubmitted(envelope, requisitionId, orgUnitId, positionId, now));
        return created;
    }

    /** The events published so far follow from the state: SUBMITTED one, APPROVED one more, CLOSED one more. */
    public static RecruitmentRequisition restore(RequisitionId requisitionId, @Nullable DocumentNumber number, PositionId positionId,
            OrgUnitId orgUnitId, LegalEntityId legalEntityId, EmployeeId requestedBy, int requestedCount, @Nullable LocalDate expectedStartDate,
            @Nullable String description, RequisitionStatus status, @Nullable CloseReasonKind closeReason,
            @Nullable ApprovalInstanceId approvalInstanceId, @Nullable Instant submittedAt, @Nullable Instant approvedAt,
            @Nullable Instant closedAt, Instant createdAt, long version) {
        long published = 1 + (approvedAt != null ? 1 : 0) + (status == RequisitionStatus.CLOSED ? 1 : 0);
        return new RecruitmentRequisition(requisitionId, number, positionId, orgUnitId, legalEntityId, requestedBy, requestedCount,
                expectedStartDate, description, status, closeReason, approvalInstanceId, submittedAt, approvedAt, closedAt, createdAt,
                version, published, false);
    }

    /** The approval process completed approved. */
    public void approve(Instant now, LocalDate today) {
        status = MACHINE.require(status, RequisitionStatus.APPROVED);
        approvedAt = now;
        record("organization.recruitment.RequisitionApproved", today, List.of(), envelope -> new RequisitionApproved(envelope, id, orgUnitId,
                positionId, now));
    }

    /** The approver rejected it, or the approval ended otherwise without approving. */
    public void closeFromApproval(CloseReasonKind reason, Instant now, LocalDate today) {
        if (reason != CloseReasonKind.REJECTED && reason != CloseReasonKind.WITHDRAWN) {
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_CLOSE_REASON_INVALID.refusal("An approval ends a requisition as REJECTED or WITHDRAWN, not " + reason);
        }
        requireStatus(RequisitionStatus.SUBMITTED);
        close(reason, now, today, List.of());
    }

    /** The requester takes a requisition back while it is still in approval. */
    public void withdraw(EmployeeId by, Instant now, LocalDate today) {
        if (!requestedBy.equals(by)) {
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND.refusal("No such requisition");
        }
        requireStatus(RequisitionStatus.SUBMITTED);
        // the caller cancelled the approval in this transaction, so the approval request is named as touched together
        close(CloseReasonKind.WITHDRAWN, now, today, List.of(new AggregateReference(APPROVAL_REQUEST, approvalInstanceId().orElseThrow().value())));
    }

    /** CloseRequisition: an approved requisition is cancelled or filled. Closing a closed one is the caller's no-op ({@link #isClosed}). */
    public void closeApproved(CloseReasonKind reason, Instant now, LocalDate today) {
        if (reason != CloseReasonKind.CANCELLED && reason != CloseReasonKind.FILLED) {
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_CLOSE_REASON_INVALID.refusal("An approved requisition is closed as CANCELLED or FILLED, not " + reason);
        }
        requireStatus(RequisitionStatus.APPROVED);
        close(reason, now, today, List.of());
    }

    private void close(CloseReasonKind reason, Instant now, LocalDate today, List<AggregateReference> touchedTogether) {
        status = MACHINE.require(status, RequisitionStatus.CLOSED);
        closeReason = reason;
        closedAt = now;
        record("organization.recruitment.RequisitionClosed", today, touchedTogether, envelope -> new RequisitionClosed(envelope, id, reason, now));
    }

    private void requireStatus(RequisitionStatus expected) {
        if (status != expected) {
            throw KernelError.KERNEL_TRANSITION_ILLEGAL.refusal("The requisition is " + status + ", not " + expected);
        }
    }

    private void record(String eventType, LocalDate businessDate, List<AggregateReference> related, Function<EventEnvelope, DomainEvent> assemble) {
        record((envelopes, sequence) -> assemble.apply(envelopes.wrap(new EventDraft(eventType, 1, "RecruitmentRequisition",
                id.value(), sequence, legalEntityId, businessDate, related))));
    }

    public boolean isClosed() {
        return status == RequisitionStatus.CLOSED;
    }

    public boolean isAwaitingApproval() {
        return status == RequisitionStatus.SUBMITTED;
    }

    public DocumentReference document() {
        return new DocumentReference(RequisitionDocument.TYPE, new DocumentId(id.value()));
    }

    public RequisitionId requisitionId() {
        return id;
    }

    public Optional<DocumentNumber> number() {
        return Optional.ofNullable(number);
    }

    public PositionId positionId() {
        return positionId;
    }

    public OrgUnitId orgUnitId() {
        return orgUnitId;
    }

    public LegalEntityId legalEntityId() {
        return legalEntityId;
    }

    public EmployeeId requestedBy() {
        return requestedBy;
    }

    public int requestedCount() {
        return requestedCount;
    }

    public Optional<LocalDate> expectedStartDate() {
        return Optional.ofNullable(expectedStartDate);
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public RequisitionStatus status() {
        return status;
    }

    public RequisitionStatus loadedStatus() {
        return loadedStatus;
    }

    public Optional<CloseReasonKind> closeReason() {
        return Optional.ofNullable(closeReason);
    }

    public Optional<ApprovalInstanceId> approvalInstanceId() {
        return Optional.ofNullable(approvalInstanceId);
    }

    public Optional<Instant> submittedAt() {
        return Optional.ofNullable(submittedAt);
    }

    public Optional<Instant> approvedAt() {
        return Optional.ofNullable(approvedAt);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isNew() {
        return isNew;
    }
}
