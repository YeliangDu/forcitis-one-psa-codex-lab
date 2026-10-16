package com.forcitis.one.platform.identity;

import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.kernel.SeparationReview;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("SyncLockoutGuard (identity side): ADR-0027 section 1 item 7, Q-3")
class AdministratorSeparationReviewTest extends IdentityIntegrationTest {

    @Autowired
    SeparationReview review;

    private void holder(long id, String... roles) {
        knownEmployee(id, EmploymentStatus.EMPLOYED);
        createAccount(id, roles);
    }

    @Test
    @DisplayName("separating people who hold neither super_admin nor the security auditor role needs no confirmation")
    void ordinarySeparationsGoAhead() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(3, "employee");

        assertThat(review.review(List.of(TestAccounts.employee(3), TestAccounts.employee(404)), null).separatedAdministrators()).isEmpty();
        assertThat(review.review(List.of(), null).separatedAdministrators()).isEmpty();
    }

    @Test
    @DisplayName("separating a super_admin holder waits for a person even when enough holders remain")
    void aSuperAdministratorSeparationWaits() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(3, "super_admin");

        assertThat(review.review(List.of(TestAccounts.employee(1)), null).reason())
                .contains("1 separated person(s)").contains("another holder must confirm").doesNotContain("would remain");
    }

    @Test
    @DisplayName("a run that would leave fewer than two usable super_admin holders says so")
    void fewerThanTwoRemainingIsNamed() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(3, "employee");

        assertThat(review.review(List.of(TestAccounts.employee(1), TestAccounts.employee(3)), null).reason())
                .contains("only 1 usable super_admin holder(s) would remain");
        assertThat(review.review(List.of(TestAccounts.employee(1), TestAccounts.employee(2)), null).reason())
                .contains("only 0 usable");
    }

    @Test
    @DisplayName("a security auditor's separation waits too")
    void anAuditorSeparationWaits() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(5, "security_auditor");

        assertThat(review.review(List.of(TestAccounts.employee(5)), null).separatedAdministrators()).containsExactly(TestAccounts.employee(5));
    }

    @Test
    @DisplayName("holders who are not employed, or whose account or role is off, do not count as usable")
    void unusableHoldersDoNotCount() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(3, "super_admin");
        knownEmployee(3, EmploymentStatus.SEPARATED);
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where employee_id = " + TestOrgIds.sqlEmployee(2)).update();

        assertThat(review.review(List.of(TestAccounts.employee(1)), null).reason()).contains("only 0 usable");
    }

    @Test
    @DisplayName("only a remaining super_admin holder or security auditor may release such a hold, never a person being separated")
    void whoMayRelease() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(5, "security_auditor");
        holder(7, "org_data_admin");
        List<EmployeeId> separating = List.of(TestAccounts.employee(1));

        assertThat(review.review(separating, TestAccounts.account(2)).actorRetained()).isTrue();
        assertThat(review.review(separating, TestAccounts.account(5)).actorRetained()).isTrue();
        assertThat(review.review(separating, TestAccounts.account(1)).actorRetained()).as("not the person leaving").isFalse();
        assertThat(review.review(separating, TestAccounts.account(7)).actorRetained()).as("the organisation data administrator alone is not enough").isFalse();
        assertThat(review.review(separating, TestAccounts.account(404)).actorRetained()).isFalse();
    }
}
