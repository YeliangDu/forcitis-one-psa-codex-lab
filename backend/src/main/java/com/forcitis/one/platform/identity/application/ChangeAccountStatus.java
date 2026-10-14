package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enables or disables an account; disabling deletes the person's sessions at once (RULE-338). */
@Service
public class ChangeAccountStatus {

    private final AccountRepository accounts;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final CurrentUser currentUser;
    private final AuditLog audit;

    ChangeAccountStatus(AccountRepository accounts, SessionRegistry sessions, AccessCache cache, CurrentUser currentUser,
            AuditLog audit) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.cache = cache;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public AdminOutcome<Void> execute(long userId, boolean enable) {
        if (userId == 0 || accounts.find(new UserId(userId)).isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND);
        }
        UserId target = new UserId(userId);
        accounts.changeStatus(target, enable ? AccountStatus.ENABLED : AccountStatus.DISABLED);
        audit.record(new AuditEntry(enable ? "platform.identity.account.enabled" : "platform.identity.account.disabled",
                currentUser.userId(), null, "user-account:" + userId, enable ? "enabled" : "disabled"));
        if (!enable) {
            sessions.revokeAll(target);
        }
        cache.evict(target);
        return new AdminOutcome.Done<>(null);
    }
}
