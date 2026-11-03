package com.forcitis.one.organization.workforce.application;

import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlOrgUnit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.manager;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.organization.workforce.domain.OrgReconciliationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Named *Test so Surefire runs it in the default build. */
class OrgReconciliationIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    ReconcileOrgSnapshot reconcile;

    @Test
    @DisplayName("组织零差异: after a sync the stored snapshot equals the source in people, org units and reporting relations")
    void zeroDifferenceAfterSync() {
        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 1L), person(4, 2, 100L, null, null, null));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);

        OrgReconciliationReport report = reconcile.execute();

        assertThat(report.isZeroDifference()).isTrue();
        assertThat(report.sourceEmployedCount()).isEqualTo(3);
        assertThat(report.snapshotEmployedCount()).isEqualTo(3);
        assertThat(report.sourceOrgUnitCount()).isEqualTo(3);
        assertThat(report.snapshotOrgUnitCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("组织零差异: a change in the source that is not synced yet, and a hand edit of the snapshot, both show up as differences")
    void driftIsVisible() {
        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 1L));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);

        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 3, 101L, 3L), person(3, 3, 101L, 1L), person(4, 2, 100L, null));
        OrgReconciliationReport beforeNextSync = reconcile.execute();
        assertThat(beforeNextSync.isZeroDifference()).isFalse();
        assertThat(beforeNextSync.missingInSnapshot()).containsExactly("employee:" + TestAccounts.employee(4).value());
        assertThat(beforeNextSync.reportingLineMismatches()).containsExactly("employee:" + TestAccounts.employee(2).value());
        assertThat(beforeNextSync.sourceEmployedCount()).isEqualTo(4);
        assertThat(beforeNextSync.snapshotEmployedCount()).isEqualTo(3);

        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        assertThat(reconcile.execute().isZeroDifference()).isTrue();

        jdbc.sql("update organization_workforce.org_units set parent_org_unit_id = " + sqlOrgUnit(1) + " where id = " + sqlOrgUnit(3)).update();
        assertThat(reconcile.execute().parentMismatches()).containsExactly("org-unit:" + TestOrgIds.orgUnit(3).value());
    }

    @Test
    @DisplayName("D-1a-10 the comparison covers heads, regional receivables managers and WeCom ids too; the count of differences is what the daily alert reports")
    void headsManagersAndWecomIdsAreCompared() {
        orgSource.snapshot = source(java.util.List.of(unit(1, null), unit(2, 1L)), java.util.List.of(head(2, 1), manager(2, 1)),
                java.util.List.of(post(100)), java.util.List.of(person(1, 2, 100L, null, "13900000001", "wx-1"), person(2, 2, 100L, null, "13900000002", "wx-2")));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        assertThat(reconcile.execute().isZeroDifference()).isTrue();

        orgSource.snapshot = source(java.util.List.of(unit(1, null), unit(2, 1L)), java.util.List.of(head(2, 2), manager(2, 2)),
                java.util.List.of(post(100)), java.util.List.of(person(1, 2, 100L, null, "13900000001", "wx-1-new"), person(2, 2, 100L, null, "13900000002", "wx-2")));
        OrgReconciliationReport drift = reconcile.execute();

        assertThat(drift.responsibilityMismatches()).containsExactly("org-unit:" + TestOrgIds.orgUnit(2).value());
        assertThat(drift.wecomUserIdMismatches()).containsExactly("employee:" + TestAccounts.employee(1).value());
        assertThat(drift.differenceCount()).isEqualTo(2);
        assertThat(drift.isZeroDifference()).isFalse();
    }
}
