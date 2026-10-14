package com.forcitis.one.delivery.execution.application;

import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.delivery.execution.api.WorkPackageQueries;
import com.forcitis.one.delivery.execution.domain.WorkPackage;
import com.forcitis.one.delivery.execution.domain.WorkPackageRepository;
import com.forcitis.one.kernel.Hours;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetRemainingHours implements WorkPackageQueries {

    private final WorkPackageRepository workPackages;

    GetRemainingHours(WorkPackageRepository workPackages) {
        this.workPackages = workPackages;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Hours> remainingHours(WorkPackageId workPackageId) {
        return workPackages.findById(workPackageId).map(WorkPackage::remainingHours);
    }
}
