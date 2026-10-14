package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeAccountStatus {

    private final AccountRepository accounts;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final DomainEvents events;
    private final RevokeBrowserIdentifiers revokeBrowserIdentifiers;

    ChangeAccountStatus(AccountRepository accounts, SessionRegistry sessions, AccessCache cache, DomainEvents events,
            RevokeBrowserIdentifiers revokeBrowserIdentifiers) {
        this.revokeBrowserIdentifiers = revokeBrowserIdentifiers;
        this.accounts = accounts;
        this.sessions = sessions;
        this.cache = cache;
        this.events = events;
    }

    @Transactional
    public void execute(long userId, boolean enable) {
        UserAccount account = userId == 0 ? null : accounts.find(new UserId(userId)).orElse(null);
        if (account == null) {
            throw IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND.refusal("no account " + userId);
        }
        account.declareEnabled(enable);
        if (account.hasPendingEvents()) {
            accounts.save(account);
            events.publish(account);
        }
        if (!enable) {
            sessions.revokeAll(account.userId());
            revokeBrowserIdentifiers.execute(account.userId());
        }
        cache.evict(account.userId());
    }
}
