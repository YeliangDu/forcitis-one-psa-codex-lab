package com.forcitis.one.kernel;

import java.util.List;
import java.util.Set;

/**
 * Asked before a sync run turns people into separated ones. Implemented by whoever owns administrator access, so that a wrong
 * source can never take every administrator away at once (ADR-0027 section 1, ADR-0028).
 */
public interface SeparationReview {

    /**
     * The text of the transaction-level advisory lock (pg_advisory_xact_lock(hashtextextended(key, 0))) that every transaction taking part in
     * the administrator-separation decision takes first: executing a separation, releasing a hold, withdrawing a held command (ADR-0068 item 2.2).
     * Workforce and identity are in different schemas, so the key lives here and no table holds it.
     */
    String SEPARATION_LOCK_KEY = "com.forcitis.one.kernel.SeparationReview.separation-decision";

    /**
     * releaser is the account releasing a held run, null for a scheduled run; the implementation finds the releaser's employee to set
     * against the separating ones. sessionMode is the mode of the releaser's session, empty text when there is no releaser or no
     * signed-in session. held are the employees whose separation is held at this moment: they still are employed, but they do not
     * count as available administrators for the remainder, nor as the retained releaser (ADR-0068 item 2.2).
     */
    Verdict review(List<EmployeeId> separating, Set<EmployeeId> held, UserId releaser);

    /** The sync path has no held set. */
    default Verdict review(List<EmployeeId> separating, UserId releaser) {
        return review(separating, Set.of(), releaser);
    }

    /**
     * separatedAdministrators are those of the separating who hold super_admin or the security auditor role; actorRetained says
     * that the releaser is an enabled holder of one of the two, explicitly employed in the organisation snapshot and not separating;
     * remainingAdministrators counts the usable super_admin holders left after the separation.
     */
    record Verdict(Set<EmployeeId> separatedAdministrators, boolean actorRetained, int remainingAdministrators, String sessionMode, String reason) {
        public Verdict {
            separatedAdministrators = Set.copyOf(separatedAdministrators);
        }
    }
}
