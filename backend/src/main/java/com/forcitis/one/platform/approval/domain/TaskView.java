package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/** assignee is empty until a candidate claims it; insideSubProcess decides what a reject means; advisory marks an added co-signer's task. */
public record TaskView(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, DocumentReference document,
                       LegalEntityId legalEntityId, String nodeKey, String nodeName, EmployeeId initiator, Optional<UserId> assignee,
                       Set<UserId> candidates, Instant createdAt, boolean insideSubProcess, Optional<String> beforeHookKey,
                       boolean advisory, ContentHash contentHash) {

    public TaskView {
        candidates = Set.copyOf(candidates);
    }

    public boolean isClaimed() {
        return assignee.isPresent();
    }
}
