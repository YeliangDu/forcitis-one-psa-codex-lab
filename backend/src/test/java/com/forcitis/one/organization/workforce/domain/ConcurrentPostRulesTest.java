package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #711 final review M2, M3, M1 and the entry refusals of a named post, as the domain decides them before anything is written. */
class ConcurrentPostRulesTest {
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-0000000007e1"));
    static final OrgUnitId UNIT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000007b1"));
    static final PositionId POSITION = new PositionId(UUID.fromString("019a0000-0000-7000-8000-0000000007b2"));
    static final AssignmentId PRIMARY = new AssignmentId(UUID.fromString("019a0000-0000-7000-8000-0000000007a1"));
    static final AssignmentId POST = new AssignmentId(UUID.fromString("019a0000-0000-7000-8000-0000000007a2"));
    static final AssignmentId OTHERS_POST = new AssignmentId(UUID.fromString("019a0000-0000-7000-8000-0000000007a3"));
    static final UserId USER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000007aa"));

    private static int counter;

    private static PersonnelCommand stored(PersonnelCommandKind kind, LocalDate day, CommandStatus status, AssignmentId post, AssignmentKind postKind) {
        boolean unit = kind == PersonnelCommandKind.START_CONCURRENT;
        return PersonnelCommand.restore(new PersonnelCommandId(UUID.nameUUIDFromBytes(("c" + counter++).getBytes())), EMPLOYEE, kind, day, status, unit ? UNIT : null,
                unit ? POSITION : null, null, null, null, null, null, null, post, postKind, USER, Instant.parse("2026-11-01T00:00:00Z"), null, 1, 1);
    }

    private static PersonnelCommand separation(LocalDate day, CommandStatus status) {
        return stored(PersonnelCommandKind.SEPARATE, day, status, null, null);
    }

    private static PersonnelCommand endOfPost(LocalDate day, CommandStatus status) {
        return stored(PersonnelCommandKind.END_CONCURRENT, day, status, POST, AssignmentKind.CONCURRENT);
    }

    private static PersonnelHistory history(PersonnelCommand... commands) {
        VersionRow employed = new VersionRow(UUID.randomUUID(), LocalDate.of(2026, 1, 1), null, Map.of(CommandEffects.EMPLOYMENT, "EMPLOYED"), ChangeSource.MIGRATION, null, false);
        VersionRow post = new VersionRow(UUID.randomUUID(), LocalDate.of(2026, 1, 1), null, Map.of(), ChangeSource.MIGRATION, null, false, POST.value());
        Map<UUID, PersonnelCommand> lookup = new HashMap<>();
        for (PersonnelCommand c : commands) {
            lookup.put(c.id().value(), c);
        }
        return new PersonnelHistory(List.of(employed), List.of(post), List.of(new AssignmentRoot(PRIMARY, AssignmentKind.PRIMARY), new AssignmentRoot(POST, AssignmentKind.CONCURRENT)),
                List.of(), List.of(commands), lookup);
    }

    private static PersonnelCommand start(LocalDate day) {
        return stored(PersonnelCommandKind.START_CONCURRENT, day, CommandStatus.PENDING, null, null);
    }

