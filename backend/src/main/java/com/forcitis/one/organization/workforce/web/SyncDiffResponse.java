package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.SyncDiffView;
import java.util.List;

/** One report row: a change, or a data exception (changeKind ISSUE) for the data stewards. */
record SyncDiffResponse(String entityKind, String subjectKey, String changeKind, List<String> changedFields,
                        String issueKind, String detail) {

    static SyncDiffResponse of(SyncDiffView view) {
        return new SyncDiffResponse(view.entityKind(), view.subjectKey(), view.changeKind(), view.changedFields(),
                view.issueKind(), view.detail());
    }
}
