package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleKind;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteRole {

    private final RoleRepository roles;
    private final AccessCache cache;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final AccountRepository accounts;
    private final AuditLog audit;

    DeleteRole(RoleRepository roles, AccessCache cache, DomainEvents events, CurrentUser currentUser, AccountRepository accounts, AuditLog audit) {
        this.roles = roles;
        this.cache = cache;
        this.events = events;
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional
    public AdminOutcome<Void> execute(RoleId roleId, ExpectedVersion expected) {
        Optional<Role> existing = roles.findById(roleId);
        if (existing.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
        }
        expected.check(existing.get().version());
        var caller = currentUser.userId();
        if (existing.get().kind() != RoleKind.SYSTEM && accounts.findAssignedRoleCodes(caller).contains(existing.get().code())) {
            audit.recordIndependently(new AuditEntry("platform.identity.grant.refused", caller, null, "role:" + roleId.value(),
                    "nobody deletes a role they hold"));
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_OWN_ROLE_DEFINITION_FORBIDDEN);
        }
        try {
            existing.get().delete();
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
        roles.delete(existing.get());
        events.publish(existing.get());
        cache.evictAll();
        return new AdminOutcome.Done<>(null);
    }
}
