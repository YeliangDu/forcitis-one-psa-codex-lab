package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;

/**
 * roleVersionId: the version current after the change (a change that left the definition as it was names the version it kept).
 * Such a change opens no version and therefore writes no audit entry: nothing changed, nothing is recorded.
 */
@NotAudited(reason = "role-version.opened/closed 同事务直写（A-6）")
public record RoleRevised(EventEnvelope envelope, RoleId roleId, RoleVersionId roleVersionId) implements DomainEvent {
}
