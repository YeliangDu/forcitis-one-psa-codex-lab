package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.DeadlinePlan;
import com.forcitis.one.platform.approval.domain.Moment;
import com.forcitis.one.platform.approval.domain.TaskAssignment;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The engine creates tasks while a use case drives it; the use case opens a scope with the request it is changing, and every task
 * the engine creates meanwhile becomes part of that request. A task created outside any scope fails the command.
 */
@Component
public class TaskRegistrar {

    private final DeadlinePlanner deadlines;
    private final BusinessTime time;
    private final ThreadLocal<ApprovalRequest> current = new ThreadLocal<>();

    TaskRegistrar(DeadlinePlanner deadlines, BusinessTime time) {
        this.deadlines = deadlines;
        this.time = time;
    }

    public Scope open(ApprovalRequest request) {
        if (current.get() != null) {
            throw new IllegalStateException("Another approval request is being changed on this thread");
        }
        current.set(request);
        return current::remove;
    }

    /** True when the system passes the task itself: the only candidate already approved this node with exactly this content (RULE-302). */
    public boolean register(TaskRegistration registration) {
        ApprovalRequest request = current.get();
        if (request == null) {
            throw new IllegalStateException("Task " + registration.taskId().value() + " was created outside a use case that owns its request");
        }
        Moment now = time.moment();
        if (registration.autoApproveRepeat() && registration.candidates().size() == 1
                && request.isRepeatApprover(registration.nodeKey(), registration.candidates().getFirst(), registration.contentHash())) {
            request.passAutomatically(assignment(registration, Optional.empty()), registration.candidates().getFirst(), now);
            return true;
        }
        request.assignTask(assignment(registration, registration.deadline().map(node -> deadlines.plan(node, request.legalEntityId()))), now);
        return false;
    }

    private static TaskAssignment assignment(TaskRegistration registration, Optional<DeadlinePlan> plan) {
        return new TaskAssignment(registration.taskId(), registration.instanceId(), registration.nodeKey(), registration.nodeName(),
                registration.processName(), registration.candidates(), plan);
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
