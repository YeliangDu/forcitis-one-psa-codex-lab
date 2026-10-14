package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(review.holdReason(List.of(new UserId(3), new UserId(404)), null)).isEmpty();
        assertThat(review.holdReason(List.of(), null)).isEmpty();
    }

    @Test
    @DisplayName("separating a super_admin holder waits for a person even when enough holders remain")
    void aSuperAdministratorSeparationWaits() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(3, "super_admin");

        assertThat(review.holdReason(List.of(new UserId(1)), null)).hasValueSatisfying(reason -> assertThat(reason)
                .contains("1 separated person(s)").contains("another holder must confirm").doesNotContain("would remain"));
    }

    @Test
    @DisplayName("a run that would leave fewer than two usable super_admin holders says so")
    void fewerThanTwoRemainingIsNamed() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(3, "employee");

        assertThat(review.holdReason(List.of(new UserId(1), new UserId(3)), null)).hasValueSatisfying(reason -> assertThat(reason)
                .contains("only 1 usable super_admin holder(s) would remain"));
        assertThat(review.holdReason(List.of(new UserId(1), new UserId(2)), null)).hasValueSatisfying(reason -> assertThat(reason)
                .contains("only 0 usable"));
    }

    @Test
    @DisplayName("a security auditor's separation waits too")
    void anAuditorSeparationWaits() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(5, "security_auditor");

        assertThat(review.holdReason(List.of(new UserId(5)), null)).isPresent();
    }

    @Test
    @DisplayName("holders who are not employed, or whose account or role is off, do not count as usable")
    void unusableHoldersDoNotCount() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(3, "super_admin");
        knownEmployee(3, EmploymentStatus.SEPARATED);
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where user_id = 2").update();

        assertThat(review.holdReason(List.of(new UserId(1)), null)).hasValueSatisfying(reason -> assertThat(reason).contains("only 0 usable"));
    }

    @Test
    @DisplayName("only a remaining super_admin holder or security auditor may release such a hold, never a person being separated")
    void whoMayRelease() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(5, "security_auditor");
        holder(7, "org_data_admin");
        List<UserId> separating = List.of(new UserId(1));

        assertThat(review.holdReason(separating, new UserId(2))).isEmpty();
        assertThat(review.holdReason(separating, new UserId(5))).isEmpty();
        assertThat(review.holdReason(separating, new UserId(1))).as("not the person leaving").isPresent();
        assertThat(review.holdReason(separating, new UserId(7))).as("the organisation data administrator alone is not enough").isPresent();
        assertThat(review.holdReason(separating, new UserId(404))).isPresent();
    }
}
