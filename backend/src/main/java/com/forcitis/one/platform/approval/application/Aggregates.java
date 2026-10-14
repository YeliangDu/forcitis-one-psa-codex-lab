package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.ApprovalTaskRepository;
import com.forcitis.one.platform.approval.domain.StaleAggregateException;
import org.springframework.stereotype.Component;

/** Loads aggregates (locked), saves them once per use case and publishes what they recorded in the same transaction. */
@Component
class Aggregates {

    private final ApprovalRequestRepository requests;
    private final ApprovalTaskRepository tasks;
    private final ApprovalEvents events;

    Aggregates(ApprovalRequestRepository requests, ApprovalTaskRepository tasks, ApprovalEvents events) {
        this.requests = requests;
        this.tasks = tasks;
        this.events = events;
    }

    ApprovalRequest requestOf(ApprovalInstanceId instanceId) {
        return requests.findByInstance(instanceId).orElseThrow(() -> new ApprovalOperationException(
                ApprovalError.PLATFORM_APPROVAL_REQUEST_NOT_FOUND, "No approval request for instance " + instanceId.value()));
    }

    ApprovalTask task(ApprovalTaskId taskId) {
        return tasks.find(taskId).orElseThrow(() -> new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND,
                "Task " + taskId.value() + " does not exist or is finished"));
    }

    void save(ApprovalRequest request) {
        try {
            requests.save(request);
        } catch (StaleAggregateException stale) {
            throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_CONCURRENT_CHANGE, stale.getMessage());
        }
        events.publish(request.pullEvents());
    }

    void save(ApprovalTask task) {
        try {
            tasks.save(task);
        } catch (StaleAggregateException stale) {
            throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_CONCURRENT_CHANGE, stale.getMessage());
        }
        events.publish(task.pullEvents());
    }
}
