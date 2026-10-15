package com.forcitis.one.platform.identity.domain;

import java.util.List;

/** The server-side sessions that have run out, with the reason, so that each one can be audited before it is removed. */
public interface ExpiredSessions {

    enum Cause {
        /** Not used for longer than the idle timeout. */
        IDLE,
        /** Older than the absolute cap, however much it was used. */
        ABSOLUTE
    }

    record Expired(String primaryKey, String principalName, Cause cause) {
    }

    List<Expired> find(long absoluteTimeoutMillis, int limit);

    int deleteSessions(List<String> primaryKeys);
}
