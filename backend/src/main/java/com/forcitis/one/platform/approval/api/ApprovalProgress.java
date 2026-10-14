package com.forcitis.one.platform.approval.api;

import java.util.Objects;

/** The round the request is in and its status, read without locking anything. */
public record ApprovalProgress(RoundNumber round, ApprovalStatus status) {

    public ApprovalProgress {
        Objects.requireNonNull(round, "round");
        Objects.requireNonNull(status, "status");
    }
}
