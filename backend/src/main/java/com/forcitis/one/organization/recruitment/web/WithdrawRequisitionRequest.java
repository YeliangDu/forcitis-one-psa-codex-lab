package com.forcitis.one.organization.recruitment.web;

import com.forcitis.one.kernel.InvalidRequestException;
import org.jspecify.annotations.Nullable;

record WithdrawRequisitionRequest(@Nullable String reason) {

    String requiredReason() {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("reason is required");
        }
        return reason;
    }
}
