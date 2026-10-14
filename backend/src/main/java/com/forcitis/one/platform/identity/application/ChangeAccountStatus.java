package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeAccountStatus {

    private final AccountRepository accounts;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final DomainEvents events;

    ChangeAccountStatus(AccountRepository accounts, SessionRegistry sessions, AccessCache cache, DomainEvents events) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.cache = cache;
        this.events = events;
    }

    @Transactional
    public AdminOutcome<Void> execute(long userId, boolean enable) {
        Optional<UserAccount> found = userId == 0 ? Optional.empty() : accounts.find(new UserId(userId));
        if (found.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND);
        }
        UserAccount account = found.get();
        account.declareEnabled(enable);
        if (account.hasPendingEvents()) {
            accounts.save(account);
            events.publish(account);
        }
        if (!enable) {
            sessions.revokeAll(account.userId());
        }
        cache.evict(account.userId());
        return new AdminOutcome.Done<>(null);
    }
}
