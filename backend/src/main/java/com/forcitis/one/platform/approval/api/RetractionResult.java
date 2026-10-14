package com.forcitis.one.platform.approval.api;

/** A refusal is an answer, not an exception, so the caller's transaction is not marked for rollback; only a foreign actor is an AccessDeniedException. */
public enum RetractionResult {
    DONE,
    REASON_REQUIRED,
    NOT_RUNNING,
    CUTOFF_PASSED
}
