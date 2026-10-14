package com.forcitis.one.platform.idempotency.application;

import com.forcitis.one.kernel.IdempotentResponses;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurgeExpiredResponses {
    private final IdempotentResponses responses;
    private final Clock clock;

    PurgeExpiredResponses(IdempotentResponses responses, Clock clock) {
        this.responses = responses;
        this.clock = clock;
    }

    @Transactional
    public int execute() {
        return responses.purgeExpired(clock.instant());
    }
}
