package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.domain.ApprovalTaskQueries;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The latest tasks the person decided, with the opinion they gave. */
@Service
public class ListHandledTasks {

    static final int LIMIT = 100;

    private final ApprovalTaskQueries tasks;

    ListHandledTasks(ApprovalTaskQueries tasks) {
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<HandledTask> execute(UserId user) {
        return tasks.handledBy(user, LIMIT).stream()
                .map(record -> new HandledTask(record.taskId(), record.processKey(), record.document(), record.nodeKey(),
                        record.outcome().orElseThrow(), record.effect().orElseThrow(), record.reason(), record.completedAt().orElseThrow()))
                .toList();
    }
}
