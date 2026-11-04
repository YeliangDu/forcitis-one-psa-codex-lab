package com.forcitis.one.platform.identity.application;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** limits: requests per client address and minute for each kind of login request; every kind must be given, none has a default. */
@ConfigurationProperties("forcitis.platform.identity.login-throttle")
public record LoginThrottleSettings(Map<String, Integer> limits) {
    static final List<String> KINDS = List.of("login-states", "wecom-login", "ticket-login", "emergency-login");

    public LoginThrottleSettings {
        limits = limits == null ? Map.of() : Map.copyOf(limits);
        for (String kind : KINDS) {
            Integer limit = limits.get(kind);
            if (limit == null || limit < 1) {
                throw new IllegalArgumentException("forcitis.platform.identity.login-throttle.limits." + kind + " must be set to at least 1");
            }
        }
    }
}
