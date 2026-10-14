package com.forcitis.one.delivery.execution.api;

import com.forcitis.one.kernel.Hours;
import java.util.Optional;

public interface WorkPackageQueries {

    /** Empty when the task package does not exist - callers must decide, never a silent zero. */
    Optional<Hours> remainingHours(WorkPackageId workPackageId);
}
