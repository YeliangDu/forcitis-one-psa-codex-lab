package com.forcitis.one.platform.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Seeyon SSO (RULE-321): off by default; baseUrl from SEEYON_OA_BASE_URL; legacyLoginUrl is the "back to the old login" link. */
@ConfigurationProperties("forcitis.platform.identity.seeyon")
public record SeeyonSettings(@DefaultValue("false") boolean enabled, @DefaultValue("") String baseUrl,
                             @DefaultValue("5s") Duration timeout, @DefaultValue("") String legacyLoginUrl,
                             @DefaultValue("7d") Duration replayWindow) {
}
