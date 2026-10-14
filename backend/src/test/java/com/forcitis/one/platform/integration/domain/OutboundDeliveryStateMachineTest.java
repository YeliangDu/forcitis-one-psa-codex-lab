package com.forcitis.one.platform.integration.domain;

import static com.forcitis.one.platform.integration.domain.DeliveryStatus.DEAD_LETTER;
import static com.forcitis.one.platform.integration.domain.DeliveryStatus.FAILED;
import static com.forcitis.one.platform.integration.domain.DeliveryStatus.PENDING;
import static com.forcitis.one.platform.integration.domain.DeliveryStatus.SENT;
import static com.forcitis.one.platform.integration.domain.DeliveryStatus.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.WecomUserId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboundDeliveryStateMachineTest {
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    static final WecomUserId ALICE = new WecomUserId("alice");
    static final WecomUserId BOB = new WecomUserId("bob");

    static OutboundDelivery at(DeliveryStatus status) {
        return OutboundDelivery.restore(new OutboundDeliveryId(3), "approval-task:1", List.of(ALICE), "t", "d", "https://one.example.test/x",
                status.name(), 2, null, null, DeliveryKind.CARD, null, UUID.randomUUID(), 5, 5);
    }

    @Test
    @DisplayName("markSent, scheduleRetry, deadLetter and skip are tried from every status: all four leave only PENDING, and a retry keeps it PENDING")
    void exhaustiveTransitions() {
        new TransitionTable<DeliveryStatus, OutboundDelivery>(DeliveryStatus.class, OutboundDeliveryStateMachineTest::at, OutboundDelivery::status)
                .operation("markSent", d -> d.markSent("rc-1", NOW), PENDING, SENT)
                .operation("scheduleRetry", d -> d.scheduleRetry(NOW.plusSeconds(30), "down"), PENDING, PENDING)
                .operation("deadLetter", d -> d.deadLetter("gave up"), PENDING, DEAD_LETTER)
                .operation("skip", d -> d.skip("no card"), PENDING, SKIPPED)
                .verify();
    }

    @Test
    @DisplayName("each transition sets the columns it owns: sent clears the error, retry sets the next attempt, dead letter and skip clear it")
    void transitionsCarryTheirFacts() {
        OutboundDelivery sent = at(PENDING);
        sent.markSent("rc-1", NOW);
        assertThat(sent.responseCode()).isEqualTo("rc-1");
        assertThat(sent.sentAt()).isEqualTo(NOW);
        assertThat(sent.nextAttemptAt()).isNull();
        assertThat(sent.lastError()).isNull();

        OutboundDelivery retry = at(PENDING);
        retry.scheduleRetry(NOW.plusSeconds(30), "down");
        assertThat(retry.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(retry.lastError()).isEqualTo("down");
        assertThat(retry.loadedStatus()).isEqualTo(PENDING);

        OutboundDelivery dead = at(PENDING);
        dead.deadLetter("gave up");
        assertThat(dead.nextAttemptAt()).isNull();
        assertThat(dead.lastError()).isEqualTo("gave up");
    }

    @Test
    @DisplayName("queueing decides the first status: no recipient FAILED, nobody on the allow list SKIPPED, otherwise PENDING with the allowed ones")
    void queueingDecidesTheFirstStatus() {
        RecipientPolicy open = new RecipientPolicy(false, Set.of());
        RecipientPolicy allowList = new RecipientPolicy(true, Set.of(ALICE));
        UUID correlation = UUID.randomUUID();

        OutboundDelivery none = OutboundDelivery.queue("k1", List.of(), open, "t", "d", "u", correlation, NOW);
        assertThat(none.status()).isEqualTo(FAILED);
        assertThat(none.lastError()).contains("no recipient");

        OutboundDelivery notAllowed = OutboundDelivery.queue("k2", List.of(BOB), allowList, "t", "d", "u", correlation, NOW);
        assertThat(notAllowed.status()).isEqualTo(SKIPPED);
        assertThat(notAllowed.skippedRecipients()).containsExactly(BOB);

        OutboundDelivery mixed = OutboundDelivery.queue("k3", List.of(ALICE, BOB), allowList, "t", "d", "u", correlation, NOW);
        assertThat(mixed.status()).isEqualTo(PENDING);
        assertThat(mixed.recipients()).containsExactly(ALICE);
        assertThat(mixed.skippedRecipients()).containsExactly(BOB);
        assertThat(mixed.nextAttemptAt()).isEqualTo(NOW);
        assertThat(mixed.correlation()).isEqualTo(correlation);
    }

    @Test
    @DisplayName("the replacement of a sent card is a PENDING delivery of kind HANDLED_NOTE, keyed after the card and pointing at it")
    void handledNote() {
        OutboundDelivery note = OutboundDelivery.handledNoteFor(at(SENT), "handled by Dave", UUID.randomUUID(), NOW);

        assertThat(note.status()).isEqualTo(PENDING);
        assertThat(note.kind()).isEqualTo(DeliveryKind.HANDLED_NOTE);
        assertThat(note.deduplicationKey()).isEqualTo("approval-task:1#handled");
        assertThat(note.targetKey()).isEqualTo("approval-task:1");
        assertThat(note.description()).isEqualTo("handled by Dave");
    }
}
