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
import java.util.UUID;

/** Reads without locking: the tasks live inside the request aggregate, and these questions never change one. */
public interface ApprovalTaskQueries {

    Optional<TaskRecord> find(ApprovalTaskId taskId);

    Optional<ApprovalInstanceId> instanceOf(ApprovalTaskId taskId);

    /** What the person did to tasks (not what the system did for them), newest first, starting after the given place; one more than the limit when there is a next page. */
    List<TaskHandling> handledBy(UserId user, Optional<Instant> after, Optional<UUID> afterId, int limit);

    /**
     * Open tasks oldest first, or only those past their deadline at {@code overdueAt}: the administrator's overview (D-1a-139). A page
     * starts after the given creation time and task id; the limit is the number of rows asked for.
     */
    List<TaskRecord> openTasks(Optional<Instant> overdueAt, Optional<Instant> after, Optional<ApprovalTaskId> afterId, int limit);

    /** Everything done to the tasks of one document, oldest first, whichever round it is from: the history shown next to a task. */
    List<TaskHandling> historyOf(ProcessKey processKey, String documentKey);

    /** The deadline of each given task that has one. */
    Map<ApprovalTaskId, Instant> dueAtOf(Collection<ApprovalTaskId> taskIds);

    /** Open tasks whose reminder or escalation time has come and has not been recorded yet. */
    List<ApprovalTaskId> findDue(Instant now);
}
