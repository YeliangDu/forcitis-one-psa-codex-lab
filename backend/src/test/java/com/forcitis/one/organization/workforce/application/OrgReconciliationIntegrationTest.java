package com.forcitis.one.organization.workforce.application;

import static com.forcitis.one.organization.workforce.domain.Fixtures.member;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.domain.OrgReconciliationReport;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * OrgReconciliationIT (exit criterion "组织零差异"): people count, org unit count and reporting relations of the stored
 * snapshot against A8 now. Named *Test so Surefire runs it in the default build.
 */
class OrgReconciliationIntegrationTest extends WorkforceIntegrationSupport {

    @Autowired
    SyncOrgSnapshot sync;

    @Autowired
    ReconcileOrgSnapshot reconcile;

    @Test
    @DisplayName("组织零差异: after a sync the stored snapshot equals A8 in people, org units and reporting relations")
    void zeroDifferenceAfterSync() {
        a8.snapshot = tree(member(1, 2, 100L, null), member(2, 3, 101L, 1L), member(3, 3, 101L, 1L), member(4, 2, 100L, null, null, null));
        sync.execute(SyncTrigger.SCHEDULED, false);

        OrgReconciliationReport report = reconcile.execute();

        assertThat(report.isZeroDifference()).isTrue();
        assertThat(report.sourceEmployedCount()).isEqualTo(3);
        assertThat(report.snapshotEmployedCount()).isEqualTo(3);
        assertThat(report.sourceOrgUnitCount()).isEqualTo(3);
        assertThat(report.snapshotOrgUnitCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("组织零差异: a change in A8 that is not synced yet, and a hand edit of the snapshot, both show up as differences")
    void driftIsVisible() {
        a8.snapshot = tree(member(1, 2, 100L, null), member(2, 3, 101L, 1L), member(3, 3, 101L, 1L));
        sync.execute(SyncTrigger.SCHEDULED, false);

        a8.snapshot = tree(member(1, 2, 100L, null), member(2, 3, 101L, 3L), member(3, 3, 101L, 1L), member(4, 2, 100L, null));
        OrgReconciliationReport beforeNextSync = reconcile.execute();
        assertThat(beforeNextSync.isZeroDifference()).isFalse();
        assertThat(beforeNextSync.missingInSnapshot()).containsExactly("employee:4");
        assertThat(beforeNextSync.reportingLineMismatches()).containsExactly("employee:2");
        assertThat(beforeNextSync.sourceEmployedCount()).isEqualTo(4);
        assertThat(beforeNextSync.snapshotEmployedCount()).isEqualTo(3);

        sync.execute(SyncTrigger.SCHEDULED, false);
        assertThat(reconcile.execute().isZeroDifference()).isTrue();

        jdbc.sql("update organization_workforce.org_units set parent_org_unit_id = 1 where id = 3").update();
        assertThat(reconcile.execute().parentMismatches()).containsExactly("org-unit:3");
    }
}
