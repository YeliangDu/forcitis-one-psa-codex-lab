package com.forcitis.one.platform.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** replayWindow is how long a used ticket stays remembered. */
@ConfigurationProperties("forcitis.platform.identity.sso")
public record SsoSettings(@DefaultValue("false") boolean enabled, @DefaultValue("") String legacyLoginUrl,
                          @DefaultValue("7d") Duration replayWindow) {
}
