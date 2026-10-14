package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import java.util.List;
import org.springframework.stereotype.Service;

/** The runs waiting for a person, with their reasons and the separation list; no diffs (ADR-0028). */
@Service
public class ListHeldSyncRuns {
    private final SyncRunQueries runs;

    ListHeldSyncRuns(SyncRunQueries runs) {
        this.runs = runs;
    }

    public List<SyncRunView> execute() {
        return runs.findHeld().stream().map(SyncRunView::of).toList();
    }
}
