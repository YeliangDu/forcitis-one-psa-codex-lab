package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;

/** roleVersionId: the first version of the role, opened with it (ADR-0034 decision 1 item 2). */
@NotAudited(reason = "role-version.opened/closed 同事务直写（A-6）")
public record RoleCreated(EventEnvelope envelope, RoleId roleId, RoleVersionId roleVersionId) implements DomainEvent {
}
