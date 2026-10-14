package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;

/**
 * Port to the short-lived (at most 60 s) cache of account state and permissions. A change evicts so it takes effect at
 * once; when called inside a transaction the eviction happens after the commit.
 */
public interface AccessCache {

    void evict(UserId userId);

    void evictAll();
}
