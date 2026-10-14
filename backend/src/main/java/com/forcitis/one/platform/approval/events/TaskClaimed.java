package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;

/** From now on only that person may act (I-7); other cards become handled. */
public record TaskClaimed(ApprovalTaskId taskId, UserId claimedBy) {
}
