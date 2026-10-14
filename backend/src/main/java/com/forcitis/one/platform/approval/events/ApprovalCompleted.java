package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ProcessKey;

/** Delivered at least once through the event publication registry: listeners are idempotent per eventId. reason is empty for an approval. */
public record ApprovalCompleted(EventEnvelope envelope, DocumentReference document, ProcessKey processKey, ApprovalInstanceId instanceId,
                                int round, ApprovalOutcome outcome, String reason) implements DomainEvent {
}
