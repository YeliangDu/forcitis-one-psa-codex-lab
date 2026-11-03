package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The daily expiry job (ADR-0044 decision 2 item 3): upheld sensitive-role reviews at their expiry turn EXPIRED and, for a person still employed and holding
 * a sensitive role, a new review follows; items that expired while their person was held get that decision once the separation is no longer held. One batch
 * of each per run; a failed item is an alert and is found again by the next run.
 */
@Component
public class ExpirePermissionAdjustments {

    private static final Logger LOG = LoggerFactory.getLogger(ExpirePermissionAdjustments.class);
    private static final int BATCH = 100;

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
            for (PermissionAdjustmentId id : adjustments.dueToExpire(clock.instant(), BATCH)) {
                try {
                    transitions.expire(id);
                } catch (RuntimeException failure) {
                    LOG.error("ALERT platform.identity item {} not expired, retried at the next run", id.value(), failure);
                }
            }
            for (PermissionAdjustmentId id : adjustments.pendingRegeneration(BATCH)) {
                try {
                    transitions.decide(id);
                } catch (RuntimeException failure) {
                    LOG.error("ALERT platform.identity item {} not decided about its successor, retried at the next run", id.value(), failure);
                }
            }
        }
    }
}
