package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.SyncRunDetailView;
import java.util.List;

/** A sync run with the differences and data exceptions it found. */
record SyncRunDetailResponse(SyncRunResponse syncRun, List<SyncDiffResponse> diffs) {

    static SyncRunDetailResponse of(SyncRunDetailView view) {
        return new SyncRunDetailResponse(SyncRunResponse.of(view.run()), view.diffs().stream().map(SyncDiffResponse::of).toList());
    }
}
