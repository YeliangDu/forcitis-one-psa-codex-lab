package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.platform.approval.api.RetractionResult;

final class Retractions {

    private Retractions() {
    }

    static void require(RetractionResult result) {
        switch (result) {
            case DONE -> {
            }
            case REASON_REQUIRED -> throw KernelError.KERNEL_REASON_REQUIRED.refusal("A reason is required");
            case NOT_RUNNING -> throw KernelError.KERNEL_TRANSITION_ILLEGAL.refusal("The requisition is not in a running approval");
            case CUTOFF_PASSED -> throw KernelError.KERNEL_TRANSITION_ILLEGAL.refusal("The approval can no longer be withdrawn");
        }
    }
}
