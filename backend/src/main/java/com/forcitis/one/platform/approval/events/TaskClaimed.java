package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;

/** A candidate claimed the task: from now on only that person may act (I-7, RULE-276); other cards become "handled". */
public record TaskClaimed(ApprovalTaskId taskId, UserId claimedBy) {
}
