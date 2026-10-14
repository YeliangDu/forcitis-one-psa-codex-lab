package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;

/** Deliberately offers no way to approve, claim or return a task and no HTTP start (I-5, I-7). */
public interface ApprovalProcesses {

    /** Idempotent per (process type, document); a foreign initiator gets AccessDeniedException (I-5); joins the caller's transaction. */
    ApprovalInstanceId start(ApprovalStartRequest request);

    boolean isFinished(ApprovalInstanceId instance);

    /** The answer the withdraw and cancel below act on. */
    WithdrawCheck checkWithdraw(ProcessKey processKey, DocumentReference document, UserId actor);

    /** Ends the round as RETURNED; the initiator may start a new one. Needs a reason. */
    void withdraw(ProcessKey processKey, DocumentReference document, UserId actor, String reason);

    /** Ends the round as CANCELLED, keeping the history. Needs a reason. */
    void cancel(ProcessKey processKey, DocumentReference document, UserId actor, String reason);
}
