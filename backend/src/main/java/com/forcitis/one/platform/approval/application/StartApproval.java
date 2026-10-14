package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRecord;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalStatus;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
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
 * guarantees that nobody else can start a new round of that document's approval afterwards (I-5, SEC-117).
 *
 * <p>Rounds (RULE-278/287): a return ends the round, so a document in status RETURNED opens round + 1 with a new
 * instance; the idempotency key is (process, document, round) - the unique key of start_rounds - so a repeated call of
 * the same start returns the running instance instead of opening another round. Only the initiator of the previous round
 * may open the next one.
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
