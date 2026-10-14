package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.Moment;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/**
 * What the use cases share. Every change runs in the caller's transaction, so the engine change, the request with its tasks and
 * the events commit together; the request row is locked first and everything else changes under that lock.
 */
@Component
class Approvals {

    private final ApprovalRequestRepository requests;
    private final ApprovalEngine engine;
    private final DomainEvents events;
    private final BusinessTime time;

    Approvals(ApprovalRequestRepository requests, ApprovalEngine engine, DomainEvents events, BusinessTime time) {
        this.requests = requests;
        this.engine = engine;
        this.events = events;
        this.time = time;
    }

    ApprovalRequest lock(ApprovalInstanceId instanceId) {
        return requests.findByInstanceForUpdate(instanceId).orElseThrow(
                () -> ApprovalError.PLATFORM_APPROVAL_REQUEST_NOT_FOUND.refusal("No approval request for instance " + instanceId.value()));
    }

    /** Strong comparison with the version of the request as locked; a caller that sent none (a job, another module) is not held to one. */
    void requireVersion(ApprovalRequest request, OptionalLong expected) {
        if (expected.isPresent() && expected.getAsLong() != request.version()) {
            throw new com.forcitis.one.kernel.VersionMismatchException("Approval " + request.aggregateId() + " is at version " + request.version() + ", the caller read "
                    + expected.getAsLong());
        }
    }

    /** The aggregate of the round a start asks for: a new request, or the existing one opened again. */
    ApprovalRequest openRound(ApprovalStartRequest request, Optional<ApprovalRequest> existing) {
        ApprovalInstanceId instanceId = new ApprovalInstanceId(Uuids.timeOrdered(time.clock()).toString());
        Moment now = time.moment();
        if (existing.isPresent()) {
            existing.get().restart(request.initiator(), instanceId, request.summary(), request.trigger(), now);
            return existing.get();
        }
        return ApprovalRequest.begin(request.processKey(), request.document(), request.legalEntityId(), request.initiator(), instanceId,
                request.summary(), request.trigger(), now);
    }

    /** A lost race against another candidate is a refusal (409), not a server error. */
    void claim(ApprovalRequest request, TaskView view, UserId actor) {
        if (!engine.claimTask(view.taskId(), actor)) {
            throw ApprovalError.PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED.refusal("Task " + view.taskId().value() + " was claimed by somebody else");
        }
        request.claimTask(view.taskId(), actor, time.moment());
    }

    /** The request approves itself when the engine has no live instance left. */
    ApprovalStatus finishIfDone(ApprovalRequest request) {
        if (!engine.isRunning(request.instanceId())) {
            request.approve(time.moment());
        }
        return request.status();
    }

    /** A return ends the round, the initiator starts a new one; history stays. */
    ApprovalStatus returned(ApprovalRequest request, Reason reason) {
        engine.endAsReturned(request.instanceId());
        request.returnRound(reason, time.moment());
        return request.status();
    }

    ApprovalStatus reject(ApprovalRequest request, Reason reason) {
        engine.terminate(request.instanceId(), "REJECTED");
        request.reject(reason, time.moment());
        return request.status();
    }

    void save(ApprovalRequest request) {
        requests.save(request);
        events.publish(request);
    }
}
