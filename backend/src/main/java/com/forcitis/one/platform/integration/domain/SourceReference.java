package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.platform.integration.api.ConfirmationStatus;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.IntegrationError;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.events.SourceReferenceConfirmed;
import com.forcitis.one.platform.integration.events.SourceReferenceRegistered;
import com.forcitis.one.platform.integration.events.SourceReferenceRejected;
import com.forcitis.one.platform.integration.events.SourceReferenceSuperseded;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The correspondence between the id of an object in an external system and its own id. Rows the source sync allocates are confirmed
 * from the start; the only one registered by hand is the accounting organization code of a legal entity, which a second person
 * confirms or rejects. Source id and own id never change once the row exists.
 */
public final class SourceReference extends AggregateRoot {

    public static final StateMachine<ConfirmationStatus> MACHINE = StateMachine.of(ConfirmationStatus.class)
            .allow(ConfirmationStatus.PENDING, ConfirmationStatus.CONFIRMED, ConfirmationStatus.REJECTED)
            .build();

    private final SourceReferenceId sourceReferenceId;
    private final ExternalSource system;
    private final SourceObjectKind kind;
    private final SourceObjectId sourceId;
    private final OwnObjectId ownId;
    private final @Nullable LocalDate effectiveFrom;
    private @Nullable LocalDate effectiveTo;
    private final @Nullable UserId registeredBy;
    private ConfirmationStatus status;
    private final ConfirmationStatus loadedStatus;
    private @Nullable UserId decidedBy;
    private @Nullable Instant decidedAt;

