package com.forcitis.one.organization.workforce.domain;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record SyncDiff(EntityKind entityKind, String subjectKey, ChangeKind changeKind, List<String> changedFields,
                       @Nullable IssueKind issueKind, @Nullable String detail) {
    public SyncDiff {
        Objects.requireNonNull(entityKind, "entityKind");
        Objects.requireNonNull(subjectKey, "subjectKey");
        Objects.requireNonNull(changeKind, "changeKind");
        changedFields = List.copyOf(changedFields);
    }

    public static SyncDiff change(EntityKind entityKind, String subjectKey, ChangeKind changeKind, List<String> changedFields) {
        return new SyncDiff(entityKind, subjectKey, changeKind, changedFields, null, null);
    }

    public static SyncDiff issue(EntityKind entityKind, String subjectKey, @Nullable IssueKind issueKind, @Nullable String detail) {
        return new SyncDiff(entityKind, subjectKey, ChangeKind.ISSUE, List.of(), issueKind, detail);
    }

    public boolean isIssue() {
        return changeKind == ChangeKind.ISSUE;
    }
}
