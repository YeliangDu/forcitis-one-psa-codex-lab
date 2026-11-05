package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN;
import static com.forcitis.one.organization.workforce.domain.CommandStatus.EFFECTIVE;
import static com.forcitis.one.organization.workforce.domain.CommandStatus.HELD;
import static com.forcitis.one.organization.workforce.domain.CommandStatus.PENDING;
import static com.forcitis.one.organization.workforce.domain.CommandStatus.WITHDRAWN;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The table is what the domain methods do today: each asks the machine for its one target and does not check the source status itself. */
class PersonnelCommandStateMachineTest {
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    static final LocalDate TODAY = LocalDate.parse("2026-10-14");
    static final UserId BY = new UserId(TestIds.uuid(5));

    static PersonnelCommand at(CommandStatus status, LocalDate effectiveDate) {
        return PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(1)), new EmployeeId(TestIds.uuid(2)), PersonnelCommandKind.SEPARATE, effectiveDate, status,
                null, null, null, null, null, null, null, null, null, null, BY, NOW.minusSeconds(60), null, 3, 0);
    }

    private static TransitionTable<CommandStatus, PersonnelCommand> table(LocalDate effectiveDate) {
        Function<CommandStatus, PersonnelCommand> at = status -> at(status, effectiveDate);
        return new TransitionTable<>(CommandStatus.class, at, PersonnelCommand::status);
    }

    @Test
    @DisplayName("a command whose day has not come: completeCountersign and releaseHeld make it PENDING, holdOnDueDay holds a pending one, withdraw takes any open one back, dueEffect refuses all")
    void exhaustiveTransitionsBeforeTheDay() {
        table(TODAY.plusDays(5))
                .operation("completeCountersign", c -> c.completeCountersign(NOW, TODAY), HELD, PENDING, AWAITING_ATTRIBUTION_COUNTERSIGN, PENDING)
                .operation("releaseHeld", c -> c.releaseHeld(NOW, TODAY), HELD, PENDING, AWAITING_ATTRIBUTION_COUNTERSIGN, PENDING)
                .operation("holdOnDueDay", PersonnelCommand::holdOnDueDay, PENDING, HELD)
                .operation("dueEffect", c -> c.dueEffect(NOW, TODAY))
                .operation("withdraw", c -> c.withdraw(BY, "no longer needed", NOW), PENDING, WITHDRAWN, HELD, WITHDRAWN, AWAITING_ATTRIBUTION_COUNTERSIGN, WITHDRAWN)
                .verify();
    }

    @Test
    @DisplayName("a command whose day has come: completeCountersign, releaseHeld and dueEffect make any open one EFFECTIVE")
    void exhaustiveTransitionsOnTheDay() {
        table(TODAY)
                .operation("completeCountersign", c -> c.completeCountersign(NOW, TODAY), PENDING, EFFECTIVE, HELD, EFFECTIVE, AWAITING_ATTRIBUTION_COUNTERSIGN, EFFECTIVE)
                .operation("releaseHeld", c -> c.releaseHeld(NOW, TODAY), PENDING, EFFECTIVE, HELD, EFFECTIVE, AWAITING_ATTRIBUTION_COUNTERSIGN, EFFECTIVE)
                .operation("dueEffect", c -> c.dueEffect(NOW, TODAY), PENDING, EFFECTIVE, HELD, EFFECTIVE, AWAITING_ATTRIBUTION_COUNTERSIGN, EFFECTIVE)
                .verify();
    }
}
