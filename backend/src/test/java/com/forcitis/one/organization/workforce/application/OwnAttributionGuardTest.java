package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.Employee;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0033 section 3, the gate for the legal entity of an employee or a department: the actor is an account, the
 * subject an employee or a department, so the account is turned into its employee first. The actor is employee 1001 with account 6001;
 * the employee numbered like the account (6001) is somebody else and may be changed.
 */
class OwnAttributionGuardTest {
    static final UserId ACTOR = TestAccounts.account(1001);
    static final EmployeeId ACTING_EMPLOYEE = new EmployeeId(1001);
    static final OrgUnitId OWN_UNIT = new OrgUnitId(5);
    static final OrgUnitId UNIT_ABOVE = new OrgUnitId(2);

    final OrgSnapshotQueries snapshot = mock(OrgSnapshotQueries.class);
    final AuditLog audit = mock(AuditLog.class);
    final OwnAttributionGuard guard = new OwnAttributionGuard(snapshot, audit, TestAccounts.everyoneHasOne());

    OwnAttributionGuardTest() {
        Employee employee = new Employee(ACTING_EMPLOYEE, "E1001", "Actor", "actor", null, null, null, EmploymentStatus.EMPLOYED, false);
        when(snapshot.findStaff(ACTING_EMPLOYEE)).thenReturn(Optional.of(new StaffEmployee(employee, new Assignment(ACTING_EMPLOYEE, OWN_UNIT, null, null))));
        when(snapshot.findAncestors(OWN_UNIT)).thenReturn(List.of(UNIT_ABOVE, new OrgUnitId(1)));
    }

    private void assertRefused(AttributionSubject subject) {
        assertThatThrownBy(() -> guard.requireNotOwn(subject, ACTOR)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN));
    }

    @Test
    @DisplayName("the actor's own employee entry is refused and audited, the entry of the employee numbered like the actor's account is not")
    void ownEmployeeEntry() {
        assertRefused(AttributionSubject.of(ACTING_EMPLOYEE));
        verify(audit).recordIndependently(any());

        assertThatCode(() -> guard.requireNotOwn(AttributionSubject.of(new EmployeeId(6001)), ACTOR)).doesNotThrowAnyException();
        verify(audit, org.mockito.Mockito.times(1)).recordIndependently(any());
    }

    @Test
    @DisplayName("the actor's own department and every department above it are refused, a department beside it is not")
    void ownDepartmentChain() {
        assertRefused(AttributionSubject.of(OWN_UNIT));
        assertRefused(AttributionSubject.of(UNIT_ABOVE));
        assertRefused(AttributionSubject.of(new OrgUnitId(1)));

        assertThatCode(() -> guard.requireNotOwn(AttributionSubject.of(new OrgUnitId(9)), ACTOR)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an actor the organisation does not know has no department to protect, and the employee entry check still holds")
    void unknownActor() {
        when(snapshot.findStaff(ACTING_EMPLOYEE)).thenReturn(Optional.empty());

        assertThatCode(() -> guard.requireNotOwn(AttributionSubject.of(OWN_UNIT), ACTOR)).doesNotThrowAnyException();
        verify(audit, never()).recordIndependently(any());
        assertRefused(AttributionSubject.of(ACTING_EMPLOYEE));
    }
}
