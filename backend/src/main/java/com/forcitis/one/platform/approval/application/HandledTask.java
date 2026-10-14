package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import java.time.Instant;
import java.util.Optional;

/** A task the person decided. reason is empty when the person gave none (RULE-280). */
public record HandledTask(ApprovalTaskId taskId, ProcessKey processKey, DocumentReference document, NodeKey nodeKey, TaskOutcome outcome,
                          TaskEffect effect, Optional<Reason> reason, Instant completedAt) {
}
