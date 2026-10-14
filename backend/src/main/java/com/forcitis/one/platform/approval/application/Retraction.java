package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RetractionResult;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Withdraw and cancel share one check, the same one the "can withdraw" display uses (RULE-269, RULE-268, RULE-309). */
@Component
class Retraction {

    private final ApprovalRequestRepository requests;
    private final ApprovalEngine engine;
    private final Approvals approvals;
    private final DenialAudit denials;
    private final BusinessTime time;

    Retraction(ApprovalRequestRepository requests, ApprovalEngine engine, Approvals approvals, DenialAudit denials, BusinessTime time) {
        this.requests = requests;
        this.engine = engine;
        this.approvals = approvals;
        this.denials = denials;
        this.time = time;
    }

    WithdrawCheck check(ProcessKey processKey, DocumentReference document, UserId actor) {
        return requests.find(processKey, document).map(request -> request.checkWithdraw(actor, cutoffPassed(request)))
                .orElse(WithdrawCheck.NOT_RUNNING);
    }

    RetractionResult withdraw(ProcessKey processKey, DocumentReference document, UserId actor, String reason) {
        Optional<ApprovalRequest> found = requests.findForUpdate(processKey, document);
        if (found.isEmpty()) {
            return RetractionResult.NOT_RUNNING;
        }
        ApprovalRequest request = found.get();
        requireInitiator(request, actor);
        boolean cutoff = cutoffPassed(request);
        RetractionResult refusal = refusal(request, actor, cutoff, reason);
        if (refusal != null) {
            return refusal;
        }
        request.withdraw(actor, cutoff, Reason.ofNullable(reason), time.moment());
        engine.endAsReturned(request.instanceId());
        approvals.save(request);
        return RetractionResult.DONE;
    }

    RetractionResult cancel(ProcessKey processKey, DocumentReference document, UserId actor, String reason) {
        Optional<ApprovalRequest> found = requests.findForUpdate(processKey, document);
        if (found.isEmpty()) {
            return RetractionResult.NOT_RUNNING;
        }
        ApprovalRequest request = found.get();
        requireInitiator(request, actor);
        boolean cutoff = cutoffPassed(request);
        RetractionResult refusal = refusal(request, actor, cutoff, reason);
        if (refusal != null) {
            return refusal;
        }
        request.cancel(actor, cutoff, Reason.ofNullable(reason), time.moment());
        engine.terminate(request.instanceId(), "CANCELLED");
        approvals.save(request);
        return RetractionResult.DONE;
    }

    private static RetractionResult refusal(ApprovalRequest request, UserId actor, boolean cutoff, String reason) {
        return switch (request.checkWithdraw(actor, cutoff)) {
            case NOT_RUNNING -> RetractionResult.NOT_RUNNING;
            case CUTOFF_PASSED -> RetractionResult.CUTOFF_PASSED;
            case NOT_INITIATOR, ALLOWED -> reason == null || reason.isBlank() ? RetractionResult.REASON_REQUIRED : null;
        };
    }

    private void requireInitiator(ApprovalRequest request, UserId actor) {
        if (!request.initiator().equals(actor)) {
            denials.record(new AuditEntry("platform.approval.request.denied", actor, null, "approval-request:" + request.aggregateId(),
                    "not the initiator"));
            throw new AccessDeniedException("User " + actor.value() + " did not start the approval of " + request.aggregateId());
        }
    }

    private boolean cutoffPassed(ApprovalRequest request) {
        return request.status() == ApprovalStatus.RUNNING && engine.cutoffPassed(request.instanceId());
    }
}
