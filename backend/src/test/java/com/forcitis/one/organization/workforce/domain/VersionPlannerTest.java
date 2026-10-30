package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** IMP-1030-PAR-B3: the supersede-and-inserts mechanism of W2 design section 2 on plain values, no database. */
class VersionPlannerTest {
    static final LocalDate START = LocalDate.of(2026, 1, 1);
    static final PersonnelCommandId COMMAND = new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
    static final PersonnelCommandId LATER = new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c2"));
    static final ChangeSource BY_COMMAND = ChangeSource.ofCommand(COMMAND, new AuditEntryId(UUID.fromString("019a0000-0000-7000-8000-0000000000f1")));
    static final ChangeSource BY_LATER = ChangeSource.ofCommand(LATER, new AuditEntryId(UUID.fromString("019a0000-0000-7000-8000-0000000000f2")));
    static final Set<String> UNIT = Set.of("unit");
    final AtomicInteger counter = new AtomicInteger();

    UUID next() {
        return UUID.fromString("019a0000-0000-7000-8000-%012d".formatted(1000 + counter.incrementAndGet()));
    }

    static VersionRow row(int id, LocalDate from, LocalDate to, Object unit, ChangeSource source) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("unit", unit);
        values.put("phase", "PROBATION");
        return new VersionRow(UUID.fromString("019a0000-0000-7000-8000-%012d".formatted(id)), from, to, values, source, null, false);
    }

    /** What a command named in the history set: a migrated row set everything, a command row only the unit. */
    static Function<VersionRow, Set<String>> setters() {
        return r -> r.source().command() ? UNIT : Set.of("unit", "phase");
    }

    VersionPlanner.Change toUnit(LocalDate from, String unit) {
        return new VersionPlanner.Change(from, Map.of("unit", unit), r -> true, false);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 a change in the middle of a row supersedes it and appends the untouched part under the old source and the changed part under the command")
    void aChangeSplitsTheRowItLandsIn() {
        VersionRow migrated = row(1, START, null, "A", ChangeSource.MIGRATION);

        VersionPlan plan = VersionPlanner.apply(List.of(migrated), toUnit(LocalDate.of(2026, 8, 1), "B"), BY_COMMAND, setters(), this::next);

        assertThat(plan.supersede()).containsExactly(migrated.id());
        assertThat(plan.inserts()).hasSize(2);
        assertThat(plan.inserts().get(0)).satisfies(untouched -> {
            assertThat(untouched.from()).isEqualTo(START);
            assertThat(untouched.to()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(untouched.values().get("unit")).isEqualTo("A");
            assertThat(untouched.source()).isEqualTo(ChangeSource.MIGRATION);
            assertThat(untouched.correctedFrom()).isEqualTo(migrated.id());
        });
        assertThat(plan.inserts().get(1)).satisfies(changed -> {
            assertThat(changed.from()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(changed.to()).isNull();
            assertThat(changed.values().get("unit")).isEqualTo("B");
            assertThat(changed.source()).isEqualTo(BY_COMMAND);
        });
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 a back-dated change stops at the first later row that sets the same column itself, and leaves that row alone")
    void aChangeStopsAtALaterSetter() {
        VersionRow first = row(1, START, LocalDate.of(2026, 10, 1), "A", ChangeSource.MIGRATION);
        VersionRow later = row(2, LocalDate.of(2026, 10, 1), null, "C", BY_LATER);

        VersionPlan plan = VersionPlanner.apply(List.of(first, later), toUnit(LocalDate.of(2026, 8, 1), "B"), BY_COMMAND, setters(), this::next);

        assertThat(plan.supersede()).containsExactly(first.id());
        assertThat(plan.inserts()).extracting(n -> n.values().get("unit")).containsExactly("A", "B");
        assertThat(plan.inserts().get(1).to()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 a column another command did not set is overwritten past that command's row: a transfer is not undone by a later regularization")
    void aChangeRunsThroughRowsThatSetOtherColumns() {
        VersionRow first = row(1, START, LocalDate.of(2026, 10, 1), "A", ChangeSource.MIGRATION);
        VersionRow regularized = new VersionRow(UUID.fromString("019a0000-0000-7000-8000-000000000002"), LocalDate.of(2026, 10, 1), null,
                Map.of("unit", "A", "phase", "REGULAR"), BY_LATER, first.id(), false);
        Function<VersionRow, Set<String>> setters = r -> r.source().command() ? Set.of("phase") : Set.of("unit", "phase");

        VersionPlan plan = VersionPlanner.apply(List.of(first, regularized), toUnit(LocalDate.of(2026, 8, 1), "B"), BY_COMMAND, setters, this::next);

        assertThat(plan.supersede()).containsExactly(first.id(), regularized.id());
        assertThat(plan.inserts()).extracting(n -> n.values().get("unit")).containsExactly("A", "B", "B");
        assertThat(plan.inserts().getLast().values().get("phase")).isEqualTo("REGULAR");
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 a row whose values do not change is left alone; a change that touches nothing supersedes nothing")
    void anUnchangedRowIsNotTouched() {
        VersionRow row = row(1, START, null, "A", ChangeSource.MIGRATION);

        VersionPlan plan = VersionPlanner.apply(List.of(row), toUnit(LocalDate.of(2026, 8, 1), "A"), BY_COMMAND, setters(), this::next);

        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 a day no row covers is filled only when the command says so (hire, reinstatement)")
    void aGapIsFilledOnlyWhenAsked() {
        VersionRow ended = row(1, START, LocalDate.of(2026, 6, 1), "A", ChangeSource.MIGRATION);
        LocalDate back = LocalDate.of(2026, 9, 1);

        VersionPlan ignored = VersionPlanner.apply(List.of(ended), toUnit(back, "B"), BY_COMMAND, setters(), this::next);
        VersionPlan filled = VersionPlanner.apply(List.of(ended), new VersionPlanner.Change(back, Map.of("unit", "B"), r -> true, true), BY_COMMAND, setters(), this::next);

        assertThat(ignored.isEmpty()).isTrue();
        assertThat(filled.supersede()).isEmpty();
        assertThat(filled.inserts()).singleElement().satisfies(n -> {
            assertThat(n.from()).isEqualTo(back);
            assertThat(n.to()).isNull();
            assertThat(n.correctedFrom()).isNull();
        });
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 taking a command out restores the earlier value, gives the pieces it wrote the source of the rows they came from, and supersedes what it only inserted")
    void revertRestoresWhatWasThere() {
        VersionRow migrated = row(1, START, null, "A", ChangeSource.MIGRATION);
        VersionPlan entry = VersionPlanner.apply(List.of(migrated), toUnit(LocalDate.of(2026, 8, 1), "B"), BY_COMMAND, setters(), this::next);
        VersionRow head = fromNew(entry.inserts().get(0));
        VersionRow tail = fromNew(entry.inserts().get(1));

        VersionPlan undo = VersionPlanner.revert(List.of(head, tail), id -> migrated, COMMAND, LocalDate.of(2026, 8, 1), UNIT, r -> true, setters(), this::next);

        assertThat(undo.supersede()).containsExactly(tail.id());
        assertThat(undo.inserts()).singleElement().satisfies(n -> {
            assertThat(n.values().get("unit")).isEqualTo("A");
            assertThat(n.source()).isEqualTo(ChangeSource.MIGRATION);
            assertThat(n.from()).isEqualTo(LocalDate.of(2026, 8, 1));
        });
        VersionPlan inserted = VersionPlanner.revert(List.of(new VersionRow(next(), START, null, Map.of("unit", "B"), BY_COMMAND, null, false)), id -> migrated,
                COMMAND, START, UNIT, r -> true, setters(), this::next);
        assertThat(inserted.inserts()).as("a row the command only inserted is not replaced").isEmpty();
        assertThat(inserted.supersede()).hasSize(1);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 an assignment ended by a separation comes back open when the separation is taken out")
    void endAtAndReopenAtAreInverse() {
        VersionRow open = row(1, START, null, "A", ChangeSource.MIGRATION);
        LocalDate end = LocalDate.of(2026, 11, 3);

        VersionPlan ended = VersionPlanner.endAt(List.of(open), end, this::next);
        VersionRow closed = fromNew(ended.inserts().getFirst());
        VersionPlan reopened = VersionPlanner.reopenAt(List.of(closed), end, this::next);

        assertThat(ended.supersede()).containsExactly(open.id());
        assertThat(closed.to()).isEqualTo(end);
        assertThat(reopened.supersede()).containsExactly(closed.id());
        assertThat(reopened.inserts()).singleElement().satisfies(n -> {
            assertThat(n.to()).isNull();
            assertThat(n.values()).isEqualTo(open.values());
        });
    }

    private static VersionRow fromNew(VersionPlan.NewRow n) {
        return new VersionRow(n.id(), n.from(), n.to(), n.values(), n.source(), n.correctedFrom(), false);
    }
}
