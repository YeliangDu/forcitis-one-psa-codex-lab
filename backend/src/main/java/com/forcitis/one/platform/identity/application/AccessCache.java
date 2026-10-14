package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;

/** A change evicts so it takes effect at once; inside a transaction the eviction happens after the commit. */
public interface AccessCache {

    void evict(UserId userId);

    void evictAll();
}
