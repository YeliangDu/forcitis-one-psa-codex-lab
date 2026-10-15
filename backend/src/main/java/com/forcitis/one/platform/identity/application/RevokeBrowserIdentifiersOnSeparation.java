package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.platform.identity.events.AccountSeparated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** A separated person's browsers must not skip the identity question again; delivery may repeat, the revocation is idempotent. */
@SystemActorName("platform.identity.separation")
@Component
class RevokeBrowserIdentifiersOnSeparation {

    private final RevokeBrowserIdentifiers revoke;

    RevokeBrowserIdentifiersOnSeparation(RevokeBrowserIdentifiers revoke) {
        this.revoke = revoke;
    }

    @ApplicationModuleListener
    void on(AccountSeparated event) {
        revoke.execute(event.userId());
    }
}
