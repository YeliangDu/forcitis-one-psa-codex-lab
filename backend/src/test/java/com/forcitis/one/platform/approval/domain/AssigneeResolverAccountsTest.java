package com.forcitis.one.platform.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forcitis.one.fixture.doubles.TestOrgIds;

/**
 * ADR-0033 section 3, the approval rule "initiator versus candidate": the initiator is an employee, role holders are accounts,
 * and the candidates of a task are accounts. The employees are 1001 (initiator), 1002 and 1003 (the initiator's supervisor); their
 * accounts are UUIDs (TestAccounts). An initiator that is left out by comparing the account with the employee would stay a candidate of
 * their own request.
 */
class AssigneeResolverAccountsTest {
    static final EmployeeId INITIATOR = TestAccounts.employee(1001);
    static final EmployeeId SUPERVISOR = TestAccounts.employee(1003);
    static final RoleCode REVIEWER = new RoleCode("reviewer");

    final OrgDirectory org = mock(OrgDirectory.class);
    final RoleDirectory roles = mock(RoleDirectory.class);
    final AssigneeResolver resolver = new AssigneeResolver(org, roles, Set.of(), TestAccounts.everyoneHasOne(), java.time.LocalDate.of(2026, 11, 2));

    AssigneeResolverAccountsTest() {
        for (long employee : new long[] {1001, 1002, 1003}) {
            when(org.findEmployee(TestAccounts.employee(employee))).thenReturn(Optional.of(new EmployeeProfile(TestAccounts.employee(employee), "person-" + employee,
                    TestOrgIds.orgUnit(5), null, EmploymentStatus.EMPLOYED, false)));
        }
        when(org.findSupervisor(any(EmployeeId.class))).thenAnswer(call -> call.getArgument(0).equals(INITIATOR) ? Optional.of(SUPERVISOR) : Optional.empty());
    }

    private Resolution reviewers(long... accounts) {
        when(roles.findRoleHolders(REVIEWER)).thenReturn(java.util.Arrays.stream(accounts).mapToObj(TestAccounts::account)
                .collect(java.util.stream.Collectors.toSet()));
        return resolver.resolve(new AssignmentRule.Role(REVIEWER), false, INITIATOR, ProcessVariables.empty());
    }

    @Test
    @DisplayName("the initiator's account is left out of the role holders, so the others are the candidates, and they are accounts")
    void initiatorLeavesTheCandidates() {
        Resolution resolution = reviewers(1001, 1002);

        assertThat(resolution.candidates()).containsExactly(TestAccounts.account(1002));
        assertThat(resolution.kind()).isEqualTo(ResolutionKind.RESOLVED);
    }

    @Test
    @DisplayName("another person's account is not the initiator's: it stays a candidate")
    void anotherAccountWithTheInitiatorsNumberStays() {
        when(org.findEmployee(TestAccounts.employee(3999))).thenReturn(Optional.of(new EmployeeProfile(TestAccounts.employee(3999), "other", TestOrgIds.orgUnit(5), null,
                EmploymentStatus.EMPLOYED, false)));

        Resolution resolution = reviewers(3999);

        assertThat(resolution.candidates()).containsExactly(TestAccounts.account(3999));
    }

    @Test
    @DisplayName("when the initiator alone holds the role the request goes to the initiator's supervisor, as the supervisor's account")
    void soleHolderIsRoutedToTheSupervisor() {
        Resolution resolution = reviewers(1001);

        assertThat(resolution.candidates()).containsExactly(TestAccounts.account(1003));
        assertThat(resolution.kind()).isEqualTo(ResolutionKind.ESCALATED_SELF_APPROVAL);
    }

    @Test
    @DisplayName("people named in a form field are employees, and the candidates are their accounts")
    void formFieldNamesEmployees() {
        ProcessVariables variables = new ProcessVariables(Map.of("projectManagerId", List.of(TestAccounts.employee(1002).toString())), Map.of());

        Resolution resolution = resolver.resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), false, INITIATOR, variables);

        assertThat(resolution.candidates()).containsExactly(TestAccounts.account(1002));
    }
}
