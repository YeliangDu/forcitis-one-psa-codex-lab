package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A role change takes effect at once: the account's sessions are deleted and the cache evicted. */
@Service
public class ReplaceAccountRoles {

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final DomainEvents events;

    ReplaceAccountRoles(AccountRepository accounts, RoleRepository roles, PrivilegeCeiling ceiling, SessionRegistry sessions,
            AccessCache cache, DomainEvents events) {
        this.accounts = accounts;
        this.roles = roles;
        this.ceiling = ceiling;
        this.sessions = sessions;
        this.cache = cache;
        this.events = events;
    }

    @Transactional
    public AdminOutcome<Void> execute(long userId, Set<String> roleCodes) {
        Optional<UserAccount> found = userId == 0 ? Optional.empty() : accounts.find(new UserId(userId));
        if (found.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND);
        }
        UserAccount account = found.get();
        UserId target = account.userId();
        Set<RoleId> wanted = new HashSet<>();
        Set<String> wantedPermissions = new HashSet<>();
        boolean administrative = false;
        Set<RoleCode> alreadyHeld = accounts.findAssignedRoleCodes(target);
        for (String code : roleCodes) {
            Optional<Role> role = roles.findByCode(new RoleCode(code));
            if (role.isEmpty()) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
            }
            wanted.add(role.get().roleId());
            if (!alreadyHeld.contains(role.get().code())) {
                wantedPermissions.addAll(role.get().permissionCodes());
                administrative |= role.get().administrative();
            }
        }
        if (!ceiling.mayGrant(wantedPermissions, administrative)) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
        }
        account.replaceRoles(wanted);
        accounts.save(account);
        events.publish(account);
        sessions.revokeAll(target);
        cache.evict(target);
        return new AdminOutcome.Done<>(null);
    }
}
