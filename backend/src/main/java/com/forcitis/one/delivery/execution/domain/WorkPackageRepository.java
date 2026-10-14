package com.forcitis.one.delivery.execution.domain;

import com.forcitis.one.delivery.execution.api.WorkPackageId;
import java.util.Optional;

public interface WorkPackageRepository {

    Optional<WorkPackage> findById(WorkPackageId id);
}
