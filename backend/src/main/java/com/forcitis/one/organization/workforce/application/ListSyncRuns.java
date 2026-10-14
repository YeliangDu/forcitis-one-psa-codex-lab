package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListSyncRuns {
    private static final int MAX = 200;

    private final SyncRunRepository runs;

    ListSyncRuns(SyncRunRepository runs) {
        this.runs = runs;
    }

    public List<SyncRunView> execute(int limit) {
        return runs.findRecent(Math.max(1, Math.min(limit, MAX))).stream().map(SyncRunView::of).toList();
    }
}
