package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The daily escalation job (RULE-394, 0032-Q3): an open item past its escalation due time is marked escalated once; it stays open and nothing is taken back. One batch per run. */
@Component
public class EscalatePermissionAdjustments {

    private static final Logger LOG = LoggerFactory.getLogger(EscalatePermissionAdjustments.class);
    private static final int BATCH = 100;

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
            for (PermissionAdjustmentId id : adjustments.overdueOpen(clock.instant(), BATCH)) {
                try {
                    transitions.escalate(id);
                } catch (RuntimeException failure) {
                    LOG.error("ALERT platform.identity item {} not escalated, retried at the next run", id.value(), failure);
                }
            }
        }
    }
}
