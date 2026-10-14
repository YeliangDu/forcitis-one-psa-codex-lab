package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.OrgReconciliation;
import com.forcitis.one.organization.workforce.domain.OrgReconciliationReport;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.OrgSource;
import org.springframework.stereotype.Service;

/**
 * Exit criterion "组织零差异": read the source now, compare with the stored snapshot - people count, org unit count and the
 * reporting relations (org unit parent, person's direct leader). Read-only; the source read happens outside any transaction.
 */
@Service
public class ReconcileOrgSnapshot {

    private final OrgSource source;
    private final OrgSnapshotRepository snapshots;

    ReconcileOrgSnapshot(OrgSource source, OrgSnapshotRepository snapshots) {
        this.source = source;
        this.snapshots = snapshots;
    }

    public OrgReconciliationReport execute() {
        return OrgReconciliation.compare(source.fetchAll(), snapshots.loadAll());
    }
}
