package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;

/** A review item for the authorizations of one person was raised; it only reminds, nothing is granted or taken back. */
public record PermissionAdjustmentOpened(EventEnvelope envelope, PermissionAdjustmentId permissionAdjustmentId, UserId userId) implements DomainEvent {
}
