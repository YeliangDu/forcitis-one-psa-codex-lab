package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoundNumber;

/** A round of approval began, the first one or the one after a return, rejection or cancellation. */
public record ApprovalRoundStarted(EventEnvelope envelope, DocumentReference document, ProcessKey processKey,
                                   ApprovalInstanceId instanceId, RoundNumber round, UserId initiator) implements DomainEvent {
}
