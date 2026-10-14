package com.forcitis.one.organization.workforce.application;

import java.util.List;

public record SyncRunDetailView(SyncRunView run, List<SyncDiffView> diffs) {
}
