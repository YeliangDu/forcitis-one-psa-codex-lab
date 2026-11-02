package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.NotAudited;

@NotAudited(reason = "account.disabled 同事务直写：通知卡要用该条审计条目的 ID（#732）")
public record AccountDisabled(EventEnvelope envelope, UserId userId) implements DomainEvent {
}
