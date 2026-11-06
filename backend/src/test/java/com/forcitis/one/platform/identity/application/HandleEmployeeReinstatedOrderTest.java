package com.forcitis.one.platform.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

@DisplayName("#939 the account row is locked before the directory is read (a separation that takes effect meanwhile must not be missed)")
class HandleEmployeeReinstatedOrderTest {

    final AccountRepository accounts = mock(AccountRepository.class);
    final OrgDirectory directory = mock(OrgDirectory.class);
    final HandleEmployeeReinstated handler = new HandleEmployeeReinstated(TestAccounts.everyoneHasOne(), directory, accounts, mock(RoleRepository.class),
            mock(AccountGrants.class), mock(DomainEvents.class), mock(AccessCache.class), "employee");

    @Test
    void theLockComesBeforeTheDirectoryRead() {
        UserId user = TestAccounts.account(1001);
        var employee = TestAccounts.employee(1001);
        when(accounts.findForUpdate(any())).thenReturn(Optional.of(UserAccount.restore(user, employee, "ENABLED", Set.of(), null, 1, 1)));
        when(directory.findEmployee(employee)).thenReturn(Optional.empty());
        var event = new EmployeeReinstated(TestEnvelopes.of("organization.workforce.EmployeeReinstated", "Employee", employee.value()),
                new PersonnelCommandId(UUID.randomUUID()), employee, LocalDate.of(2026, 11, 2), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "platform.identity.account-provisioning")) {
            handler.on(event);
        }

        InOrder order = inOrder(accounts, directory);
        order.verify(accounts).findForUpdate(user);
        order.verify(accounts).isHandled(event.personnelCommandId());
        order.verify(directory).findEmployee(employee);
    }
}
