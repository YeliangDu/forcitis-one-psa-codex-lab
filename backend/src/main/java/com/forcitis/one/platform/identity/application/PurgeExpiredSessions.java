package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.domain.ExpiredSessions;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Replaces the framework's silent clean-up: each session that ends by running out leaves an audit entry that says whether it was idle or too old. */
@Service
public class PurgeExpiredSessions {
    private static final int BATCH = 500;

    private final ExpiredSessions sessions;
    private final SessionSettings settings;
    private final AuditLog audit;
    private final Clock clock;

    PurgeExpiredSessions(ExpiredSessions sessions, SessionSettings settings, AuditLog audit, Clock clock) {
        this.sessions = sessions;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public int execute() {
        int removed = 0;
        List<ExpiredSessions.Expired> batch;
        do {
            batch = sessions.find(clock.millis(), settings.absoluteTimeout().toMillis(), BATCH);
            for (ExpiredSessions.Expired expired : batch) {
                UserId user = expired.principalName() == null ? null : new UserId(Long.parseLong(expired.principalName()));
                audit.record(new AuditEntry("platform.identity.session.expired", user, null,
                        user == null ? "session:unknown" : "user-account:" + user.value(), "cause=" + expired.cause()));
            }
            removed += sessions.deleteSessions(batch.stream().map(ExpiredSessions.Expired::primaryKey).toList());
        } while (batch.size() == BATCH);
        return removed;
    }
}
