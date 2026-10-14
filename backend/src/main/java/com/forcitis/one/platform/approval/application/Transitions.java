package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.domain.ApprovalTransitionException;

final class Transitions {

    private Transitions() {
    }

    static void run(Runnable transition) {
        try {
            transition.run();
        } catch (ApprovalTransitionException refused) {
            throw new ApprovalOperationException(switch (refused.violation()) {
                case ILLEGAL_TRANSITION -> ApprovalError.PLATFORM_APPROVAL_TRANSITION_ILLEGAL;
                case NOT_INITIATOR -> ApprovalError.PLATFORM_APPROVAL_NOT_INITIATOR;
                case REASON_REQUIRED -> ApprovalError.PLATFORM_APPROVAL_REASON_REQUIRED;
                case CUTOFF_PASSED -> ApprovalError.PLATFORM_APPROVAL_CUTOFF_PASSED;
            }, refused.getMessage());
        }
    }
}
