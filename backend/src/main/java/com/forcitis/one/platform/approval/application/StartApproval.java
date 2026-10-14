package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRecord;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalStatus;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.TaskView;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditTrail;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts the approval of one business document, idempotently (RULE-264/266/278). The unique key is (process type,
 * business key); the row is locked first, so concurrent starts of one document produce exactly one instance. The
 * initiator is whoever the OWNING MODULE passes (the current user it has verified owns the document); approval only
 * guarantees that nobody else can start or resubmit that document's approval afterwards (I-5, SEC-117).
 */
@Service
public class StartApproval {

    private final ApprovalRequestRepository requests;
    private final ApprovalEngine engine;
    private final ApprovalLifecycle lifecycle;
    private final AuditTrail audit;

    StartApproval(ApprovalRequestRepository requests, ApprovalEngine engine, ApprovalLifecycle lifecycle, AuditTrail audit) {
        this.requests = requests;
        this.engine = engine;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    @Transactional
    public ApprovalInstanceId execute(ApprovalRequest request) {
        ApprovalRequestRecord record = requests.lockOrCreate(request.processKey(), request.businessKey(), request.initiator());
        if (!record.created()) {
            if (!record.initiator().equals(request.initiator())) {
                audit.recordIndependently(new AuditEntry("platform.approval.start.denied", request.initiator(), null,
                        "approval-request:" + request.processKey() + ":" + request.businessKey(), "not the initiator"));
                throw new AccessDeniedException("The approval of " + request.businessKey() + " was started by another user");
            }
            if (record.status() == ApprovalStatus.RUNNING) {
                return record.instanceId();
            }
            if (record.status() == ApprovalStatus.RETURNED) {
                return resubmit(record, request);
            }
        }
        return startRound(record, request);
    }

    private ApprovalInstanceId startRound(ApprovalRequestRecord record, ApprovalRequest request) {
        int round = record.round() + 1;
        ApprovalInstanceId instanceId = engine.startInstance(request.processKey(), request.businessKey(), request.initiator(),
                variablesOf(request));
        requests.beginRound(request.processKey(), request.businessKey(), round, request.initiator(), instanceId);
        lifecycle.finishIfDone(new ApprovalRequestRecord(request.processKey(), request.businessKey(), request.initiator(),
                round, instanceId, ApprovalStatus.RUNNING, false), ApprovalStatus.RUNNING);
        return instanceId;
    }

    /** RULE-266/267: a document parked at its initiator is resumed (same instance) with the fresh values. */
    private ApprovalInstanceId resubmit(ApprovalRequestRecord record, ApprovalRequest request) {
        TaskView rework = engine.findOpenTasks(record.instanceId()).stream().filter(TaskView::initiatorNode).findFirst()
                .orElseThrow(() -> new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND,
                        "The returned approval has no initiator task"));
        engine.updateVariables(record.instanceId(), variablesOf(request));
        lifecycle.claim(rework, request.initiator());
        engine.completeTask(rework.taskId());
        lifecycle.resumed(record);
        lifecycle.publishTaskResult(rework, request.initiator(), TaskOutcome.APPROVED, null);
        lifecycle.finishIfDone(record, ApprovalStatus.RUNNING);
        return record.instanceId();
    }

    private static ProcessVariables variablesOf(ApprovalRequest request) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        for (ApprovalField field : request.formFields()) {
            fields.put(field.name(), field.values());
        }
        Map<String, List<String>> variables = new LinkedHashMap<>();
        variables.put("initiatorId", List.of(String.valueOf(request.initiator().value())));
        request.routingFlags().forEach(flag -> variables.put(flag, List.of("true")));
        return new ProcessVariables(fields, variables);
    }
}
