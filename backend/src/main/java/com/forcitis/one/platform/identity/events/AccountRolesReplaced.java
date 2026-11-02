package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.api.RoleId;
import java.util.List;

/** roleIds is the complete set the account holds after the change; the grant ids are the rows the change opened and closed. */
public record AccountRolesReplaced(EventEnvelope envelope, UserId userId, List<RoleId> roleIds, List<AccountRoleGrantId> openedGrantIds,
                                 List<AccountRoleGrantId> closedGrantIds) implements DomainEvent {
    public AccountRolesReplaced {
        roleIds = List.copyOf(roleIds);
        openedGrantIds = List.copyOf(openedGrantIds);
        closedGrantIds = List.copyOf(closedGrantIds);
    }
}
