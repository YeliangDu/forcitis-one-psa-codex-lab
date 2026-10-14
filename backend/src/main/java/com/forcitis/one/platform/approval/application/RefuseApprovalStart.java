package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A start broke a business rule and the engine was not asked to run anything: the round is recorded and ends as returned to its
 * initiator with the reason. Asking again for a round that is already recorded changes nothing.
 */
@Service
public class RefuseApprovalStart {

    private final ApprovalRequestRepository requests;
    private final Approvals approvals;
    private final BusinessTime time;

    RefuseApprovalStart(ApprovalRequestRepository requests, Approvals approvals, BusinessTime time) {
        this.requests = requests;
        this.approvals = approvals;
        this.time = time;
    }

    @Transactional
    public void execute(ApprovalStartRequest request, String reason) {
        requests.lockForStart(request.processKey(), request.document());
        Optional<ApprovalRequest> existing = requests.findForUpdate(request.processKey(), request.document());
        if (existing.isPresent() && existing.get().round() >= request.round().value()) {
            return;
        }
        int expected = existing.map(current -> current.round() + 1).orElse(1);
        if (request.round().value() != expected) {
            throw ApprovalError.PLATFORM_APPROVAL_ROUND_MISMATCH.refusal("Round " + request.round().value() + " was asked for, round "
                    + expected + " is next");
        }
        ApprovalRequest aggregate = approvals.openRound(request, existing);
        aggregate.returnRound(new Reason(reason), time.moment());
        approvals.save(aggregate);
    }
}
