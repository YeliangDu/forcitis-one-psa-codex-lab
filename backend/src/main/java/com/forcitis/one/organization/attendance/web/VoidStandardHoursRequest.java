package com.forcitis.one.organization.attendance.web;

import org.jspecify.annotations.Nullable;

record VoidStandardHoursRequest(@Nullable String reason) {
    String requiredReason() {
        return PublishStandardHoursRequest.requiredText(reason, "reason");
    }
}
