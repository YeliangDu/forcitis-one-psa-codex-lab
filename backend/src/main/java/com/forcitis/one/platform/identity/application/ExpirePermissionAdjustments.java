package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The daily expiry job (ADR-0044 decision 2 item 3): upheld sensitive-role reviews at their expiry turn EXPIRED and, for a person still employed and holding
 * a sensitive role, a new review follows; items that expired while their person was held get that decision once the separation is no longer held. Every pending item is
 * visited once per run; a failed item is an alert and is found again by the next run.
 */
@Component
public class ExpirePermissionAdjustments {

    private static final Logger LOG = LoggerFactory.getLogger(ExpirePermissionAdjustments.class);

    private final PermissionAdjustmentRepository adjustments;
    private final PermissionAdjustmentTransitions transitions;
    private final Clock clock;

    ExpirePermissionAdjustments(PermissionAdjustmentRepository adjustments, PermissionAdjustmentTransitions transitions, Clock clock) {
        this.adjustments = adjustments;
        this.transitions = transitions;
        this.clock = clock;
    }

    public void execute() {
        try (WorkContext.Scope scope = WorkContext.enter(Uuids.timeOrdered(clock), null, "platform.identity.permission.adjustment.expire")) {
            Instant now = clock.instant();
            PagedRun.forEachPending((after, limit) -> adjustments.dueToExpire(now, after, limit), id -> {
                try {
                    transitions.expire(id);
                } catch (RuntimeException failure) {
                    LOG.error("ALERT P3 platform.identity item {} not expired, retried at the next run", id.value(), failure);
                }
            });
            PagedRun.forEachPending(adjustments::pendingRegeneration, id -> {
                try {
                    transitions.decide(id);
                } catch (RuntimeException failure) {
                    LOG.error("ALERT P3 platform.identity item {} not decided about its successor, retried at the next run", id.value(), failure);
                }
            });
        }
    }
}
