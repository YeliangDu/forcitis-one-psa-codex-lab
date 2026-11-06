package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Deliberately offers no way to approve, claim or return a task and no HTTP start (I-5, I-7). */
public interface ApprovalProcesses {

    /** Idempotent per (process type, document, round); a foreign initiator gets AccessDeniedException (I-5); joins the caller's transaction. */
    ApprovalInstanceId start(ApprovalStartRequest request);

    boolean isFinished(ApprovalInstanceId instance);

    /** The form fields as submitted at start, running or finished; empty for an unknown instance. Reads without locking and needs no actor: for a trusted module listener, like progress. */
    Map<String, List<String>> formFields(ApprovalInstanceId instance);

    /**
     * The instance of an open task of that process, only while the person may act on it now: its assignee once somebody claimed it, otherwise any candidate.
     * Empty for everyone else and for a task that is decided, closed, of another process or unknown: the caller answers "not found" without telling which. Reads
     * without locking and audits nothing.
     */
    Optional<ApprovalInstanceId> instanceOfTaskOpenTo(ProcessKey processKey, ApprovalTaskId taskId, UserId actor);

    /** Reads without locking; empty while no approval of the document was started. */
    Optional<ApprovalProgress> progress(ProcessKey processKey, DocumentReference document);

    /** The answer the withdraw and cancel below act on. */
    WithdrawCheck checkWithdraw(ProcessKey processKey, DocumentReference document, UserId actor);

    /** Ends the round as RETURNED; the initiator may start a new one. Needs a reason. */
    RetractionResult withdraw(ProcessKey processKey, DocumentReference document, UserId actor, String reason);

    /** Ends the round as CANCELLED, keeping the history. Needs a reason. */
    RetractionResult cancel(ProcessKey processKey, DocumentReference document, UserId actor, String reason);
}
