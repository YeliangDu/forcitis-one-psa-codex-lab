package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.IntegrationError;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One message to the outside. It is queued PENDING (or already SKIPPED / FAILED when nobody may receive it) and leaves PENDING
 * once: sent, skipped, or dead after the last retry; a retry keeps it PENDING. A HANDLED_NOTE delivery replaces the card whose delivery id is targetDeliveryId.
 * The claim number (attemptCount) guards a claimed delivery: the repository applies a change only to the claim it was made for.
 */
public final class OutboundDelivery extends AggregateRoot {
    public static final StateMachine<DeliveryStatus> MACHINE = StateMachine.of(DeliveryStatus.class)
            .allow(DeliveryStatus.PENDING, DeliveryStatus.SENT, DeliveryStatus.SKIPPED, DeliveryStatus.DEAD_LETTER, DeliveryStatus.PENDING)
            .build();

    private final @Nullable OutboundDeliveryId deliveryId;
    private final DeliverySubject subject;
    private final List<WecomUserId> recipients;
    private final List<WecomUserId> skippedRecipients;
    private final String title;
    private final String description;
    private final String targetUrl;
    private final DeliveryStatus loadedStatus;
    private final int attemptCount;
    private @Nullable String handledNote;
    private @Nullable String handledResult;
    private final DeliveryKind kind;
    // 被本投递替换的投递
    private final @Nullable OutboundDeliveryId target;
    private final UUID correlation;
    private DeliveryStatus status;
    private @Nullable String responseCode;
    private @Nullable Instant nextAttemptAt;
    private @Nullable Instant sentAt;
    private @Nullable String lastError;

    private OutboundDelivery(@Nullable OutboundDeliveryId deliveryId, DeliverySubject subject, List<WecomUserId> recipients,
                             List<WecomUserId> skippedRecipients, String title, String description, String targetUrl,
                             DeliveryStatus status, int attemptCount, @Nullable String responseCode, @Nullable String handledNote, @Nullable String handledResult, DeliveryKind kind,
                             @Nullable OutboundDeliveryId target, UUID correlationId, @Nullable Instant nextAttemptAt, @Nullable String lastError, long version, long eventSequence) {
        super(version, eventSequence);
        this.deliveryId = deliveryId;
        this.subject = Objects.requireNonNull(subject, "subject");
        this.recipients = List.copyOf(recipients);
        this.skippedRecipients = List.copyOf(skippedRecipients);
        this.title = title;
        this.description = description;
        this.targetUrl = targetUrl;
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
        this.attemptCount = attemptCount;
        this.responseCode = responseCode;
        this.handledNote = handledNote;
        this.handledResult = handledResult;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.target = target;
        this.correlation = correlationId;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
    }

    /** What the recipient policy lets through decides whether the card is sent at all. */
    public static OutboundDelivery queue(DeliverySubject subject, List<WecomUserId> asked, RecipientPolicy policy, String title,
                                         String description, String targetUrl, UUID correlationId, Instant now) {
        if (asked.isEmpty()) {
            return new OutboundDelivery(null, subject, List.of(), List.of(), title, description, targetUrl,
                    DeliveryStatus.FAILED, 0, null, null, null, DeliveryKind.CARD, null, correlationId, null,
                    "no recipient with a WeCom user id", 0, 0);
        }
        RecipientPolicy.Decision decision = policy.decide(asked);
        if (decision.allowed().isEmpty()) {
            return new OutboundDelivery(null, subject, List.of(), decision.skipped(), title, description, targetUrl,
                    DeliveryStatus.SKIPPED, 0, null, null, null, DeliveryKind.CARD, null, correlationId, null,
                    "no recipient on the allow list", 0, 0);
        }
        return new OutboundDelivery(null, subject, decision.allowed(), decision.skipped(), title, description, targetUrl,
                DeliveryStatus.PENDING, 0, null, null, null, DeliveryKind.CARD, null, correlationId, now, null, 0, 0);
    }

    /** The replacement of an already sent card by a note, queued as a delivery of its own. */
    public static OutboundDelivery handledNoteFor(OutboundDelivery sentCard, String note, UUID correlationId, Instant now) {
        return new OutboundDelivery(null, sentCard.subject, sentCard.recipients, List.of(), sentCard.title, note,
                sentCard.targetUrl, DeliveryStatus.PENDING, 0, null, null, null, DeliveryKind.HANDLED_NOTE, sentCard.deliveryId,
                correlationId, now, null, 0, 0);
    }

    public static OutboundDelivery restore(OutboundDeliveryId deliveryId, DeliverySubject subject, List<WecomUserId> recipients,
                                           String title, String description, String targetUrl, String status, int attemptCount,
                                           @Nullable String responseCode, @Nullable String handledNote, @Nullable String handledResult,
                                           DeliveryKind kind, @Nullable UUID target, UUID correlationId, long version, long eventSequence) {
        return new OutboundDelivery(Objects.requireNonNull(deliveryId, "deliveryId"), subject, recipients, List.of(), title,
                description, targetUrl, DeliveryStatus.valueOf(status), attemptCount, responseCode, handledNote, handledResult, kind, target == null ? null : new OutboundDeliveryId(target),
                correlationId, null, null, version, eventSequence);
    }

    public @Nullable OutboundDeliveryId deliveryId() {
        return deliveryId;
    }

    public DeliverySubject subject() {
        return subject;
    }

    public List<WecomUserId> recipients() {
        return recipients;
    }

    public List<WecomUserId> skippedRecipients() {
        return skippedRecipients;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String targetUrl() {
        return targetUrl;
    }

    public DeliveryStatus status() {
        return status;
    }

    public DeliveryStatus loadedStatus() {
        return loadedStatus;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public @Nullable String responseCode() {
        return responseCode;
    }

    public @Nullable String handledNote() {
        return handledNote;
    }

    public @Nullable String handledResult() {
        return handledResult;
    }

    /** Remembers the note for a card once; a card that was already marked keeps its first note. */
    public void noteHandled(String note) {
        if (kind == DeliveryKind.CARD && handledNote == null) {
            this.handledNote = Objects.requireNonNull(note, "note");
        }
    }

    public void recordHandledResult(String result) {
        this.handledResult = Objects.requireNonNull(result, "result");
    }

    public DeliveryKind kind() {
        return kind;
    }

    /** The card a HANDLED_NOTE replaces; null for a card. */
    public @Nullable OutboundDeliveryId target() {
        return target;
    }

    public UUID correlation() {
        return correlation;
    }

    public @Nullable Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public @Nullable Instant sentAt() {
        return sentAt;
    }

    public @Nullable String lastError() {
        return lastError;
    }

    public void markSent(String code, Instant now) {
        this.status = MACHINE.require(status, DeliveryStatus.SENT, IntegrationError.PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL);
        this.responseCode = code;
        this.sentAt = now;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void scheduleRetry(Instant next, String error) {
        this.status = MACHINE.require(status, DeliveryStatus.PENDING, IntegrationError.PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL);
        this.nextAttemptAt = Objects.requireNonNull(next, "next");
        this.lastError = error;
    }

    public void deadLetter(String error) {
        this.status = MACHINE.require(status, DeliveryStatus.DEAD_LETTER, IntegrationError.PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL);
        this.nextAttemptAt = null;
        this.lastError = error;
    }

    public void skip(String reason) {
        this.status = MACHINE.require(status, DeliveryStatus.SKIPPED, IntegrationError.PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL);
        this.nextAttemptAt = null;
        this.lastError = reason;
    }
}
