package com.forcitis.one.delivery.execution.domain;

import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.Hours;
import java.math.BigDecimal;

public record WorkPackage(WorkPackageId workPackageId, Hours plannedHours, Hours actualHours, WorkPackageStatus status) {

    public Hours remainingHours() {
        BigDecimal remaining = plannedHours.value().subtract(actualHours.value());
        return remaining.signum() > 0 ? new Hours(remaining) : Hours.ZERO;
    }
}
