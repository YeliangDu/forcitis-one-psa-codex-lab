package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * The pre-release check of the cut-over to personnel commands (ADR-0068 item 2.5 C): the application does not start while a HELD sync run still has
 * held-separation rows. Those separations were kept back by the old sync, which no longer runs, and nothing else would ever carry them out; they are
 * dealt with on the previous version first (the release runbook).
 */
@Component
class SyncHeldRowsCheck implements SmartInitializingSingleton {
    private final SyncRunQueries runs;

    SyncHeldRowsCheck(SyncRunQueries runs) {
        this.runs = runs;
    }

    @Override
    public void afterSingletonsInstantiated() {
        verify();
    }

    void verify() {
        int rows = runs.countHeldRowsOfHeldRuns();
        if (rows > 0) {
            throw new IllegalStateException("Refusing to start: " + rows + " held-separation row(s) of HELD sync runs remain in organization_workforce.sync_run_held_separations."
                    + " Deal with those runs on the previous version before this release (release runbook, pre-release check of personnel commands).");
        }
    }
}
