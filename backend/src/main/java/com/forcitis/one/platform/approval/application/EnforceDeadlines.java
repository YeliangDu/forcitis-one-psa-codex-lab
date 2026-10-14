package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalTaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Not transactional itself: a task that fails is counted and logged, and the others still get their turn. */
@Service
public class EnforceDeadlines {

    private static final Logger log = LoggerFactory.getLogger(EnforceDeadlines.class);

    private final ApprovalTaskRepository tasks;
    private final TaskDeadline deadline;
    private final MeterRegistry meters;
    private final BusinessTime time;

    EnforceDeadlines(ApprovalTaskRepository tasks, TaskDeadline deadline, MeterRegistry meters, BusinessTime time) {
        this.tasks = tasks;
        this.deadline = deadline;
        this.meters = meters;
        this.time = time;
    }

    public int execute() {
        int handled = 0;
        for (ApprovalTaskId id : tasks.findDue(time.now())) {
            try {
                deadline.enforce(id);
                handled++;
            } catch (RuntimeException failed) {
                log.error("Enforcing the deadline of task {} failed: {}", id.value(), failed.toString());
                meters.counter("platform.approval.deadline.failed").increment();
            }
        }
        return handled;
    }
}
