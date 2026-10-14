package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleId;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final CurrentUser currentUser;
    private final AuditLog audit;

    ReplaceAccountRoles(AccountRepository accounts, RoleRepository roles, PrivilegeCeiling ceiling, SessionRegistry sessions,
            AccessCache cache, CurrentUser currentUser, AuditLog audit) {
        this.accounts = accounts;
        this.roles = roles;
        this.ceiling = ceiling;
        this.sessions = sessions;
        this.cache = cache;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public AdminOutcome<Void> execute(long userId, Set<String> roleCodes) {
        if (userId == 0 || accounts.find(new UserId(userId)).isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND);
        }
        UserId target = new UserId(userId);
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
        accounts.replaceRoles(target, wanted);
        audit.record(new AuditEntry("platform.identity.account.roles.replaced", currentUser.userId(), null, "user-account:" + userId,
                "roles=" + roleCodes.stream().sorted().collect(Collectors.joining(","))));
        sessions.revokeAll(target);
        cache.evict(target);
        return new AdminOutcome.Done<>(null);
    }
}
