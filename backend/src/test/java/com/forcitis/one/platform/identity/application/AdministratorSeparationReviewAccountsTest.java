package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forcitis.one.fixture.doubles.TestOrgIds;

/**
 * ADR-0033 section 3, the "released by someone who is staying" gate: the people about to leave are employees, role holders and
 * the releaser are accounts. Employees 1001 and 1002 are the super_admin holders with accounts 6001 and 6002; employee 1003 (account
 * 6003) is the security auditor. The account numbered 1001 is nobody's administrator account.
 */
class AdministratorSeparationReviewAccountsTest {
    final AccountRepository accounts = mock(AccountRepository.class);
    final OrgDirectory directory = mock(OrgDirectory.class);
    final SessionContext session = mock(SessionContext.class);
    final AdministratorSeparationReview review = new AdministratorSeparationReview(accounts, directory, session, TestAccounts.everyoneHasOne(), () -> false,
            java.time.Clock.fixed(java.time.Instant.parse("2026-11-02T02:00:00Z"), java.time.ZoneOffset.UTC),
            new com.forcitis.one.kernel.BusinessZone(java.time.ZoneId.of("Asia/Shanghai")));

    AdministratorSeparationReviewAccountsTest() {
        for (long employee : new long[] {1001, 1002, 1003}) {
            when(directory.findEmployee(TestAccounts.employee(employee))).thenReturn(Optional.of(new EmployeeProfile(TestAccounts.employee(employee), "p" + employee,
                    TestOrgIds.orgUnit(5), null, EmploymentStatus.EMPLOYED, false)));
        }
        when(accounts.findEnabledHolders(Role.SUPER_ADMIN)).thenReturn(Set.of(TestAccounts.account(1001), TestAccounts.account(1002)));
        when(accounts.findEnabledHolders(Role.SECURITY_AUDITOR)).thenReturn(Set.of(TestAccounts.account(1003)));
        when(session.modeIfSignedIn()).thenReturn(Optional.empty());
    }

    private SeparationReview.Verdict reviewing(List<EmployeeId> leaving, UserId releaser) {
        return review.review(leaving, releaser);
    }

    @Test
    @DisplayName("an administrator in the leaving list is found by employee, whatever the number of their account")
    void leavingAdministratorsAreFoundByEmployee() {
        SeparationReview.Verdict verdict = reviewing(List.of(TestAccounts.employee(1001), TestAccounts.employee(6002)), null);

        assertThat(verdict.separatedAdministrators()).containsExactly(TestAccounts.employee(1001));
        assertThat(verdict.remainingAdministrators()).isEqualTo(1);
    }

    @Test
    @DisplayName("the releaser is the account of a remaining administrator employee: not the account that has the number of a leaving employee")
    void releaserIsSetAgainstTheLeavingListAsAnEmployee() {
        List<EmployeeId> leaving = List.of(TestAccounts.employee(1001));

        assertThat(reviewing(leaving, TestAccounts.account(1002)).actorRetained()).as("remaining super_admin").isTrue();
        assertThat(reviewing(leaving, TestAccounts.account(1003)).actorRetained()).as("security auditor").isTrue();
        assertThat(reviewing(leaving, TestAccounts.account(1001)).actorRetained()).as("the account of the employee who is leaving").isFalse();
        assertThat(reviewing(leaving, TestAccounts.unknownAccount(1001)).actorRetained()).as("an account that only has the leaving employee's number").isFalse();
        assertThat(reviewing(leaving, null).actorRetained()).as("a scheduled run has no releaser").isFalse();
    }

    @Test
    @DisplayName("holders whose employee is not employed do not count, found through the account's employee")
    void separatedHoldersDoNotCount() {
        when(directory.findEmployee(any(EmployeeId.class))).thenAnswer(call -> {
            EmployeeId employee = call.getArgument(0);
            EmploymentStatus status = employee.equals(TestAccounts.employee(1002)) ? EmploymentStatus.SEPARATED : EmploymentStatus.EMPLOYED;
            return Optional.of(new EmployeeProfile(employee, "p", TestOrgIds.orgUnit(5), null, status, false));
        });

        assertThat(reviewing(List.of(), null).remainingAdministrators()).isEqualTo(1);
    }

    private static final java.time.LocalDate TODAY = java.time.LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("IMP-1030-PAR-B5 (ADR-0068 section 3, the fifth in-service check): an administrator on unpaid leave is no available super_admin and no retained releaser, but is still employed")
    void aPersonOnUnpaidLeaveIsNeitherAvailableNorRetained() {
        when(directory.isOnUnpaidLeave(TestAccounts.employee(1002), TODAY)).thenReturn(true);
        when(directory.isOnUnpaidLeave(TestAccounts.employee(1003), TODAY)).thenReturn(true);

        SeparationReview.Verdict verdict = reviewing(List.of(TestAccounts.employee(1001), TestAccounts.employee(1002)), TestAccounts.account(1003));

        assertThat(verdict.separatedAdministrators()).as("1002 on leave is not available: only 1001 is held back").containsExactly(TestAccounts.employee(1001));
        assertThat(verdict.remainingAdministrators()).as("nobody available remains").isZero();
        assertThat(verdict.actorRetained()).as("the releaser 1003 is on leave: not retained").isFalse();
        assertThat(reviewing(List.of(), null).remainingAdministrators()).as("only 1001 counts while 1002 is on leave").isEqualTo(1);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B5 (ADR-0068 item 2.2): people whose separation is held do not count as the remainder, and a releaser who is held is not retained")
    void theHeldSetIsLeftOutOfTheRemainderAndTheReleaser() {
        Set<EmployeeId> held = Set.of(TestAccounts.employee(1002));

        SeparationReview.Verdict verdict = review.review(List.of(TestAccounts.employee(1001)), held, TestAccounts.account(1002));

        assertThat(verdict.remainingAdministrators()).as("1001 leaves, 1002 is held: nobody remains").isZero();
        assertThat(verdict.actorRetained()).as("the releaser 1002 is in the held set").isFalse();
        assertThat(review.review(List.of(TestAccounts.employee(1001)), held, TestAccounts.account(1003)).actorRetained()).as("the auditor is not held").isTrue();
        assertThat(review.review(List.of(TestAccounts.employee(1001)), TestAccounts.account(1003)).remainingAdministrators())
                .as("the two-argument form is the sync path: no held set").isEqualTo(1);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B5 (ADR-0068 item 2.2): the lock key is one non-blank kernel constant")
    void theLockKeyIsAKernelConstant() {
        assertThat(SeparationReview.SEPARATION_LOCK_KEY).isNotBlank().startsWith("com.forcitis.one.kernel.SeparationReview");
    }
}
