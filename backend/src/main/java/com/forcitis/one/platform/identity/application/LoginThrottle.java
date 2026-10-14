package com.forcitis.one.platform.identity.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Per client address and kind of request, within a window of one minute; the counters live in this node only. */
@Component
public class LoginThrottle {
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();
    private final int maxPerMinute;

    LoginThrottle(@Value("${forcitis.platform.identity.login-throttle.max-per-minute}") int maxPerMinute) {
        if (maxPerMinute < 1) {
            throw new IllegalArgumentException("login throttle must allow at least one request per minute");
        }
        this.maxPerMinute = maxPerMinute;
    }

    public boolean allows(String kind, String address) {
        return counters.get(kind + "|" + address, key -> new AtomicInteger()).incrementAndGet() <= maxPerMinute;
    }
}
