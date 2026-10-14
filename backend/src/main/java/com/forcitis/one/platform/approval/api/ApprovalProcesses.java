package com.forcitis.one.platform.approval.api;

/**
 * What business modules may ask of approval. There is deliberately no way to approve, claim or return a task here and
 * no HTTP start endpoint: tasks are acted on only through approval's own endpoints, which check the actor on the
 * server (I-7), and a start can only come from the module that owns the document (I-5).
 */
public interface ApprovalProcesses {

    /**
     * Idempotent per (process type, business key) (RULE-264/266/278): a repeated start by the same initiator returns the
     * live instance; once the previous round has ended, a new round starts. A start for a document whose approval
     * was started by somebody else fails with Spring Security's AccessDeniedException and changes nothing (I-5).
     * Must run inside the caller's transaction.
     */
    ApprovalInstanceId start(ApprovalRequest request);

    boolean isFinished(ApprovalInstanceId instance);
}
