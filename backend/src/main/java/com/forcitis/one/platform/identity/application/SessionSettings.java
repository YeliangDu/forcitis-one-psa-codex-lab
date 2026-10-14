package com.forcitis.one.platform.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** principalCacheTtl is capped at 60 s by the architecture. */
@ConfigurationProperties("forcitis.platform.identity.session")
public record SessionSettings(@DefaultValue("10h") Duration idleTimeout, @DefaultValue("24h") Duration absoluteTimeout,
                              @DefaultValue("60s") Duration principalCacheTtl, @DefaultValue("true") boolean secureCookie,
                              @DefaultValue("Lax") String sameSite) {

    public SessionSettings {
        if (idleTimeout.isNegative() || idleTimeout.isZero() || absoluteTimeout.compareTo(idleTimeout) < 0) {
            throw new IllegalArgumentException("session timeouts: idle must be positive and not longer than the absolute cap");
        }
        if (!"Lax".equals(sameSite) && !"Strict".equals(sameSite)) {
            throw new IllegalArgumentException("the session cookie must be SameSite=Lax or Strict");
        }
        if (principalCacheTtl.isNegative() || principalCacheTtl.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("principal cache TTL must be between 0 and 60 seconds");
        }
    }
}
