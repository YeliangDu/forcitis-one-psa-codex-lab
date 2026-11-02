package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.domain.GrantHistory;
import com.forcitis.one.platform.identity.domain.GrantHistory.HeldRole;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a person held at a past moment, with the role definition of that moment (ADR-0034 decision 4 items 3 to 5): read only. A moment before every
 * record of the person is not "held nothing": it fails and raises an ALERT, because the history cannot say.
 */
@Service
public class AuthorizationHistory {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationHistory.class);

    private final GrantHistory history;

    AuthorizationHistory(GrantHistory history) {
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<HeldRole> heldAt(UserId user, Instant moment) {
        return history.heldAt(user, moment).orElseThrow(() -> {
            LOG.error("ALERT platform.identity authorization history of user {} asked for {}, before every record of the person", user.value(), moment);
            return new IllegalArgumentException("no grant record of the account exists at or before " + moment);
        });
    }
}
