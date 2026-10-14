package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.domain.InstanceStart;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.audit.api.AuditEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts of one document are serialised, so concurrent starts give one instance; a return opens round + 1, and only the previous round's initiator may open it. */
@Service
public class StartApproval {

    private final ApprovalRequestRepository requests;
    private final ApprovalEngine engine;
    private final Aggregates aggregates;
    private final DenialAudit audit;
    private final BusinessTime time;

    StartApproval(ApprovalRequestRepository requests, ApprovalEngine engine, Aggregates aggregates, DenialAudit audit, BusinessTime time) {
        this.requests = requests;
        this.engine = engine;
        this.aggregates = aggregates;
        this.audit = audit;
        this.time = time;
    }

    @Transactional
    public ApprovalInstanceId execute(ApprovalStartRequest request) {
        requests.lockForStart(request.processKey(), request.document());
        Optional<ApprovalRequest> existing = requests.find(request.processKey(), request.document());
        if (existing.isPresent()) {
            ApprovalRequest current = existing.get();
            if (!current.initiator().equals(request.initiator())) {
                audit.record(new AuditEntry("platform.approval.start.denied", request.initiator(), null,
                        "approval-request:" + request.processKey() + ":" + current.aggregateId(), "not the initiator"));
                throw new AccessDeniedException("The approval of " + current.aggregateId() + " was started by another user");
            }
            if (current.status() == ApprovalStatus.RUNNING) {
                return current.instanceId();
            }
        }
        if (request.processKey().routingFlagRequired() && request.routingFlags().isEmpty()) {
            throw ApprovalError.PLATFORM_APPROVAL_NO_ROUTE.refusal("No routing flag is set: " + request.processKey() + " would route nowhere");
        }
        ApprovalInstanceId instanceId = engine.startInstance(new InstanceStart(request.processKey(), request.document(),
                request.legalEntityId(), request.initiator(), time.today(), request.contentDigest(), variablesOf(request)));
        ApprovalRequest aggregate;
        if (existing.isPresent()) {
            aggregate = existing.get();
            Transitions.run(() -> aggregate.restart(request.initiator(), instanceId));
        } else {
            aggregate = ApprovalRequest.begin(request.processKey(), request.document(), request.legalEntityId(), request.initiator(),
                    instanceId);
        }
        if (!engine.isRunning(instanceId)) {
            Transitions.run(() -> aggregate.approve(time.today()));
        }
        aggregates.save(aggregate);
        return instanceId;
    }

    private static ProcessVariables variablesOf(ApprovalStartRequest request) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        for (ApprovalField field : request.formFields()) {
            fields.put(field.name(), field.values());
        }
        Map<String, List<String>> variables = new LinkedHashMap<>();
        request.routingFlags().forEach(flag -> variables.put(flag, List.of("true")));
        return new ProcessVariables(fields, variables);
    }
}
