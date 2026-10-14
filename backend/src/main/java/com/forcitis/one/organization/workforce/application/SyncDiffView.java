package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.SyncDiff;
import java.util.List;

/** One report row of a sync run (a change or a data exception). */
public record SyncDiffView(String entityKind, String subjectKey, String changeKind, List<String> changedFields,
                           String issueKind, String detail) {

    static SyncDiffView of(SyncDiff diff) {
        return new SyncDiffView(diff.entityKind().name(), diff.subjectKey(), diff.changeKind().name(),
                diff.changedFields(), diff.issueKind() == null ? null : diff.issueKind().name(), diff.detail());
    }
}
