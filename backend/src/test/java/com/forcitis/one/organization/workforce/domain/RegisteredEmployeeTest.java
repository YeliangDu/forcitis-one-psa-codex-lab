package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.events.EmployeeRegistered;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;

class RegisteredEmployeeTest {

    private static StaffEmployee staff() {
        EmployeeId id = TestAccounts.employee(42);
        return new StaffEmployee(new Employee(id, "E42", "Synthetic", "synthetic", null, null, null, EmploymentStatus.EMPLOYED, false),
                new Assignment(id, TestOrgIds.orgUnit(3), null, null));
    }

    @Test
    @DisplayName("registering at the first login announces EmployeeRegistered as the employee's own event, numbered after the stored sequence")
    void registrationIsTheEmployeesOwnEvent() {
        RegisteredEmployee registered = RegisteredEmployee.onFirstLogin(staff(), 0, 0);

        List<DomainEvent> events = registered.drainEvents(TestEnvelopes.INSTANCE);

        assertThat(events).singleElement().isInstanceOfSatisfying(EmployeeRegistered.class, e -> {
            assertThat(e.employeeId()).isEqualTo(TestAccounts.employee(42));
            assertThat(e.envelope().aggregateType()).isEqualTo("Employee");
            assertThat(e.envelope().aggregateId()).isEqualTo(TestAccounts.employee(42).value());
            assertThat(e.envelope().aggregateVersion()).isEqualTo(1);
        });
        assertThat(registered.eventSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("a person registered again continues the stored event sequence")
    void sequenceContinues() {
        RegisteredEmployee registered = RegisteredEmployee.onFirstLogin(staff(), 0, 3);

        assertThat(registered.drainEvents(TestEnvelopes.INSTANCE)).singleElement()
                .satisfies(e -> assertThat(e.envelope().aggregateVersion()).isEqualTo(4));
    }
}
