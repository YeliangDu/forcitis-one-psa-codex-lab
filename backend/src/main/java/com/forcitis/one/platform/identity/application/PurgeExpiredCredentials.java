package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.domain.OneTimeCredentials;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurgeExpiredCredentials {

    private final OneTimeCredentials credentials;
    private final BrowserIdentity browserIdentity;
    private final Clock clock;

    PurgeExpiredCredentials(OneTimeCredentials credentials, BrowserIdentity browserIdentity, Clock clock) {
        this.credentials = credentials;
        this.browserIdentity = browserIdentity;
        this.clock = clock;
    }

    @Transactional
    public int execute() {
        return credentials.purgeExpired(clock.instant()) + browserIdentity.purge();
    }
}
