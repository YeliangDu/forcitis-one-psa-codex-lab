package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository {

    /** Returns the row locked (FOR UPDATE) until the transaction ends, so concurrent starts of one document serialise. */
    ApprovalRequestRecord lockOrCreate(ProcessKey processKey, String businessKey, UserId initiator);

    Optional<ApprovalRequestRecord> lockByInstance(ApprovalInstanceId instanceId);

    void beginRound(ProcessKey processKey, String businessKey, int round, UserId initiator, ApprovalInstanceId instanceId);

    List<ApprovalRound> rounds(ProcessKey processKey, String businessKey);

    void updateStatus(ApprovalInstanceId instanceId, ApprovalStatus status, String reason);
}
