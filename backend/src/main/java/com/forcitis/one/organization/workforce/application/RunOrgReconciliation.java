package com.forcitis.one.organization.workforce.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The manual trigger of the comparison: reads the source now and reports every difference as the problem list. */
@Service
public class RunOrgReconciliation {
    private final ReconcileOrgSnapshot reconcile;

    RunOrgReconciliation(ReconcileOrgSnapshot reconcile) {
        this.reconcile = reconcile;
    }

    @Transactional(readOnly = true)
    public OrgReconciliationView execute() {
        return OrgReconciliationView.of(reconcile.execute());
    }
}
