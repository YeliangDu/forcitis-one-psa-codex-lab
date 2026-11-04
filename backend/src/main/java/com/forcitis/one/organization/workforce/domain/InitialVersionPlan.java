package com.forcitis.one.organization.workforce.domain;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The employee versions the one-time initial migration writes for a person only the snapshot knows (one-design data design on #960, section 3):
 * the current state only, never a history. The left edge is the earliest assignment version, so that both version tables agree on the first day.
 * The person is employed or separated as the snapshot says. The snapshot keeps no day of the leaving, so a separated person gets one separated row that
 * opens on the left edge and stays open.
 */
public final class InitialVersionPlan {

    /** A person as the snapshot holds them; firstDay is the earliest current assignment version, empty when there is none. */
    public record SnapshotPerson(java.util.UUID id, boolean employed, boolean feeEarner, @Nullable LocalDate firstDay, boolean missingAssignmentVersion) {
    }

    /** One MIGRATION row: employed rows carry the fee-earner flag of the snapshot; separated rows are never fee earners and have no phase. */
    public record Row(LocalDate from, @Nullable LocalDate to, boolean employed, boolean feeEarner) {
    }

    private InitialVersionPlan() {
    }

    public static LocalDate firstDay(SnapshotPerson person, LocalDate today) {
        return person.firstDay() == null ? today : person.firstDay();
    }

    public static List<Row> rowsFor(SnapshotPerson person, LocalDate today) {
        LocalDate left = firstDay(person, today);
        if (person.employed()) {
            return List.of(new Row(left, null, true, person.feeEarner()));
        }
        return List.of(new Row(left, null, false, false));
    }
}
