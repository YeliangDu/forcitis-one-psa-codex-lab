package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.SyncDiffView;
import java.util.List;

/** changeKind ISSUE rows are data exceptions for the data stewards, not changes. */
record SyncDiffResponse(String entityKind, String subjectKey, String changeKind, List<String> changedFields,
                        String issueKind, String detail) {
    static SyncDiffResponse of(SyncDiffView view) {
        return new SyncDiffResponse(view.entityKind(), view.subjectKey(), view.changeKind(), view.changedFields(),
                view.issueKind(), view.detail());
    }
}
