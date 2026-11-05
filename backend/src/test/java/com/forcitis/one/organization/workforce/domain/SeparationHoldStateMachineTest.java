package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.HoldStatus.HELD;
import static com.forcitis.one.organization.workforce.domain.HoldStatus.RELEASED;
import static com.forcitis.one.organization.workforce.domain.HoldStatus.WITHDRAWN;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeparationHoldStateMachineTest {
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");

    static SeparationHold at(HoldStatus status) {
        boolean released = status == RELEASED;
        return SeparationHold.restore(new SeparationHoldId(TestIds.uuid(1)), new PersonnelCommandId(TestIds.uuid(2)), new EmployeeId(TestIds.uuid(3)), status, 4,
                released ? NOW : null, null, released ? "checked" : null, released ? ReleaseChannelKind.OPERATIONS_COMMAND : null);
    }

    @Test
    @DisplayName("withdraw, release and releaseByOperations are tried from every status: each leaves only HELD")
    void exhaustiveTransitions() {
        new TransitionTable<HoldStatus, SeparationHold>(HoldStatus.class, SeparationHoldStateMachineTest::at, SeparationHold::status)
                .operation("withdraw", SeparationHold::withdraw, HELD, WITHDRAWN)
                .operation("release", h -> h.release(new UserId(TestIds.uuid(5)), "checked", NOW), HELD, RELEASED)
                .operation("releaseByOperations", h -> h.releaseByOperations("checked", NOW), HELD, RELEASED)
                .verify();
    }
}
