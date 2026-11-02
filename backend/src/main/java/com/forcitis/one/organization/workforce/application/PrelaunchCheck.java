package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import com.forcitis.one.organization.workforce.domain.SyncRunRecord;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The pre-launch check (#961; ADR-0055 §2, §5): go-live is refused while a HELD sync run still holds separations, since the stopped sync can no
 * longer release them on its own. Read only.
 */
@Component
class PrelaunchCheck {

    private final SyncRunQueries runs;

    PrelaunchCheck(SyncRunQueries runs) {
        this.runs = runs;
    }

    /** One line per HELD run that holds separation rows, empty when the check passes. */
    List<String> refusals() {
        return runs.findHeld().stream().filter(run -> !run.heldSeparations().isEmpty()).map(PrelaunchCheck::line).toList();
    }

    private static String line(SyncRunRecord run) {
        return "sync run " + run.syncRunId().value() + " is HELD and holds " + run.heldSeparations().size() + " separation rows";
    }
}
