package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.OrgReconciliation;
import com.forcitis.one.organization.workforce.domain.OrgReconciliationReport;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.platform.integration.api.OrgSource;
import org.springframework.stereotype.Service;

@Service
public class ReconcileOrgSnapshot {
    private final OrgSource source;
    private final OrgSnapshotRepository snapshots;

    ReconcileOrgSnapshot(OrgSource source, OrgSnapshotRepository snapshots) {
        this.source = source;
        this.snapshots = snapshots;
    }

    public OrgReconciliationReport execute() {
        return OrgReconciliation.compare(source.readSnapshot(), snapshots.loadAll());
    }
}
