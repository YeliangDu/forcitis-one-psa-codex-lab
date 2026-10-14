package com.forcitis.one.organization.workforce.application;

import java.util.List;

/** A sync run with its report rows. */
public record SyncRunDetailView(SyncRunView run, List<SyncDiffView> diffs) {
}
