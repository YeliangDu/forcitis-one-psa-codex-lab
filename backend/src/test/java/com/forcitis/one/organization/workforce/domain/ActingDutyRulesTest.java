package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ActingDutyRulesTest {
    static final LocalDate DAY = LocalDate.of(2026, 11, 6);
    static final EmployeeId EMPLOYEE = new EmployeeId(TestIds.uuid(1));
    static final OrgUnitId UNIT = new OrgUnitId(TestIds.uuid(2));

    static final EmploymentRelationshipId REL = new EmploymentRelationshipId(TestIds.uuid(5));
    static final PositionId POSITION = new PositionId(TestIds.uuid(6));

    static PersonnelCommand start(LocalDate end, String reason) {
        return start(DAY, end, UNIT, null, null, reason, 3);
    }

    static PersonnelCommand start(LocalDate from, LocalDate end, OrgUnitId unit, PositionId position, EmployeeId covered, String reason, int id) {
        EnterPersonnelCommand request = new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.ACTING_START, from, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                unit, position, covered, end, null, reason);
        return PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(id)), EMPLOYEE, request, new UserId(TestIds.uuid(4)),
                Instant.parse("2026-11-06T08:00:00Z"), DAY, 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("#1566 an acting duty must end strictly after its start")
    void refusesEmptyOrReversedTerms(int days) {
        assertThatThrownBy(() -> start(DAY.plusDays(days), "temporary coverage")).isInstanceOf(DomainException.class);
    }
    static PersonnelHistory history(List<VersionRow> assignments, List<PersonnelCommand> commands) {
        return new PersonnelHistory(List.of(), assignments, List.of(), List.of(), commands, Map.of());
    }

    static ActingDuty duty(PositionId position, EmployeeId employee, LocalDate from, LocalDate end) {
        return new ActingDuty(new ActingDutyId(TestIds.uuid(7)), employee, REL, UNIT, position, null, from, end, null,
                "coverage", new PersonnelCommandId(TestIds.uuid(8)), new UserId(TestIds.uuid(4)), Instant.parse("2026-11-06T08:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("#1566 same person's overlapping position or unit-head duties are refused")
    void refusesOverlappingDuties(boolean unitHead) {
        PositionId position = unitHead ? null : POSITION;
        PersonnelCommand command = start(DAY, DAY.plusDays(3), UNIT, position, null, "coverage", 3);
        assertThatThrownBy(() -> ActingDutyRules.requireAvailable(command, history(List.of(), List.of()),
                List.of(duty(position, EMPLOYEE, DAY.minusDays(1), DAY.plusDays(1))))).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#1566 a pending start reserves overlapping days across the person's relationships")
    void refusesOverlappingPendingStart() {
        PersonnelCommand earlier = start(DAY.plusDays(2), DAY.plusDays(5), UNIT, POSITION, null, "coverage", 9);
        PersonnelCommand command = start(DAY, DAY.plusDays(3), UNIT, POSITION, null, "coverage", 3);
        assertThatThrownBy(() -> ActingDutyRules.requireAvailable(command, history(List.of(), List.of(earlier)), List.of()))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("#1566 every own post version overlapping any day of the acting term prevents covering it")
    void refusesOwnPostEvenWhenItStartsLater(boolean concurrent) {
        VersionRow row = new VersionRow(TestIds.uuid(10), DAY.plusDays(1), DAY.plusDays(4),
                Map.of(CommandEffects.ORG_UNIT, UNIT.value(), CommandEffects.POSITION, POSITION.value()), ChangeSource.MIGRATION, null, false,
                TestIds.uuid(concurrent ? 11 : 12));
        PersonnelCommand command = start(DAY, DAY.plusDays(3), UNIT, POSITION, null, "coverage", 3);
        assertThatThrownBy(() -> ActingDutyRules.requireAvailable(command, history(List.of(row), List.of()), List.of()))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("#1566 half-open touching terms, other people and other covered posts do not conflict")
    void allowsNonOverlappingCoverage(boolean unitHead) {
        PositionId position = unitHead ? null : POSITION;
        PersonnelCommand command = start(DAY, DAY.plusDays(3), UNIT, position, null, "coverage", 3);
        ActingDutyRules.requireAvailable(command, history(List.of(), List.of()), List.of(
                duty(position, EMPLOYEE, DAY.minusDays(2), DAY), duty(position, EMPLOYEE, DAY.plusDays(3), DAY.plusDays(5)),
                duty(position, new EmployeeId(TestIds.uuid(20)), DAY, DAY.plusDays(3)),
                duty(unitHead ? POSITION : null, EMPLOYEE, DAY, DAY.plusDays(3))));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t", "bad\u0000reason"})
    @DisplayName("#1566 a reason is required, nonblank and contains no NUL")
    void refusesInvalidReason(String reason) {
        assertThatThrownBy(() -> start(DAY.plusDays(1), reason)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#1566 one day and 500 Unicode characters are valid; 501 are refused")
    void acceptsBoundaryLengths() {
        assertThat(start(DAY.plusDays(1), "x".repeat(500)).status()).isEqualTo(CommandStatus.EFFECTIVE);
        assertThat(start(DAY.plusDays(1), "😀".repeat(500)).status()).isEqualTo(CommandStatus.EFFECTIVE);
        assertThatThrownBy(() -> start(DAY.plusDays(1), "x".repeat(501))).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#1566 covered department is mandatory and the covered employee must be another person")
    void refusesMissingDepartmentAndSelfCoverage() {
        assertThatThrownBy(() -> start(DAY, DAY.plusDays(1), null, null, null, "coverage", 3)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> start(DAY, DAY.plusDays(1), UNIT, null, EMPLOYEE, "coverage", 3)).isInstanceOf(DomainException.class);
    }
    @ParameterizedTest
    @EnumSource(value = PersonnelCommandKind.class, names = {"ACTING_END", "ACTING_EXTEND"})
    @DisplayName("#1566 later acting kinds are refused explicitly until their implementation slices")
    void refusesUnsupportedKinds(PersonnelCommandKind kind) {
        EnterPersonnelCommand request = new EnterPersonnelCommand(EMPLOYEE, kind, DAY, null, null, null, null, null,
                null, null, null, null, null);
        assertThatThrownBy(() -> PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(3)), EMPLOYEE, request,
                new UserId(TestIds.uuid(4)), Instant.parse("2026-11-06T08:00:00Z"), DAY, 0))
                .isInstanceOf(DomainException.class).hasMessageContaining("unsupported personnel command kind");
    }

    @Test
    @DisplayName("#1566 an acting start needs employment on its day, using the ordinary personnel-command rule")
    void requiresEmploymentOnTheEffectiveDay() {
        PersonnelCommand command = start(DAY.plusDays(3), "coverage");
        assertThatThrownBy(() -> PersonnelCommandRules.check(command, history(List.of(), List.of())))
                .isInstanceOf(DomainException.class);
        VersionRow separated = new VersionRow(TestIds.uuid(40), DAY.minusDays(1), null,
                Map.of(CommandEffects.EMPLOYMENT, "SEPARATED"), ChangeSource.MIGRATION, null, false);
        PersonnelHistory noEmployment = new PersonnelHistory(List.of(separated), List.of(), List.of(), List.of(), List.of(), Map.of());
        assertThatThrownBy(() -> PersonnelCommandRules.check(command, noEmployment)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#1566 acting is outside the ordinary same-day bucket and changes no assignment or employee version")
    void doesNotOccupyTheOrdinaryCommandDayOrChangeVersions() {
        PersonnelCommand command = start(DAY.plusDays(3), "coverage");
        PersonnelCommand transfer = PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(41)), EMPLOYEE,
                PersonnelCommandKind.TRANSFER, DAY, CommandStatus.PENDING, UNIT, POSITION, null, null, null,
                null, null, null, null, AssignmentKind.PRIMARY, new UserId(TestIds.uuid(4)), Instant.parse("2026-11-06T08:00:00Z"), null, 0, 0);
        VersionRow employed = new VersionRow(TestIds.uuid(42), DAY.minusDays(1), null,
                Map.of(CommandEffects.EMPLOYMENT, "EMPLOYED"), ChangeSource.MIGRATION, null, false);
        PersonnelHistory all = new PersonnelHistory(List.of(employed), List.of(), List.of(), List.of(), List.of(transfer, command), Map.of());
        PersonnelCommandRules.check(command, all);
        PersonnelCommandRules.requireOneCommandPerDay(command, all);
        PersonnelCommandRules.check(transfer, all);
        PersonnelCommandRules.requireOneCommandPerDay(transfer, all);
        assertThat(CommandEffects.employeeValues(command)).isEmpty();
        assertThat(CommandEffects.assignmentValues(command)).isEmpty();
        assertThat(CommandEffects.legalEntityValues(command)).isEmpty();
    }

    @Test
    @DisplayName("#1566 a withdrawn pending start releases its reservation; touching assignment dates remain valid")
    void withdrawnReservationsAndTouchingPostsAreAllowed() {
        PersonnelCommand withdrawn = start(DAY.plusDays(2), DAY.plusDays(5), UNIT, POSITION, null, "coverage", 9);
        withdrawn.withdraw(new UserId(TestIds.uuid(4)), "changed plans", Instant.parse("2026-11-06T08:00:00Z"));
        VersionRow earlier = new VersionRow(TestIds.uuid(43), DAY.minusDays(2), DAY,
                Map.of(CommandEffects.ORG_UNIT, UNIT.value(), CommandEffects.POSITION, POSITION.value()), ChangeSource.MIGRATION, null, false);
        VersionRow later = new VersionRow(TestIds.uuid(44), DAY.plusDays(3), null,
                Map.of(CommandEffects.ORG_UNIT, UNIT.value(), CommandEffects.POSITION, POSITION.value()), ChangeSource.MIGRATION, null, false);
        PersonnelCommand command = start(DAY, DAY.plusDays(3), UNIT, POSITION, null, "coverage", 3);
        ActingDutyRules.requireAvailable(command, history(List.of(earlier, later), List.of(withdrawn)), List.of());
    }
}
