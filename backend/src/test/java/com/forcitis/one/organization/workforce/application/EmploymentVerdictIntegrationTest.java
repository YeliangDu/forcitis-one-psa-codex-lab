package com.forcitis.one.organization.workforce.application;

import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentVerdict;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EmploymentVerdictIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    OrgDirectory directory;

    @Autowired
    SyncOrgSnapshot sync;

    @Test
    @DisplayName("P2 recheck L-5 employmentOf on the database: no sync yet or a stale snapshot is unavailable, except for a person it lists as separated")
    void theVerdictFollowsTheSyncRuns() {
        EmployeeId employed = new EmployeeId(1);
        EmployeeId leaver = new EmployeeId(10);
        EmployeeId unknown = new EmployeeId(999);
        assertThat(directory.employmentOf(employed)).isEqualTo(EmploymentVerdict.SNAPSHOT_UNAVAILABLE);

        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 2, 100L, null), person(3, 2, 100L, null), person(4, 2, 100L, null),
                person(5, 2, 100L, null), person(6, 2, 100L, null), person(7, 2, 100L, null), person(8, 2, 100L, null),
                person(9, 2, 100L, null), person(10, 2, 100L, null));
        sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 2, 100L, null), person(3, 2, 100L, null), person(4, 2, 100L, null),
                person(5, 2, 100L, null), person(6, 2, 100L, null), person(7, 2, 100L, null), person(8, 2, 100L, null),
                person(9, 2, 100L, null), person(10, 2, 100L, null, null, "wx-10"));
        sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(directory.employmentOf(employed)).isEqualTo(EmploymentVerdict.EMPLOYED);
        assertThat(directory.employmentOf(leaver)).isEqualTo(EmploymentVerdict.SEPARATED);
        assertThat(directory.employmentOf(unknown)).isEqualTo(EmploymentVerdict.NOT_LISTED);

        clock.advance(Duration.ofMinutes(11));

        assertThat(directory.employmentOf(employed)).isEqualTo(EmploymentVerdict.SNAPSHOT_UNAVAILABLE);
        assertThat(directory.employmentOf(leaver)).isEqualTo(EmploymentVerdict.SEPARATED);
        assertThat(directory.employmentOf(unknown)).isEqualTo(EmploymentVerdict.SNAPSHOT_UNAVAILABLE);
    }
}
