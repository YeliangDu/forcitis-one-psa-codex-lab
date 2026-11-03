package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.RunOrgReconciliation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
class OrgReconciliationController {
    private final RunOrgReconciliation reconciliation;

    OrgReconciliationController(RunOrgReconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    /** The daily comparison on demand: snapshot against source, every difference listed. */
    @PostMapping(path = "/org-reconciliations:run")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_RECONCILIATION_EXECUTE + "')")
    OrgReconciliationResponse reconcile() {
        return OrgReconciliationResponse.of(reconciliation.execute());
    }
}
