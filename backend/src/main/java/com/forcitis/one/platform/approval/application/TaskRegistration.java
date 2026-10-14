package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

/** deadlineWorkdays, escalationWorkdays and autoApproveRepeat come from the node's decision table; the deadline numbers are both given or both absent. */
public record TaskRegistration(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, String processName,
                               DocumentReference document, LegalEntityId legalEntityId, String nodeKey, String nodeName,
                               UserId initiator, List<UserId> candidates, LocalDate submittedOn, OptionalInt deadlineWorkdays,
                               OptionalInt escalationWorkdays, boolean autoApproveRepeat, String contentDigest) {

    public TaskRegistration {
        candidates = List.copyOf(candidates);
        if (deadlineWorkdays.isPresent() != escalationWorkdays.isPresent()) {
            throw new IllegalArgumentException("a node deadline needs both the deadline and the escalation interval");
        }
    }
}
