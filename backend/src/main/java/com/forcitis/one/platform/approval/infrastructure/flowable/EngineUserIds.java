package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.UserId;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Candidates and assignees are kept in the engine as the canonical text of the account id; anything else is a broken process. */
final class EngineUserIds {

    private static final Logger log = LoggerFactory.getLogger(EngineUserIds.class);

    static final Comparator<UserId> ORDER = Comparator.comparing(id -> id.value().toString());

    private EngineUserIds() {
    }

    static String text(UserId userId) {
        return userId.value().toString();
    }

    static UserId parse(String raw, String where) {
        try {
            return UserId.parse(raw);
        } catch (IllegalArgumentException | NullPointerException broken) {
            log.error("ALERT platform.approval {} holds {}, which is not an account id: the process cannot go on", where, raw);
            throw new IllegalStateException(where + " holds a value that is not an account id", broken);
        }
    }
}
