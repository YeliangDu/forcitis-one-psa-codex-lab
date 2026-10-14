package com.forcitis.one.platform.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Single sign-on from the company portal (RULE-321): off by default; legacyLoginUrl is the "back to the old login" link;
 * replayWindow is how long a used ticket stays remembered. How the portal is asked is platform.integration's business.
 */
@ConfigurationProperties("forcitis.platform.identity.sso")
public record SsoSettings(@DefaultValue("false") boolean enabled, @DefaultValue("") String legacyLoginUrl,
                          @DefaultValue("7d") Duration replayWindow) {
}
