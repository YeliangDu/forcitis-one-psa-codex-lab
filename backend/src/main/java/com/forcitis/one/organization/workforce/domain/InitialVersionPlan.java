package com.forcitis.one.organization.workforce.domain;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The employee versions the one-time initial migration writes for a person only the sync snapshot knows (one-design data design on #960, section 3):
 * the current state only, never the sync history. The left edge is the earliest assignment version, so that both version tables agree on the first day.
 * The person is employed or separated as the snapshot says; a separation day later than the left edge splits the history in an employed row that closes
 * on it and a separated row that stays open. The separation day is the day the sync observed the leaving, not the day HR recorded.
 */
public final class InitialVersionPlan {

    /** A person as the snapshot holds them; firstDay is the earliest current assignment version, empty when there is none; separatedOn is the business day of separated_at. */
    public record SnapshotPerson(java.util.UUID id, boolean employed, boolean feeEarner, @Nullable LocalDate separatedOn, @Nullable LocalDate firstDay,
                                 boolean missingAssignmentVersion) {
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
        LocalDate separatedOn = person.separatedOn() == null ? left : person.separatedOn();
        if (separatedOn.isAfter(left)) {
            return List.of(new Row(left, separatedOn, true, person.feeEarner()), new Row(separatedOn, null, false, false));
        }
        return List.of(new Row(left, null, false, false));
    }
}