    private SourceReference(SourceReferenceId sourceReferenceId, ExternalSource system, SourceObjectKind kind, SourceObjectId sourceId, OwnObjectId ownId,
                            @Nullable LocalDate effectiveFrom, @Nullable LocalDate effectiveTo, @Nullable UserId registeredBy, ConfirmationStatus status,
                            @Nullable UserId decidedBy, @Nullable Instant decidedAt, long version, long eventSequence) {
        super(version, eventSequence);
        this.sourceReferenceId = Objects.requireNonNull(sourceReferenceId, "sourceReferenceId");
        this.system = Objects.requireNonNull(system, "system");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.ownId = Objects.requireNonNull(ownId, "ownId");
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.registeredBy = registeredBy;
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    /** What the source sync allocates when it first sees an object: confirmed at once, no period, nobody to decide it. */
    public static SourceReference allocate(SourceReferenceId id, ExternalSource system, SourceObjectKind kind, SourceObjectId sourceId, OwnObjectId ownId) {
        if (kind.hasPeriod()) {
            throw new IllegalArgumentException(kind + " references carry a period and are registered by hand, not allocated");
        }
        return new SourceReference(id, system, kind, sourceId, ownId, null, null, null, ConfirmationStatus.CONFIRMED, null, null, 0, 0);
    }

    /** Only the accounting organization code of a legal entity may be written by a person (ADR-0033 section 5 item 4). */
    public static void requireRegistrationAllowed(ExternalSource system, SourceObjectKind kind) {
        if (system != ExternalSource.ACCOUNTING_SYSTEM || kind != SourceObjectKind.LEGAL_ENTITY) {
            throw new DomainException(IntegrationError.PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE,
                    kind + " references of " + system + " are written by the sync or the import, not by hand");
        }
    }

    public static SourceReference register(SourceReferenceId id, ExternalSource system, SourceObjectKind kind, SourceObjectId sourceId, OwnObjectId ownId,
                                           @Nullable LocalDate effectiveFrom, UserId registeredBy) {
        requireRegistrationAllowed(system, kind);
        if (sourceId.value() <= 0) {
            throw invalid("an accounting organization code is a positive number");
        }
        if (effectiveFrom == null) {
            throw invalid("effectiveFrom, the day the code starts to apply, is required");
        }
        SourceReference reference = new SourceReference(id, system, kind, sourceId, ownId, effectiveFrom, null,
                Objects.requireNonNull(registeredBy, "registeredBy"), ConfirmationStatus.PENDING, null, null, 0, 0);
        reference.record((envelopes, version) -> new SourceReferenceRegistered(envelopes.wrap(reference.draft("SourceReferenceRegistered", version)),
                system, kind, ownId));
        return reference;
    }

    public static SourceReference restore(SourceReferenceId id, ExternalSource system, SourceObjectKind kind, SourceObjectId sourceId, OwnObjectId ownId,
                                          @Nullable LocalDate effectiveFrom, @Nullable LocalDate effectiveTo, @Nullable UserId registeredBy,
                                          String status, @Nullable UserId decidedBy, @Nullable Instant decidedAt, long version, long eventSequence) {
        return new SourceReference(id, system, kind, sourceId, ownId, effectiveFrom, effectiveTo, registeredBy, ConfirmationStatus.valueOf(status),
                decidedBy, decidedAt, version, eventSequence);
    }

    public void confirm(UserId confirmer, Instant at) {
        requireNotTheRegistrant(confirmer);
        this.status = MACHINE.require(status, ConfirmationStatus.CONFIRMED, IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_ALREADY_DECIDED);
        decide(confirmer, at);
        record((envelopes, version) -> new SourceReferenceConfirmed(envelopes.wrap(draft("SourceReferenceConfirmed", version)), system, kind, ownId));
    }

    public void reject(UserId rejecter, Instant at) {
        requireNotTheRegistrant(rejecter);
        this.status = MACHINE.require(status, ConfirmationStatus.REJECTED, IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_ALREADY_DECIDED);
        decide(rejecter, at);
        record((envelopes, version) -> new SourceReferenceRejected(envelopes.wrap(draft("SourceReferenceRejected", version)), system, kind, ownId));
    }

    /** The confirmed reference in effect ends where its successor starts. */
    public void supersede(LocalDate successorFrom) {
        Objects.requireNonNull(successorFrom, "successorFrom");
        if (status != ConfirmationStatus.CONFIRMED || effectiveTo != null || effectiveFrom == null || !successorFrom.isAfter(effectiveFrom)) {
            throw new DomainException(IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_CONFLICT, ErrorKind.CONFLICT,
                    "the reference in effect since " + effectiveFrom + " cannot end on " + successorFrom);
        }
        this.effectiveTo = successorFrom;
        record((envelopes, version) -> new SourceReferenceSuperseded(envelopes.wrap(draft("SourceReferenceSuperseded", version, successorFrom)), system, kind, ownId,
                successorFrom));
    }

    private void decide(UserId by, Instant at) {
        this.decidedBy = by;
        this.decidedAt = Objects.requireNonNull(at, "at");
    }

    private void requireNotTheRegistrant(UserId decider) {
        if (registeredBy != null && registeredBy.equals(decider)) {
            throw new DomainException(IntegrationError.PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR, ErrorKind.FORBIDDEN,
                    "the person who registered a source reference cannot decide it");
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException(IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_INVALID, ErrorKind.UNPROCESSABLE, message);
    }

    public SourceReferenceId sourceReferenceId() {
        return sourceReferenceId;
    }

    public ExternalSource system() {
        return system;
    }

    public SourceObjectKind kind() {
        return kind;
    }

    public SourceObjectId sourceId() {
        return sourceId;
    }

    public OwnObjectId ownId() {
        return ownId;
    }

    public @Nullable LocalDate effectiveFrom() {
        return effectiveFrom;
    }

    public @Nullable LocalDate effectiveTo() {
        return effectiveTo;
    }

    public @Nullable UserId registeredBy() {
        return registeredBy;
    }

    public ConfirmationStatus status() {
        return status;
    }

    public ConfirmationStatus loadedStatus() {
        return loadedStatus;
    }

    public @Nullable UserId decidedBy() {
        return decidedBy;
    }

    public @Nullable Instant decidedAt() {
        return decidedAt;
    }

    private EventDraft draft(String eventName, long version) {
        return draft(eventName, version, null);
    }

    private EventDraft draft(String eventName, long version, @Nullable LocalDate businessDate) {
        return new EventDraft("platform.integration." + eventName, 1, "SourceReference", sourceReferenceId.toString(), version, null, businessDate,
                new EventSource(SourceSystem.ONE, null), List.of());
    }
}
