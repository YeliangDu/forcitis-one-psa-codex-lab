package com.forcitis.one.platform.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** lifetime is how long a browser identifier counts after the last login it was used for; it is also the cookie's Max-Age. */
@ConfigurationProperties("forcitis.platform.identity.browser-identifier")
public record BrowserIdentifierSettings(Duration lifetime) {

    public BrowserIdentifierSettings {
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("forcitis.platform.identity.browser-identifier.lifetime must be set and positive");
        }
    }
}
