package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.OrgCommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN;
import static com.forcitis.one.organization.workforce.domain.OrgCommandStatus.EFFECTIVE;
import static com.forcitis.one.organization.workforce.domain.OrgCommandStatus.IN_PROGRESS;
import static com.forcitis.one.organization.workforce.domain.OrgCommandStatus.PENDING;
import static com.forcitis.one.organization.workforce.domain.OrgCommandStatus.WITHDRAWN;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The table is what the domain methods do today: most ask the machine for their one target and do not check the source status themselves. */
class OrgStructureCommandStateMachineTest {
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    static final LocalDate TODAY = LocalDate.parse("2026-10-14");
    static final UserId BY = new UserId(TestIds.uuid(5));

    private static TransitionTable<OrgCommandStatus, OrgStructureCommand> table(OrgStructureCommandKind kind, LocalDate day) {
        return new TransitionTable<>(OrgCommandStatus.class, status -> OrgStructureCommand.restore(new OrgStructureCommandId(TestIds.uuid(1)), OrgStructureTargetKind.ORG_UNIT,
                kind, new OrgUnitId(TestIds.uuid(2)), null, day, null, null, null, null, null, null, null, null, null, null, status, 0, 0, BY, NOW.minusSeconds(60), null, 3),
                OrgStructureCommand::status);
    }

    @Test
    @DisplayName("a command that is not a merge, its day not come: withdraw, requireCountersign, completeCountersign (to PENDING) and dueEffect")
    void exhaustiveTransitionsBeforeTheDay() {
        table(OrgStructureCommandKind.SET_ATTRIBUTES, TODAY.plusDays(5))
                .operation("withdraw", c -> c.withdraw(BY, "no longer needed", NOW), PENDING, WITHDRAWN, AWAITING_ATTRIBUTION_COUNTERSIGN, WITHDRAWN)
                .operation("requireCountersign", OrgStructureCommand::requireCountersign, PENDING, AWAITING_ATTRIBUTION_COUNTERSIGN, EFFECTIVE, AWAITING_ATTRIBUTION_COUNTERSIGN)
                .operation("completeCountersign", c -> c.completeCountersign(NOW, TODAY), AWAITING_ATTRIBUTION_COUNTERSIGN, PENDING)
                .operation("dueEffect", c -> c.dueEffect(NOW), PENDING, EFFECTIVE, IN_PROGRESS, EFFECTIVE, AWAITING_ATTRIBUTION_COUNTERSIGN, EFFECTIVE)
                .verify();
    }

    @Test
    @DisplayName("a command that is not a merge, its day come: completeCountersign makes any non-final one EFFECTIVE")
    void exhaustiveTransitionsOnTheDay() {
        table(OrgStructureCommandKind.SET_ATTRIBUTES, TODAY)
                .operation("completeCountersign", c -> c.completeCountersign(NOW, TODAY), PENDING, EFFECTIVE, IN_PROGRESS, EFFECTIVE, AWAITING_ATTRIBUTION_COUNTERSIGN, EFFECTIVE)
                .verify();
    }

    @Test
    @DisplayName("a merge: withdraw only before it starts, startMerge from PENDING, completeMerge once everyone is moved, never dueEffect, and a countersign never makes it EFFECTIVE")
    void exhaustiveTransitionsOfAMerge() {
        table(OrgStructureCommandKind.MERGE, TODAY.minusDays(1))
                .operation("withdraw", c -> c.withdraw(BY, "no longer needed", NOW), PENDING, WITHDRAWN, AWAITING_ATTRIBUTION_COUNTERSIGN, WITHDRAWN)
                .operation("startMerge", c -> c.startMerge(2), PENDING, IN_PROGRESS)
                .operation("completeMerge", c -> c.completeMerge(NOW), PENDING, EFFECTIVE, IN_PROGRESS, EFFECTIVE, AWAITING_ATTRIBUTION_COUNTERSIGN, EFFECTIVE)
                .operation("dueEffect", c -> c.dueEffect(NOW))
                .operation("completeCountersign", c -> c.completeCountersign(NOW, TODAY), AWAITING_ATTRIBUTION_COUNTERSIGN, PENDING)
                .verify();
    }
}
