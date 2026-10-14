package com.forcitis.one.platform.identity.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Per client address and kind of request, within a window of one minute; the counters live in this node only. Everyone in the office
 * may share one egress address, so the limits of the kinds a whole office uses at the start of the day are set far higher than the
 * limits of the kinds only one person uses.
 */
@Component
public class LoginThrottle {
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();
    private final LoginThrottleSettings settings;

    LoginThrottle(LoginThrottleSettings settings) {
        this.settings = settings;
    }

    public boolean allows(String kind, String address) {
        Integer limit = settings.limits().get(kind);
        if (limit == null) {
            throw new IllegalArgumentException("no login throttle limit for " + kind);
        }
        return counters.get(kind + "|" + address, key -> new AtomicInteger()).incrementAndGet() <= limit;
    }
}
