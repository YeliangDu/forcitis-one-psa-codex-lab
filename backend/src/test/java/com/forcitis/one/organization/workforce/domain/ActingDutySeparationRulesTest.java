package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ActingDutySeparationRulesTest {
    static final LocalDate DAY = ActingDutyRulesTest.DAY.plusDays(2);
    static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

    static PersonnelCommand separation(CommandStatus status) {
        return PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(50)), ActingDutyRulesTest.EMPLOYEE,
                PersonnelCommandKind.SEPARATE, DAY, status, null, null, null, null, null, null, null, null, null, null,
                new UserId(TestIds.uuid(4)), NOW, status == CommandStatus.EFFECTIVE ? NOW : null, 0, 0);
    }

    static PersonnelHistory history(List<PersonnelCommand> commands) {
        return new PersonnelHistory(List.of(
                new VersionRow(TestIds.uuid(51), DAY.minusDays(5), DAY, Map.of(CommandEffects.EMPLOYMENT, "EMPLOYED"), ChangeSource.MIGRATION, null, false),
                new VersionRow(TestIds.uuid(52), DAY, null, Map.of(CommandEffects.EMPLOYMENT, "SEPARATED"), ChangeSource.MIGRATION, null, false)),
                List.of(), List.of(), List.of(), commands, Map.of());
    }

    @Test
    @DisplayName("#1566 a pending separation permits an acting start exactly on its effective day")
    void allowsStartOnPendingSeparationDay() {
        PersonnelCommand start = ActingDutyRulesTest.start(DAY, DAY.plusDays(5), ActingDutyRulesTest.UNIT, null, null, "coverage", 53);
        assertThatCode(() -> PersonnelCommandRules.check(start, history(List.of(separation(CommandStatus.PENDING)))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = PersonnelCommandKind.class, names = {"ACTING_START", "ACTING_END", "ACTING_EXTEND"})
    @DisplayName("#1566 acting commands dated later do not block separation entry")
    void actingCommandsDoNotBlockSeparation(PersonnelCommandKind kind) {
        PersonnelCommand later = PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(54)), ActingDutyRulesTest.EMPLOYEE,
                kind, DAY.plusDays(3), CommandStatus.PENDING, null, null, null, null, null, null, null, null, null, null,
                new UserId(TestIds.uuid(4)), NOW, null, 0, 0);
        PersonnelHistory employed = new PersonnelHistory(List.of(new VersionRow(TestIds.uuid(55), DAY.minusDays(5), null,
                Map.of(CommandEffects.EMPLOYMENT, "EMPLOYED"), ChangeSource.MIGRATION, null, false)), List.of(), List.of(), List.of(), List.of(later), Map.of());
        assertThatCode(() -> PersonnelCommandRules.check(separation(CommandStatus.PENDING), employed)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = CommandStatus.class, names = {"PENDING", "EFFECTIVE"})
    @DisplayName("#1566 an acting start after a pending or effective separation is refused")
    void refusesStartAfterSeparation(CommandStatus status) {
        PersonnelCommand start = ActingDutyRulesTest.start(DAY.plusDays(1), DAY.plusDays(5), ActingDutyRulesTest.UNIT, null, null, "coverage", 53);
        assertThatThrownBy(() -> PersonnelCommandRules.check(start, history(List.of(separation(status)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("only a reinstatement");
    }

    @Test
    @DisplayName("#1566 the same-day employment exception does not admit a start after effective separation or an ordinary command")
    void sameDayExceptionIsLimitedToPendingActingStart() {
        PersonnelCommand start = ActingDutyRulesTest.start(DAY, DAY.plusDays(5), ActingDutyRulesTest.UNIT, null, null, "coverage", 53);
        assertThatThrownBy(() -> PersonnelCommandRules.check(start, history(List.of(separation(CommandStatus.EFFECTIVE)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("not employed");
        PersonnelCommand ordinary = PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(56)), ActingDutyRulesTest.EMPLOYEE,
                PersonnelCommandKind.REGULARIZE, DAY, CommandStatus.PENDING, null, null, null, null, null, null, null, null, null, null,
                new UserId(TestIds.uuid(4)), NOW, null, 0, 0);
        assertThatThrownBy(() -> PersonnelCommandRules.check(ordinary, history(List.of(separation(CommandStatus.PENDING)))))
                .isInstanceOf(DomainException.class);
        PersonnelHistory employed = new PersonnelHistory(List.of(new VersionRow(TestIds.uuid(55), DAY.minusDays(5), null,
                Map.of(CommandEffects.EMPLOYMENT, "EMPLOYED"), ChangeSource.MIGRATION, null, false)), List.of(), List.of(), List.of(),
                List.of(PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(57)), ActingDutyRulesTest.EMPLOYEE,
                        PersonnelCommandKind.REGULARIZE, DAY.plusDays(2), CommandStatus.PENDING, null, null, null, null, null, null, null, null, null, null,
                        new UserId(TestIds.uuid(4)), NOW, null, 0, 0)), Map.of());
        assertThatThrownBy(() -> PersonnelCommandRules.check(separation(CommandStatus.PENDING), employed))
                .isInstanceOf(DomainException.class).hasMessageContaining("correct those commands first");
    }

    static ActingDuty openDuty(LocalDate startsOn) {
        return new ActingDuty(new com.forcitis.one.organization.workforce.api.ActingDutyId(TestIds.uuid(60)), ActingDutyRulesTest.EMPLOYEE,
                new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(TestIds.uuid(61)), new com.forcitis.one.organization.workforce.api.OrgUnitId(TestIds.uuid(62)),
                null, null, startsOn, startsOn.plusDays(10), null, "coverage", new PersonnelCommandId(TestIds.uuid(63)), new UserId(TestIds.uuid(4)), NOW, 0);
    }

    @Test
    @DisplayName("#1566 a separation dated before an open duty's start is refused and names the early end")
    void refusesSeparationBeforeAnOpenDutyStarts() {
        assertThatThrownBy(() -> ActingDutyRules.requireNoOpenDutyStartingAfter(separation(CommandStatus.PENDING), List.of(openDuty(DAY.plusDays(1)))))
                .isInstanceOf(DomainException.class).hasMessageContaining("ACTING_END");
    }

    @Test
    @DisplayName("#1566 a duty that starts on or before the separation day does not block it")
    void admitsSeparationWhenNoDutyStartsLater() {
        assertThatCode(() -> ActingDutyRules.requireNoOpenDutyStartingAfter(separation(CommandStatus.PENDING),
                List.of(openDuty(DAY), openDuty(DAY.minusDays(3))))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = CommandStatus.class, names = {"PENDING", "HELD", "EFFECTIVE"})
    @DisplayName("#1566 a start dated after a pending, held or effective separation can never take effect")
    void startFollowsSeparation(CommandStatus status) {
        PersonnelCommand start = ActingDutyRulesTest.start(DAY.plusDays(2), DAY.plusDays(5), ActingDutyRulesTest.UNIT, null, null, "coverage", 53);
        org.assertj.core.api.Assertions.assertThat(ActingDutyRules.startFollowsSeparation(start, List.of(separation(status)))).isTrue();
    }

    @Test
    @DisplayName("#1566 a start on the separation day, or after a covering reinstatement, still takes effect")
    void startOnTheDayOrAfterReinstatementIsKept() {
        PersonnelCommand onTheDay = ActingDutyRulesTest.start(DAY, DAY.plusDays(5), ActingDutyRulesTest.UNIT, null, null, "coverage", 53);
        org.assertj.core.api.Assertions.assertThat(ActingDutyRules.startFollowsSeparation(onTheDay, List.of(separation(CommandStatus.PENDING)))).isFalse();
        PersonnelCommand later = ActingDutyRulesTest.start(DAY.plusDays(4), DAY.plusDays(9), ActingDutyRulesTest.UNIT, null, null, "coverage", 53);
        PersonnelCommand reinstatement = PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(64)), ActingDutyRulesTest.EMPLOYEE,
                PersonnelCommandKind.REINSTATE, DAY.plusDays(2), CommandStatus.EFFECTIVE, null, null, null, null, null, null, null, null, null, null,
                new UserId(TestIds.uuid(4)), NOW, NOW, 0, 0);
        org.assertj.core.api.Assertions.assertThat(ActingDutyRules.startFollowsSeparation(later, List.of(separation(CommandStatus.EFFECTIVE), reinstatement))).isFalse();
    }
}