    @Test
    @DisplayName("#711 M2a a concurrent post is not started before a separation that is written (pending or effective): the new open row would outlive the separation; a held or withdrawn one does not count")
    void noStartBeforeAWrittenSeparation() {
        for (CommandStatus written : new CommandStatus[] {CommandStatus.PENDING, CommandStatus.EFFECTIVE}) {
            PersonnelCommand separation = separation(DAY.plusDays(5), written);
            assertThatThrownBy(() -> PersonnelCommandRules.check(start(DAY), history(separation))).isInstanceOf(DomainException.class).hasMessageContaining("separates after");
        }
        for (CommandStatus unwritten : new CommandStatus[] {CommandStatus.HELD, CommandStatus.WITHDRAWN}) {
            PersonnelCommand separation = separation(DAY.plusDays(5), unwritten);
            assertThatCode(() -> PersonnelCommandRules.check(start(DAY), history(separation))).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("#711 M3 a separation and the end of a concurrent post do not share a day, whichever is entered second; a withdrawn one frees the day")
    void noSeparationOnTheDayAPostEnds() {
        PersonnelCommand end = endOfPost(DAY, CommandStatus.PENDING);
        assertThatThrownBy(() -> PersonnelCommandRules.check(separation(DAY, CommandStatus.PENDING), history(end))).isInstanceOf(DomainException.class).hasMessageContaining("share the day");
        PersonnelCommand separation = separation(DAY, CommandStatus.PENDING);
        assertThatThrownBy(() -> PersonnelCommandRules.check(endOfPost(DAY, CommandStatus.PENDING), history(separation))).isInstanceOf(DomainException.class)
                .hasMessageContaining("share the day");
        PersonnelCommand withdrawn = endOfPost(DAY, CommandStatus.WITHDRAWN);
        assertThatCode(() -> PersonnelCommandRules.check(separation(DAY, CommandStatus.PENDING), history(withdrawn))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("#711 M2b/c the end of a concurrent post is withdrawn only while no written separation on or after its day would have ended the post: otherwise the post would come back open past the separation")
    void noWithdrawalOfAnEndPastASeparation() {
        PersonnelCommand end = endOfPost(DAY, CommandStatus.PENDING);
        for (LocalDate separationDay : new LocalDate[] {DAY, DAY.plusDays(3)}) {
            PersonnelCommand separation = separation(separationDay, CommandStatus.PENDING);
            assertThatThrownBy(() -> PersonnelCommandRules.requireNoSeparationAtOrAfter(end, history(end, separation))).isInstanceOf(DomainException.class)
                    .hasMessageContaining("withdraw the separation first");
        }
        PersonnelCommand earlier = separation(DAY.minusDays(1), CommandStatus.EFFECTIVE);
        assertThatCode(() -> PersonnelCommandRules.requireNoSeparationAtOrAfter(end, history(end, earlier))).doesNotThrowAnyException();
        PersonnelCommand withdrawn = separation(DAY, CommandStatus.WITHDRAWN);
        assertThatCode(() -> PersonnelCommandRules.requireNoSeparationAtOrAfter(end, history(end, withdrawn))).doesNotThrowAnyException();
    }

    private static EnterPersonnelCommand request(PersonnelCommandKind kind, AssignmentId post, LegalEntityId entity, OrgUnitId unit) {
        return new EnterPersonnelCommand(EMPLOYEE, kind, DAY, null, unit, null, null, null, null, entity, null, null, null, null, post);
    }

    private static PersonnelCommand entered(EnterPersonnelCommand request) {
        return PersonnelCommand.enter(new PersonnelCommandId(UUID.randomUUID()), EMPLOYEE, request, USER, Instant.parse("2026-11-01T00:00:00Z"), DAY, 1);
    }

    @Test
    @DisplayName("#711 J10/J11/J12 the post a command names must be one of the employee, an end of a post names a concurrent one, and a kind that acts on no post names none: the domain refuses before the database does")
    void aNamedPostIsChecked() {
        PersonnelHistory history = history();
        assertThatThrownBy(() -> entered(request(PersonnelCommandKind.TRANSFER, OTHERS_POST, null, UNIT)).actOnPost(history, () -> new AssignmentRoot(PRIMARY, AssignmentKind.PRIMARY)))
                .isInstanceOf(DomainException.class).hasMessageContaining("does not belong");
        assertThatThrownBy(() -> entered(request(PersonnelCommandKind.END_CONCURRENT, null, null, null)).actOnPost(history, () -> new AssignmentRoot(PRIMARY, AssignmentKind.PRIMARY)))
                .isInstanceOf(DomainException.class).hasMessageContaining("names the concurrent post");
        assertThatThrownBy(() -> entered(request(PersonnelCommandKind.END_CONCURRENT, PRIMARY, null, null)).actOnPost(history, () -> new AssignmentRoot(PRIMARY, AssignmentKind.PRIMARY)))
                .isInstanceOf(DomainException.class).hasMessageContaining("separate the employee");
        assertThatThrownBy(() -> entered(request(PersonnelCommandKind.REGULARIZE, POST, null, null)).actOnPost(history, () -> new AssignmentRoot(PRIMARY, AssignmentKind.PRIMARY)))
                .isInstanceOf(DomainException.class).hasMessageContaining("names no post");
    }

    @Test
    @DisplayName("#711 M1 a transfer or correction of a concurrent post names no legal entity: the cost legal entity follows the primary post, so such a command never waits for a countersign")
    void aConcurrentPostNamesNoLegalEntity() {
        LegalEntityId entity = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000007f1"));
        assertThatThrownBy(() -> entered(request(PersonnelCommandKind.TRANSFER, POST, entity, UNIT)).actOnPost(history(), () -> new AssignmentRoot(PRIMARY, AssignmentKind.PRIMARY)))
                .isInstanceOf(DomainException.class).hasMessageContaining("no legal entity");
        PersonnelCommand onPrimary = entered(request(PersonnelCommandKind.TRANSFER, null, entity, UNIT));
        onPrimary.actOnPost(history(), () -> new AssignmentRoot(PRIMARY, AssignmentKind.PRIMARY));
        assertThat(onPrimary.isOnConcurrentPost()).isFalse();
    }

    @Test
    @DisplayName("#711 J4 the assignment of a day is the primary post's, even when a concurrent post covers the day too")
    void theAssignmentOnADayIsThePrimaryOne() {
        VersionRow primary = new VersionRow(UUID.randomUUID(), LocalDate.of(2026, 1, 1), null, Map.of("marker", "primary"), ChangeSource.MIGRATION, null, false, PRIMARY.value());
        VersionRow concurrent = new VersionRow(UUID.randomUUID(), LocalDate.of(2026, 1, 1), null, Map.of("marker", "concurrent"), ChangeSource.MIGRATION, null, false, POST.value());
        for (List<VersionRow> rows : List.of(List.of(concurrent, primary), List.of(primary, concurrent))) {
            PersonnelHistory history = new PersonnelHistory(List.of(), rows, List.of(new AssignmentRoot(PRIMARY, AssignmentKind.PRIMARY), new AssignmentRoot(POST, AssignmentKind.CONCURRENT)),
                    List.of(), List.of(), Map.of());
            assertThat(history.assignmentOn(DAY)).isEqualTo(primary);
        }
    }
}
