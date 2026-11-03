package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.organization.workforce.api.AttributionCountersignStatus;
import org.jspecify.annotations.Nullable;

/** ADR-0073 item 3.6: a countersign request is open until a finance head approves or rejects it, or it lapses because its command left the wait. */
public enum CountersignStatus {
    OPEN,
    APPROVED,
    REJECTED,
    LAPSED;

    public static final StateMachine<CountersignStatus> MACHINE = StateMachine.of(CountersignStatus.class)
            .allow(OPEN, APPROVED, REJECTED, LAPSED)
            .build();

    /** A request with no decision yet is open; otherwise the decision kind names the status. */
    public static CountersignStatus ofDecision(@Nullable String decisionKind) {
        return decisionKind == null ? OPEN : valueOf(decisionKind);
    }

    /** The status a reader asks for; none asked means the open ones. */
    public static CountersignStatus requested(@Nullable AttributionCountersignStatus status) {
        return status == null ? OPEN : valueOf(status.name());
    }

    public AttributionCountersignStatus toApi() {
        return AttributionCountersignStatus.valueOf(name());
    }
}
