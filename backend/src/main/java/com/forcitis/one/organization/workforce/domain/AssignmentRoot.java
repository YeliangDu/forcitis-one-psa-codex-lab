package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.AssignmentId;

/** The root of one post of one person: its versions all carry its id and its kind. */
public record AssignmentRoot(AssignmentId id, AssignmentKind kind) {
}
