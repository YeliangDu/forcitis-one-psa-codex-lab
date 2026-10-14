package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetSyncRun {
    private final SyncRunQueries runs;

    GetSyncRun(SyncRunQueries runs) {
        this.runs = runs;
    }

    public Optional<SyncRunDetailView> execute(long syncRunId) {
        if (syncRunId <= 0) {
            return Optional.empty();
        }
        SyncRunId id = new SyncRunId(syncRunId);
        return runs.find(id).map(run -> new SyncRunDetailView(SyncRunView.of(run),
                runs.findDiffs(id).stream().map(SyncDiffView::of).toList()));
    }
}
