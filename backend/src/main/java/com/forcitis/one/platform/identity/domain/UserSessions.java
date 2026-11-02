package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;

/** The server-side sessions of one account, removed in the caller's transaction (Spring Session's own deletion commits on its own, so it cannot be used where the removal must roll back with other writes). */
public interface UserSessions {

    int deleteAllOf(UserId userId);
}
