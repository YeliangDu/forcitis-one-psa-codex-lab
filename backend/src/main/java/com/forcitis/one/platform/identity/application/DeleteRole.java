package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.kernel.CurrentUser;
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

/**
 * Deleting a role closes its last version and keeps the row, so the code is never used again (ADR-0034 decision 1 item 5, 0034-Q3); the
 * role is then neither listed nor found. A role an account still holds is not deleted.
 */
@Service
public class DeleteRole {

    /** The fixed closure reason (ADR-0034-IMPL ruling A-5, card C4: a fixed phrase, the deleting account is in closed_by). */
    static final String REASON = "role management: role deleted";

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
            audit.recordIndependently(new AuditEntry("platform.identity.grant.refused", caller, null, AuditSubject.of(AuditSubjectKind.ROLE, roleId.value()),
                    "nobody deletes a role they hold"));
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_OWN_ROLE_DEFINITION_FORBIDDEN);
        }
        try {
            existing.get().delete();
        } catch (RoleRuleException refused) {
            return new AdminOutcome.Refused<>(RoleErrors.of(refused.violation()));
        }
        roles.delete(existing.get(), caller, REASON);
        events.publish(existing.get());
        cache.evictAll();
        return new AdminOutcome.Done<>(null);
    }
}
