package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.IntegrationError;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One message to the outside. It is queued PENDING (or already SKIPPED / FAILED when nobody may receive it) and leaves PENDING
 * once: sent, skipped, or dead after the last retry; a retry keeps it PENDING. A HANDLED_NOTE delivery replaces the card of targetKey.
 * The claim number (attemptCount) guards a claimed delivery: the repository applies a change only to the claim it was made for.
 */
public final class OutboundDelivery extends AggregateRoot {
    public static final StateMachine<DeliveryStatus> MACHINE = StateMachine.of(DeliveryStatus.class)
            .allow(DeliveryStatus.PENDING, DeliveryStatus.SENT, DeliveryStatus.SKIPPED, DeliveryStatus.DEAD_LETTER, DeliveryStatus.PENDING)
            .build();
    private static final String HANDLED_SUFFIX = "#handled";

    private final OutboundDeliveryId deliveryId;
    private final String deduplicationKey;
    private final List<WecomUserId> recipients;
    private final List<WecomUserId> skippedRecipients;
    private final String title;
    private final String description;
    private final String targetUrl;
    private final DeliveryStatus loadedStatus;
    private final int attemptCount;
    private final String handledNote;
    private final DeliveryKind kind;
    private final String targetKey;
    private final UUID correlation;
    private DeliveryStatus status;
    private String responseCode;
    private Instant nextAttemptAt;
    private Instant sentAt;
    private String lastError;

    private OutboundDelivery(OutboundDeliveryId deliveryId, String deduplicationKey, List<WecomUserId> recipients,
                             List<WecomUserId> skippedRecipients, String title, String description, String targetUrl,
                             DeliveryStatus status, int attemptCount, String responseCode, String handledNote, DeliveryKind kind,
                             String targetKey, UUID correlationId, Instant nextAttemptAt, String lastError, long version, long eventSequence) {
        super(version, eventSequence);
        this.deliveryId = deliveryId;
        this.deduplicationKey = Objects.requireNonNull(deduplicationKey, "deduplicationKey");
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
        this.kind = Objects.requireNonNull(kind, "kind");
        this.targetKey = targetKey;
        this.correlation = correlationId;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
    }

    /** What the recipient policy lets through decides whether the card is sent at all. */
    public static OutboundDelivery queue(String deduplicationKey, List<WecomUserId> asked, RecipientPolicy policy, String title,
                                         String description, String targetUrl, UUID correlationId, Instant now) {
        if (asked.isEmpty()) {
            return new OutboundDelivery(null, deduplicationKey, List.of(), List.of(), title, description, targetUrl,
                    DeliveryStatus.FAILED, 0, null, null, DeliveryKind.CARD, null, correlationId, null,
                    "no recipient with a WeCom user id", 0, 0);
        }
        RecipientPolicy.Decision decision = policy.decide(asked);
        if (decision.allowed().isEmpty()) {
            return new OutboundDelivery(null, deduplicationKey, List.of(), decision.skipped(), title, description, targetUrl,
                    DeliveryStatus.SKIPPED, 0, null, null, DeliveryKind.CARD, null, correlationId, null,
                    "no recipient on the allow list", 0, 0);
        }
        return new OutboundDelivery(null, deduplicationKey, decision.allowed(), decision.skipped(), title, description, targetUrl,
                DeliveryStatus.PENDING, 0, null, null, DeliveryKind.CARD, null, correlationId, now, null, 0, 0);
    }

    /** The replacement of an already sent card by a note, queued as a delivery of its own. */
    public static OutboundDelivery handledNoteFor(OutboundDelivery sentCard, String note, UUID correlationId, Instant now) {
        return new OutboundDelivery(null, sentCard.deduplicationKey + HANDLED_SUFFIX, sentCard.recipients, List.of(), sentCard.title, note,
                sentCard.targetUrl, DeliveryStatus.PENDING, 0, null, null, DeliveryKind.HANDLED_NOTE, sentCard.deduplicationKey,
                correlationId, now, null, 0, 0);
    }

    public static OutboundDelivery restore(OutboundDeliveryId deliveryId, String deduplicationKey, List<WecomUserId> recipients,
                                           String title, String description, String targetUrl, String status, int attemptCount,
                                           String responseCode, String handledNote, DeliveryKind kind, String targetKey,
                                           UUID correlationId, long version, long eventSequence) {
        return new OutboundDelivery(Objects.requireNonNull(deliveryId, "deliveryId"), deduplicationKey, recipients, List.of(), title,
                description, targetUrl, DeliveryStatus.valueOf(status), attemptCount, responseCode, handledNote, kind, targetKey,
                correlationId, null, null, version, eventSequence);
    }

    public OutboundDeliveryId deliveryId() {
        return deliveryId;
    }

    public String deduplicationKey() {
        return deduplicationKey;
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

    public String responseCode() {
        return responseCode;
    }

    public String handledNote() {
        return handledNote;
    }

    public DeliveryKind kind() {
        return kind;
    }

    public String targetKey() {
        return targetKey;
    }

    public UUID correlation() {
        return correlation;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public String lastError() {
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
