package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.identity.api.AccountOpeningRoute;
import com.forcitis.one.platform.identity.api.RoleId;
import java.util.List;

/** roleIds are the roles the account holds from the start; openingRoute is the way it was opened. */
public record AccountOpened(EventEnvelope envelope, UserId userId, List<RoleId> roleIds, AccountOpeningRoute openingRoute) implements DomainEvent {
    public AccountOpened {
        roleIds = List.copyOf(roleIds);
    }
}
