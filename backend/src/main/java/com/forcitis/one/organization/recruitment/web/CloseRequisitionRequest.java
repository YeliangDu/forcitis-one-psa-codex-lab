package com.forcitis.one.organization.recruitment.web;

import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import org.jspecify.annotations.Nullable;

record CloseRequisitionRequest(@Nullable CloseReasonKind closeReasonKind) {

    CloseReasonKind required() {
        if (closeReasonKind == null) {
            throw new InvalidRequestException("closeReasonKind is required");
        }
        return closeReasonKind;
    }
}
