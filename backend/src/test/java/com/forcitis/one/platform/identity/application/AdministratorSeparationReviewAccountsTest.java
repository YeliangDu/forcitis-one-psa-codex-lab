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

/**
 * R-1b P1 (ADR-0033 section 3, "released by someone who is staying" gate): the people about to leave are employees, role holders and
 * the releaser are accounts. Employees 1001 and 1002 are the super_admin holders with accounts 6001 and 6002; employee 1003 (account
 * 6003) is the security auditor. The account numbered 1001 is nobody's administrator account.
 */
class AdministratorSeparationReviewAccountsTest {
    final AccountRepository accounts = mock(AccountRepository.class);
    final OrgDirectory directory = mock(OrgDirectory.class);
    final SessionContext session = mock(SessionContext.class);
    final AdministratorSeparationReview review = new AdministratorSeparationReview(accounts, directory, session, TestAccounts.apart(5000));

    AdministratorSeparationReviewAccountsTest() {
        for (long employee : new long[] {1001, 1002, 1003}) {
            when(directory.findEmployee(new EmployeeId(employee))).thenReturn(Optional.of(new EmployeeProfile(new EmployeeId(employee), "p" + employee,
                    new OrgUnitId(5), null, EmploymentStatus.EMPLOYED, false)));
        }
        when(accounts.findEnabledHolders(Role.SUPER_ADMIN)).thenReturn(Set.of(new UserId(6001), new UserId(6002)));
        when(accounts.findEnabledHolders(Role.SECURITY_AUDITOR)).thenReturn(Set.of(new UserId(6003)));
        when(session.modeIfSignedIn()).thenReturn(Optional.empty());
    }

    private SeparationReview.Verdict reviewing(List<EmployeeId> leaving, UserId releaser) {
        return review.review(leaving, releaser);
    }

    @Test
    @DisplayName("an administrator in the leaving list is found by employee, whatever the number of their account")
    void leavingAdministratorsAreFoundByEmployee() {
        SeparationReview.Verdict verdict = reviewing(List.of(new EmployeeId(1001), new EmployeeId(6002)), null);

        assertThat(verdict.separatedAdministrators()).containsExactly(new EmployeeId(1001));
        assertThat(verdict.remainingAdministrators()).isEqualTo(1);
    }

    @Test
    @DisplayName("the releaser is the account of a remaining administrator employee: not the account that has the number of a leaving employee")
    void releaserIsSetAgainstTheLeavingListAsAnEmployee() {
        List<EmployeeId> leaving = List.of(new EmployeeId(1001));

        assertThat(reviewing(leaving, new UserId(6002)).actorRetained()).as("remaining super_admin").isTrue();
        assertThat(reviewing(leaving, new UserId(6003)).actorRetained()).as("security auditor").isTrue();
        assertThat(reviewing(leaving, new UserId(6001)).actorRetained()).as("the account of the employee who is leaving").isFalse();
        assertThat(reviewing(leaving, new UserId(1001)).actorRetained()).as("an account that only has the leaving employee's number").isFalse();
        assertThat(reviewing(leaving, null).actorRetained()).as("a scheduled run has no releaser").isFalse();
    }

    @Test
    @DisplayName("holders whose employee is not employed do not count, found through the account's employee")
    void separatedHoldersDoNotCount() {
        when(directory.findEmployee(any(EmployeeId.class))).thenAnswer(call -> {
            EmployeeId employee = call.getArgument(0);
            EmploymentStatus status = employee.value() == 1002 ? EmploymentStatus.SEPARATED : EmploymentStatus.EMPLOYED;
            return Optional.of(new EmployeeProfile(employee, "p", new OrgUnitId(5), null, status, false));
        });

        assertThat(reviewing(List.of(), null).remainingAdministrators()).isEqualTo(1);
    }
}
