package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.domain.Digests;
import com.forcitis.one.platform.identity.domain.OneTimeCredentials;
import com.forcitis.one.platform.integration.api.WecomEntry;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues the one-time OAuth state the browser must send back with the WeCom code (I-9). Only its hash is stored. */
@Service
public class BeginWecomLogin {

    static final Duration STATE_LIFETIME = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OneTimeCredentials credentials;
    private final Clock clock;

    BeginWecomLogin(OneTimeCredentials credentials, Clock clock) {
        this.credentials = credentials;
        this.clock = clock;
    }

    @Transactional
    public String execute(WecomEntry entry) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        credentials.storeLoginState(Digests.sha256Hex(state), entry, clock.instant().plus(STATE_LIFETIME));
        return state;
    }
}
