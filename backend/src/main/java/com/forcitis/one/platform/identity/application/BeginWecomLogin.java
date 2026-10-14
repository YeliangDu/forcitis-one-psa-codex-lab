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

/** Only the hashes of the one-time state and of the browser binding are stored (I-9). */
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
    public LoginStateIssue execute(WecomEntry entry) {
        String state = randomToken();
        String binding = randomToken();
        credentials.storeLoginState(Digests.sha256Hex(state), Digests.sha256Hex(binding), entry, clock.instant().plus(STATE_LIFETIME));
        return new LoginStateIssue(state, binding, STATE_LIFETIME);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
