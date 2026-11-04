package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.SeparationReview.Verdict;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The super_admin minimum of the synchronised-run hold changes one number in the reason text and nothing else. */
class SingleOperatorSeparationReasonIT extends SingleOperatorIdentitySupport {

    @Autowired
    SeparationReview review;

    private void holder(long id, String... roles) {
        knownEmployee(id, EmploymentStatus.EMPLOYED);
        createAccount(id, roles);
    }

    private static EmployeeId employeeId(long id) {
        return TestAccounts.employee(id);
    }

    private Verdict verdict(boolean modeOn, List<EmployeeId> separating, UserId releaser) {
        mode(modeOn);
        return review.review(separating, releaser);
    }

    private static void assertSameApartFromTheReason(Verdict on, Verdict off) {
        assertThat(on.separatedAdministrators()).isEqualTo(off.separatedAdministrators());
        assertThat(on.actorRetained()).isEqualTo(off.actorRetained());
        assertThat(on.remainingAdministrators()).isEqualTo(off.remainingAdministrators());
        assertThat(on.sessionMode()).isEqualTo(off.sessionMode());
    }

    // RULE-436
    @Test
    @DisplayName("SINGLE-OP P2-15 同一输入（两名超管都离职，剩 0 个）：模式开启与关闭给出相同的 separatedAdministrators、actorRetained、remaining；只有 reason 里的下限不同（开启 at least 1，关闭 at least 2）")
    void p215_onlyTheMinimumInTheReasonDiffers() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        List<EmployeeId> separating = List.of(employeeId(1), employeeId(2));

        Verdict off = verdict(false, separating, null);
        Verdict on = verdict(true, separating, null);

        assertSameApartFromTheReason(on, off);
        assertThat(off.separatedAdministrators()).containsExactlyInAnyOrder(employeeId(1), employeeId(2));
        assertThat(off.remainingAdministrators()).isZero();
        assertThat(off.reason()).contains("only 0 usable super_admin holder(s) would remain, at least 2 are needed");
        assertThat(on.reason()).contains("only 0 usable super_admin holder(s) would remain, at least 1 are needed").doesNotContain("at least 2");
        assertThat(on.reason().replace("at least 1", "at least 2")).as("nothing else in the text differs").isEqualTo(off.reason());
    }

    // RULE-436
    @Test
    @DisplayName("SINGLE-OP P2-15 剩 1 个超管：关闭时原因点名「只剩 1 个、至少 2 个」，开启时不再写 at least 2；其余判定字段相同，仍等人确认")
    void p215_oneRemainingIsAShortageOnlyWithTheModeOff() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        List<EmployeeId> separating = List.of(employeeId(1));

        Verdict off = verdict(false, separating, null);
        Verdict on = verdict(true, separating, null);

        assertSameApartFromTheReason(on, off);
        assertThat(off.remainingAdministrators()).isEqualTo(1);
        assertThat(off.reason()).contains("only 1 usable super_admin holder(s) would remain, at least 2 are needed");
        assertThat(on.reason()).doesNotContain("at least 2").contains("another holder must confirm");
        assertThat(on.separatedAdministrators()).as("still held for a person").containsExactly(employeeId(1));
    }

    // RULE-436
    @Test
    @DisplayName("SINGLE-OP P2-15 剩余超管足够（3 名中 1 名离职）：两种模式的 reason 逐字相同")
    void p215_enoughRemainingGivesTheSameReason() {
        holder(1, "super_admin");
        holder(2, "super_admin");
        holder(3, "super_admin");
        List<EmployeeId> separating = List.of(employeeId(1));

        Verdict off = verdict(false, separating, null);
        Verdict on = verdict(true, separating, null);

        assertThat(on).isEqualTo(off);
        assertThat(on.reason()).contains("1 separated person(s)").contains("another holder must confirm").doesNotContain("would remain");
    }

    // RULE-435
    @Test
    @DisplayName("SINGLE-OP P2-16 对唯一超管离职：两种模式下都等人放行（separatedAdministrators 非空），放行人须留任：本人离职或无放行人时 actorRetained 为假，留任的安全审计员为真；模式不改变放行条件")
    void p216_theSoleSuperAdministratorSeparatingIsHeldInBothModes() {
        holder(1, "super_admin");
        holder(5, "security_auditor");
        List<EmployeeId> separating = List.of(employeeId(1));
        UserId soleSuperAdmin = new UserId(accountOf(1));
        UserId retainedAuditor = new UserId(accountOf(5));

        for (boolean modeOn : new boolean[] {false, true}) {
            String label = "mode " + (modeOn ? "on" : "off");

            Verdict scheduled = verdict(modeOn, separating, null);
            Verdict releasedByTheLeaver = verdict(modeOn, separating, soleSuperAdmin);
            Verdict releasedByTheRetained = verdict(modeOn, separating, retainedAuditor);

            assertThat(scheduled.separatedAdministrators()).as(label).isEqualTo(Set.of(employeeId(1)));
            assertThat(scheduled.actorRetained()).as(label + ": a scheduled run has no releaser").isFalse();
            assertThat(releasedByTheLeaver.actorRetained()).as(label + ": the person leaving cannot release").isFalse();
            assertThat(releasedByTheRetained.actorRetained()).as(label + ": a retained holder can").isTrue();
            assertThat(releasedByTheRetained.remainingAdministrators()).as(label).isZero();
        }
        assertSameApartFromTheReason(verdict(true, separating, retainedAuditor), verdict(false, separating, retainedAuditor));
    }

    // RULE-435
    @Test
    @DisplayName("SINGLE-OP P2-16 不涉及管理员的离职两种模式下都不等人：separatedAdministrators 为空、reason 为空")
    void p216_ordinarySeparationsAreNotHeldInEitherMode() {
        holder(1, "super_admin");
        holder(3, "employee");

        for (boolean modeOn : new boolean[] {false, true}) {
            Verdict verdict = verdict(modeOn, List.of(employeeId(3)), null);

            assertThat(verdict.separatedAdministrators()).isEmpty();
            assertThat(verdict.reason()).isEmpty();
        }
    }
}
