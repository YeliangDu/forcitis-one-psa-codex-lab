package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.application.AccessCache;
import com.forcitis.one.platform.identity.application.SessionPrincipal;
import com.forcitis.one.platform.identity.application.SessionPrincipals;
import com.forcitis.one.platform.identity.application.SessionSettings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Every change evicts, after the commit when a transaction is running, so changes take effect at once. */
@Component
class CachedSessionPrincipals implements AccessCache {

    private final SessionPrincipals principals;
    private final Cache<Long, SessionPrincipal> cache;

    CachedSessionPrincipals(SessionPrincipals principals, SessionSettings settings, Clock clock) {
        this.principals = principals;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(settings.principalCacheTtl())
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .executor(Runnable::run)
                .maximumSize(20_000)
                .build();
    }

    SessionPrincipal get(long userId) {
        return cache.get(userId, id -> principals.resolve(new UserId(id)));
    }

    @Override
    public void evict(UserId userId) {
        afterCommit(() -> cache.invalidate(userId.value()));
    }

    @Override
    public void evictAll() {
        afterCommit(cache::invalidateAll);
    }

    private static void afterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }
}
