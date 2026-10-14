package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApprovalTaskRepository {

    Optional<ApprovalTask> find(ApprovalTaskId taskId);

    /** Read without a lock, so the caller can lock the request first and the task second, the order every use case follows. */
    Optional<ApprovalInstanceId> instanceOf(ApprovalTaskId taskId);

    /** The person finished this node of this instance with an approval of exactly this content (RULE-302). */
    boolean hasApprovedSameContent(ApprovalInstanceId instanceId, String nodeKey, UserId user, String contentDigest);

    List<ApprovalTask> findOpenByInstance(ApprovalInstanceId instanceId);

    /** Open tasks whose due or escalation time has come and whose reminder or escalation has not been recorded yet. */
    List<ApprovalTaskId> findDue(Instant now);

    void save(ApprovalTask task);
}
