package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads without locking: the tasks live inside the request aggregate, and these questions never change one. */
public interface ApprovalTaskQueries {

    Optional<TaskRecord> find(ApprovalTaskId taskId);

    Optional<ApprovalInstanceId> instanceOf(ApprovalTaskId taskId);

    /** Most recently decided first. */
    List<TaskRecord> handledBy(UserId user, int limit);

    /** Every open task, or only those past their deadline at {@code overdueAt}: the administrator's overview (D-1a-139). */
    List<TaskRecord> openTasks(Optional<Instant> overdueAt);

    /** Every task of one document, oldest first, whichever round it is from: the history shown next to a task. */
    List<TaskRecord> historyOf(ProcessKey processKey, String documentKey);

    /** The deadline of each given task that has one. */
    Map<ApprovalTaskId, Instant> dueAtOf(Collection<ApprovalTaskId> taskIds);

    /** Open tasks whose reminder or escalation time has come and has not been recorded yet. */
    List<ApprovalTaskId> findDue(Instant now);
}
