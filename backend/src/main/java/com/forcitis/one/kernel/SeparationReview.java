package com.forcitis.one.kernel;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Asked before a sync run turns people into separated ones. Implemented by whoever owns administrator access, so that a wrong
 * source can never take every administrator away at once (ADR-0027 section 1, ADR-0028).
 */
public interface SeparationReview {

    /**
     * The text of the transaction-level advisory lock (pg_advisory_xact_lock(hashtextextended(key, 0))) that every transaction taking part in
     * the administrator-separation decision takes first: entering a separation or an unpaid-leave start, releasing a hold, withdrawing a held command (ADR-0068 item 2.2; #943).
     * Workforce and identity are in different schemas, so the key lives here and no table holds it.
     */
    String SEPARATION_LOCK_KEY = "com.forcitis.one.kernel.SeparationReview.separation-decision";

    /**
     * releaser is the account releasing a held run, null for a scheduled run; the implementation finds the releaser's employee to set
     * against the separating ones. sessionMode is the mode of the releaser's session, empty text when there is no releaser or no
     * signed-in session. held are the employees already counted as gone (the caller's one count): separation held, or released and waiting for its date, or an unpaid-leave start entered and not yet started: they still are employed, but they do not
     * count as available administrators for the remainder, nor as the retained releaser (ADR-0068 item 2.2, 2.3).
     */
    Verdict review(List<EmployeeId> separating, Set<EmployeeId> held, UserId releaser);

    /**
     * The same judgement for the due-day re-judgement of a future-dated separation (ADR-0068 item 2.2, #967): the separating are available when they are
     * holders now and were employed and not on unpaid leave on {@code separatingDay}, their last working day; everyone else (remaining, retained releaser,
     * notice recipients) is judged as of today. The grant is always read as it is now. The three-argument method is this one with the separating judged today.
     */
    Verdict review(List<EmployeeId> separating, Set<EmployeeId> held, UserId releaser, LocalDate separatingDay);

    /** The sync path has no held set. */
    default Verdict review(List<EmployeeId> separating, UserId releaser) {
        return review(separating, Set.of(), releaser);
    }

    /**
     * An available holder is an enabled holder of the role who is employed (explicitly, in the organisation snapshot) and not on unpaid leave on the
     * business day (ADR-0068 section 3). separatedAdministrators are those of the separating who are available holders of super_admin or the
     * security auditor role; actorRetained says that the releaser is an available holder of one of the two, not separating and not in the
     * held set; remainingAdministrators counts the available super_admin holders left after the separation, leaving out the separating and the held;
     * noticeRecipients are the available holders of super_admin or of the security auditor role left out the separating only (the caller leaves out the held), each once: the people to tell
     * when one super administrator remains.
     */
    record Verdict(Set<EmployeeId> separatedAdministrators, boolean actorRetained, int remainingAdministrators, String sessionMode, String reason,
                   Set<EmployeeId> noticeRecipients) {
        public Verdict {
            separatedAdministrators = Set.copyOf(separatedAdministrators);
            noticeRecipients = Set.copyOf(noticeRecipients);
        }
    }
}
