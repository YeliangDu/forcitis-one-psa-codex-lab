package com.forcitis.one.organization.workforce.application;

import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentVerdict;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.forcitis.one.fixture.doubles.TestAccounts;

class EmploymentVerdictIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    OrgDirectory directory;

    @Test
    @DisplayName("P2 recheck L-5 employmentOf on the database: the snapshot row decides employed, separated or not listed; since #934 the snapshot has no age, so the verdict does not lapse")
    void theVerdictFollowsTheSnapshot() {
        EmployeeId employed = TestAccounts.employee(1);
        EmployeeId leaver = TestAccounts.employee(10);
        EmployeeId unknown = TestAccounts.employee(999);
        assertThat(directory.employmentOf(employed)).as("an empty snapshot lists nobody").isEqualTo(EmploymentVerdict.NOT_LISTED);

        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 2, 100L, null), person(3, 2, 100L, null), person(4, 2, 100L, null),
                person(5, 2, 100L, null), person(6, 2, 100L, null), person(7, 2, 100L, null), person(8, 2, 100L, null),
                person(9, 2, 100L, null), person(10, 2, 100L, null));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 2, 100L, null), person(3, 2, 100L, null), person(4, 2, 100L, null),
                person(5, 2, 100L, null), person(6, 2, 100L, null), person(7, 2, 100L, null), person(8, 2, 100L, null),
                person(9, 2, 100L, null), person(10, 2, 100L, null, null, "wx-10"));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);

        assertThat(directory.employmentOf(employed)).isEqualTo(EmploymentVerdict.EMPLOYED);
        assertThat(directory.employmentOf(leaver)).isEqualTo(EmploymentVerdict.SEPARATED);
        assertThat(directory.employmentOf(unknown)).isEqualTo(EmploymentVerdict.NOT_LISTED);

        clock.advance(Duration.ofMinutes(11));

        assertThat(directory.employmentOf(employed)).isEqualTo(EmploymentVerdict.EMPLOYED);
        assertThat(directory.employmentOf(leaver)).isEqualTo(EmploymentVerdict.SEPARATED);
        assertThat(directory.employmentOf(unknown)).isEqualTo(EmploymentVerdict.NOT_LISTED);
    }
}
