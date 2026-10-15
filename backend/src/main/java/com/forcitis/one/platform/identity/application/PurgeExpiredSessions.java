package com.forcitis.one.platform.identity.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.domain.ExpiredSessions;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Replaces the framework's silent clean-up: each session that ends by running out leaves an audit entry that says whether it was idle or too old; SessionGuardFilter judges the absolute cap by login time and stays authoritative, this is the backstop. */
@Service
public class PurgeExpiredSessions {
    private static final Logger LOG = LoggerFactory.getLogger(PurgeExpiredSessions.class);
    private static final int BATCH = 500;

    private final ExpiredSessions sessions;
    private final SessionSettings settings;
    private final AuditLog audit;
    private final MeterRegistry meters;

    PurgeExpiredSessions(ExpiredSessions sessions, SessionSettings settings, AuditLog audit, MeterRegistry meters) {
        this.meters = meters;
        this.sessions = sessions;
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional
    public int execute() {
        int removed = 0;
        List<ExpiredSessions.Expired> batch;
        do {
            batch = sessions.find(settings.absoluteTimeout().toMillis(), BATCH);
            for (ExpiredSessions.Expired expired : batch) {
                UserId user = accountOf(expired.principalName());
                audit.record(new AuditEntry("platform.identity.session.expired", user, null,
                        user == null ? "session:unknown" : "user-account:" + user.value(), "cause=" + expired.cause()));
            }
            removed += sessions.deleteSessions(batch.stream().map(ExpiredSessions.Expired::primaryKey).toList());
        } while (batch.size() == BATCH);
        return removed;
    }

    /** A session that names no account, or names it in another form, is still removed; its expiry is audited without an actor. */
    private UserId accountOf(String principalName) {
        if (principalName == null) {
            return null;
        }
        try {
            return UserId.parse(principalName);
        } catch (IllegalArgumentException broken) {
            LOG.warn("platform.identity an expired session names a principal that is not an account id; audited without an actor");
            meters.counter("forcitis.platform.identity.session.principal.broken", "where", "purge").increment();
            return null;
        }
    }
}
