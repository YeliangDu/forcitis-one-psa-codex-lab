package com.forcitis.one.kernel;

import java.util.List;
import java.util.Set;

/**
 * Asked before a sync run turns people into separated ones. Implemented by whoever owns administrator access, so that a wrong
 * source can never take every administrator away at once (ADR-0027 section 1, ADR-0028).
 */
public interface SeparationReview {

    /**
     * releaser is the person releasing a held run, null for a scheduled run. sessionMode is the mode of the releaser's session,
     * empty text when there is no releaser or no signed-in session.
     */
    Verdict review(List<UserId> separating, UserId releaser);

    /**
     * separatedAdministrators are those of the separating who hold super_admin or the security auditor role; actorRetained says
     * that the releaser is an enabled holder of one of the two, explicitly employed in the organisation snapshot and not separating;
     * remainingAdministrators counts the usable super_admin holders left after the separation.
     */
    record Verdict(Set<UserId> separatedAdministrators, boolean actorRetained, int remainingAdministrators, String sessionMode, String reason) {
        public Verdict {
            separatedAdministrators = Set.copyOf(separatedAdministrators);
        }
    }
}
