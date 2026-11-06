package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActingDutyEndExtendRulesTest {
    static final LocalDate DAY = LocalDate.of(2026, 11, 6);
    static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    static final EmployeeId EMPLOYEE = new EmployeeId(TestIds.uuid(1));
    static final EmploymentRelationshipId REL = new EmploymentRelationshipId(TestIds.uuid(2));
    static final ActingDutyId DUTY = new ActingDutyId(TestIds.uuid(3));
    static final UserId RECORDER = new UserId(TestIds.uuid(4));

    static PersonnelCommand change(PersonnelCommandKind kind, LocalDate day, LocalDate ends, String reason) {
        EnterPersonnelCommand request = new EnterPersonnelCommand(EMPLOYEE, kind, day, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, REL,
                null, null, null, ends, DUTY, reason);
        return PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(5)), EMPLOYEE, request, RECORDER, NOW, DAY, 0);
    }

    @Test
    @DisplayName("#1566 a valid end dated ahead waits; an extension entered today is immediately effective")
    void endWaitsAndExtensionIsImmediate() {
        assertThat(change(PersonnelCommandKind.ACTING_END, DAY.plusDays(2), null, "coverage finished").status())
                .isEqualTo(CommandStatus.PENDING);
        PersonnelCommand extension = change(PersonnelCommandKind.ACTING_EXTEND, DAY, DAY.plusDays(7), "coverage continues");
        assertThat(extension.status()).isEqualTo(CommandStatus.EFFECTIVE);
        assertThat(extension.appliedAt()).isEqualTo(NOW);
    }
    static ActingDuty duty(int id, EmployeeId employee, EmploymentRelationshipId relationship, PositionId post,
                           LocalDate from, LocalDate to, LocalDate ended) {
        return new ActingDuty(new ActingDutyId(TestIds.uuid(id)), employee, relationship, new OrgUnitId(TestIds.uuid(6)), post, null,
                from, to, ended, "coverage", new PersonnelCommandId(TestIds.uuid(7)), RECORDER, NOW, 2);
    }

    static ActingDuty target() {
        return duty(3, EMPLOYEE, REL, null, DAY.minusDays(2), DAY.plusDays(3), null);
    }

    static PersonnelHistory history(List<PersonnelCommand> commands) {
        return new PersonnelHistory(List.of(), List.of(), List.of(), List.of(), commands, Map.of());
    }

    @ParameterizedTest
    @EnumSource(value = PersonnelCommandKind.class, names = {"ACTING_END", "ACTING_EXTEND"})
    @DisplayName("#1566 changes refuse missing, foreign-employee, foreign-relationship and already closed duties")
    void targetMustBeAnOpenDutyOfThisRelationship(PersonnelCommandKind kind) {
        PersonnelCommand c = change(kind, DAY, kind == PersonnelCommandKind.ACTING_EXTEND ? DAY.plusDays(7) : null, "adjust cover");
        assertThatThrownBy(() -> ActingDutyRules.requireTarget(c, null)).isInstanceOf(DomainException.class);
        for (ActingDuty other : List.of(
                duty(3, new EmployeeId(TestIds.uuid(8)), REL, null, DAY.minusDays(2), DAY.plusDays(3), null),
                duty(3, EMPLOYEE, new EmploymentRelationshipId(TestIds.uuid(9)), null, DAY.minusDays(2), DAY.plusDays(3), null),
                duty(3, EMPLOYEE, REL, null, DAY.minusDays(2), DAY.plusDays(3), DAY.minusDays(1)),
                duty(10, EMPLOYEE, REL, null, DAY.minusDays(2), DAY.plusDays(3), null))) {
            assertThatThrownBy(() -> ActingDutyRules.requireTarget(c, other)).isInstanceOf(DomainException.class);
        }
        assertThat(ActingDutyRules.requireTarget(c, target())).isEqualTo(target());
    }

    @ParameterizedTest
    @ValueSource(ints = {-3, -2, 3, 4})
    @DisplayName("#1566 early end is strictly inside the original duty term")
    void refusesEndAtOrOutsideTermEdges(int offset) {
        PersonnelCommand c = change(PersonnelCommandKind.ACTING_END, DAY.plusDays(offset), null, "end cover");
        assertThatThrownBy(() -> ActingDutyRules.requireEnd(c, target(), List.of())).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#1566 another pending early end of the same duty is refused, even on a different date")
    void refusesSecondPendingEnd() {
        PersonnelCommand pending = change(PersonnelCommandKind.ACTING_END, DAY.plusDays(1), null, "end tomorrow");
        PersonnelCommand current = PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(10)), EMPLOYEE,
                new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.ACTING_END, DAY, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, REL,
                        null, null, null, null, DUTY, "end now"), RECORDER, NOW, DAY, 0);
        assertThatThrownBy(() -> ActingDutyRules.requireEnd(current, target(), List.of(pending))).isInstanceOf(DomainException.class);
        pending.withdraw(RECORDER, "replaced", NOW);
        ActingDutyRules.requireEnd(current, target(), List.of(pending));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    @DisplayName("#1566 extension must strictly increase the current exclusive end")
    void refusesNonGrowingExtension(int offset) {
        PersonnelCommand c = change(PersonnelCommandKind.ACTING_EXTEND, DAY, DAY.plusDays(offset), "extend cover");
        assertThatThrownBy(() -> ActingDutyRules.requireExtend(c, target(), history(List.of()), List.of(target())))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    @DisplayName("#1566 extension must use the business day of entry")
    void refusesDatedExtension(int offset) {
        assertThatThrownBy(() -> change(PersonnelCommandKind.ACTING_EXTEND, DAY.plusDays(offset), DAY.plusDays(7), "extend cover"))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("#1566 extension excludes itself, accepts touching terms, refuses overlapping position or unit-head duties")
    void extensionUsesHalfOpenOverlap(boolean unitHead) {
        PositionId post = unitHead ? null : new PositionId(TestIds.uuid(11));
        ActingDuty target = duty(3, EMPLOYEE, REL, post, DAY.minusDays(2), DAY.plusDays(3), null);
        PersonnelCommand c = change(PersonnelCommandKind.ACTING_EXTEND, DAY, DAY.plusDays(7), "extend cover");
        ActingDuty touching = duty(12, EMPLOYEE, REL, post, DAY.plusDays(7), DAY.plusDays(10), null);
        ActingDutyRules.requireExtend(c, target, history(List.of()), List.of(target, touching));
        ActingDuty overlap = duty(13, EMPLOYEE, REL, post, DAY.plusDays(6), DAY.plusDays(10), null);
        assertThatThrownBy(() -> ActingDutyRules.requireExtend(c, target, history(List.of()), List.of(target, overlap)))
                .isInstanceOf(DomainException.class);
        ActingDuty closed = duty(14, EMPLOYEE, REL, post, DAY.minusDays(4), DAY, DAY.minusDays(2));
        ActingDutyRules.requireExtend(c, target, history(List.of()), List.of(target, closed));
    }

    @Test
    @DisplayName("#1566 a pending start reserves days against an extension across relationships")
    void extensionCannotOverlapAPendingStart() {
        PersonnelCommand reservation = ActingDutyRulesTest.start(DAY.plusDays(5), DAY.plusDays(10),
                target().coveredOrgUnitId(), null, null, "reserved coverage", 15);
        PersonnelCommand c = change(PersonnelCommandKind.ACTING_EXTEND, DAY, DAY.plusDays(7), "extend cover");
        assertThatThrownBy(() -> ActingDutyRules.requireExtend(c, target(), history(List.of(reservation)), List.of(target())))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "bad\u0000reason"})
    @DisplayName("#1566 both acting changes need a nonblank reason without NUL")
    void refusesInvalidReasons(String reason) {
        for (PersonnelCommandKind kind : List.of(PersonnelCommandKind.ACTING_END, PersonnelCommandKind.ACTING_EXTEND)) {
            assertThatThrownBy(() -> change(kind, DAY, kind == PersonnelCommandKind.ACTING_EXTEND ? DAY.plusDays(7) : null, reason))
                    .isInstanceOf(DomainException.class);
        }
    }

}
