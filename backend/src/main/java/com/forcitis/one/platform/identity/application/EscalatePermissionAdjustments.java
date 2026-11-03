package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The daily escalation job (RULE-394, 0032-Q3): an open item past its escalation due time is marked escalated once; it stays open and nothing is taken back. Every overdue item is visited once per run. */
@Component
public class EscalatePermissionAdjustments {

    private static final Logger LOG = LoggerFactory.getLogger(EscalatePermissionAdjustments.class);

    private final PermissionAdjustmentRepository adjustments;
    private final PermissionAdjustmentTransitions transitions;
    private final Clock clock;

    EscalatePermissionAdjustments(PermissionAdjustmentRepository adjustments, PermissionAdjustmentTransitions transitions, Clock clock) {
        this.adjustments = adjustments;
        this.transitions = transitions;
        this.clock = clock;
    }

    public void execute() {
        try (WorkContext.Scope scope = WorkContext.enter(Uuids.timeOrdered(clock), null, "platform.identity.permission.adjustment.escalate")) {
            Instant now = clock.instant();
            PagedRun.forEachPending((after, limit) -> adjustments.overdueOpen(now, after, limit), id -> {
                try {
                    transitions.escalate(id);
                } catch (RuntimeException failure) {
                    LOG.error("ALERT P3 platform.identity item {} not escalated, retried at the next run", id.value(), failure);
                }
            });
        }
    }
}
