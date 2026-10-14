package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.Optional;

/** Port for approval_requests and start_rounds (JDBC in infrastructure.persistence). */
public interface ApprovalRequestRepository {

    /**
     * Inserts the row if absent and returns it locked (FOR UPDATE) until the transaction ends, so concurrent starts of
     * the same document serialise (RULE-264/266). The unique key is (processKey, businessKey).
     */
    ApprovalRequestRecord lockOrCreate(ProcessKey processKey, String businessKey, UserId initiator);

    /** Locks the request of an instance for the rest of the transaction; empty when the instance is unknown. */
    Optional<ApprovalRequestRecord> lockByInstance(ApprovalInstanceId instanceId);

    /** Records round number {@code round} with its instance (start_rounds unique on type, key, round) and sets RUNNING. */
    void beginRound(ProcessKey processKey, String businessKey, int round, UserId initiator, ApprovalInstanceId instanceId);

    void updateStatus(ApprovalInstanceId instanceId, ApprovalStatus status, String reason);
}
