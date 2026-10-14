package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.SyncDiff;
import java.util.List;

public record SyncDiffView(String entityKind, String subjectKey, String changeKind, List<String> changedFields,
                           String issueKind, String detail) {
    static SyncDiffView of(SyncDiff diff) {
        return new SyncDiffView(diff.entityKind().name(), diff.subjectKey(), diff.changeKind().name(),
                diff.changedFields(), diff.issueKind() == null ? null : diff.issueKind().name(), diff.detail());
    }
}
