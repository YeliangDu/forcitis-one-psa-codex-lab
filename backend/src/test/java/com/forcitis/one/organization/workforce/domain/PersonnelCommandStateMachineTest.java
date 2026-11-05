package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN;
import static com.forcitis.one.organization.workforce.domain.CommandStatus.EFFECTIVE;
import static com.forcitis.one.organization.workforce.domain.CommandStatus.HELD;
import static com.forcitis.one.organization.workforce.domain.CommandStatus.PENDING;
import static com.forcitis.one.organization.workforce.domain.CommandStatus.WITHDRAWN;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Each method leaves from the one status its design names: completeCountersign from AWAITING (ADR-0073 3.6), releaseHeld from HELD (ADR-0068 2.3), dueEffect from PENDING (ADR-0068 item 1.4 and 8). */
class PersonnelCommandStateMachineTest {
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    static final LocalDate TODAY = LocalDate.parse("2026-10-14");
    static final UserId BY = new UserId(TestIds.uuid(5));

    static PersonnelCommand at(CommandStatus status, LocalDate effectiveDate) {
        return at(status, effectiveDate, 3);
    }

    static PersonnelCommand at(CommandStatus status, LocalDate effectiveDate, long version) {
        return PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(1)), new EmployeeId(TestIds.uuid(2)), PersonnelCommandKind.SEPARATE, effectiveDate, status,
                null, null, null, null, null, null, null, null, null, null, BY, NOW.minusSeconds(60), null, version, 0);
    }

    private static TransitionTable<CommandStatus, PersonnelCommand> table(LocalDate effectiveDate) {
        Function<CommandStatus, PersonnelCommand> at = status -> at(status, effectiveDate);
        return new TransitionTable<>(CommandStatus.class, at, PersonnelCommand::status);
    }

    @Test
    @DisplayName("requireCountersign is for a command being entered (never stored, version 0, ADR-0073 3.6): PENDING or EFFECTIVE start AWAITING; a stored command refuses it from every status")
    void requireCountersignOnlyAtEntry() {
        LegalEntityId previous = new LegalEntityId(TestIds.uuid(7));
        LegalEntityId next = new LegalEntityId(TestIds.uuid(8));
        Function<Long, TransitionTable<CommandStatus, PersonnelCommand>> at = version ->
                new TransitionTable<>(CommandStatus.class, status -> at(status, TODAY.plusDays(5), version), PersonnelCommand::status);
        at.apply(0L).operation("requireCountersign", c -> c.requireCountersign(previous, next), PENDING, AWAITING_ATTRIBUTION_COUNTERSIGN, EFFECTIVE, AWAITING_ATTRIBUTION_COUNTERSIGN).verify();
        at.apply(3L).operation("requireCountersign", c -> c.requireCountersign(previous, next)).verify();
    }

    @Test
    @DisplayName("a command whose day has not come: completeCountersign (from AWAITING) and releaseHeld (from HELD) make it PENDING, holdOnDueDay holds a pending one, withdraw takes any open one back, dueEffect refuses all")
    void exhaustiveTransitionsBeforeTheDay() {
        table(TODAY.plusDays(5))
                .operation("completeCountersign", c -> c.completeCountersign(NOW, TODAY), AWAITING_ATTRIBUTION_COUNTERSIGN, PENDING)
                .operation("releaseHeld", c -> c.releaseHeld(NOW, TODAY), HELD, PENDING)
                .operation("holdOnDueDay", PersonnelCommand::holdOnDueDay, PENDING, HELD)
                .operation("dueEffect", c -> c.dueEffect(NOW, TODAY))
                .operation("withdraw", c -> c.withdraw(BY, "no longer needed", NOW), PENDING, WITHDRAWN, HELD, WITHDRAWN, AWAITING_ATTRIBUTION_COUNTERSIGN, WITHDRAWN)
                .verify();
    }

    @Test
    @DisplayName("a command whose day has come: completeCountersign makes an AWAITING one, releaseHeld a HELD one and dueEffect a PENDING one EFFECTIVE")
    void exhaustiveTransitionsOnTheDay() {
        table(TODAY)
                .operation("completeCountersign", c -> c.completeCountersign(NOW, TODAY), AWAITING_ATTRIBUTION_COUNTERSIGN, EFFECTIVE)
                .operation("releaseHeld", c -> c.releaseHeld(NOW, TODAY), HELD, EFFECTIVE)
                .operation("dueEffect", c -> c.dueEffect(NOW, TODAY), PENDING, EFFECTIVE)
                .verify();
    }
}
