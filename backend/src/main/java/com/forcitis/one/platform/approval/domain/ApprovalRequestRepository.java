package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository {

    /** Serialises concurrent starts of one document until the transaction ends, whether or not its request exists yet. */
    void lockForStart(ProcessKey processKey, DocumentReference document);

    /** Reads without a lock: for queries only. */
    Optional<ApprovalRequest> find(ProcessKey processKey, DocumentReference document);

    /** The row stays locked until the transaction ends. */
    Optional<ApprovalRequest> findForUpdate(ProcessKey processKey, DocumentReference document);

    Optional<ApprovalRequest> findByInstanceForUpdate(ApprovalInstanceId instanceId);

    /** Finds the request the task belongs to, whichever round the task is from. */
    Optional<ApprovalRequest> findByTaskForUpdate(ApprovalTaskId taskId);

    /** Writes the request, its new tasks and the tasks that changed, only if the stored version and status are still the ones it was loaded with. */
    void save(ApprovalRequest request);

    List<ApprovalRound> rounds(ProcessKey processKey, DocumentReference document);
}
