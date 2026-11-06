package com.forcitis.one.organization.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.Availability;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1436: the submission check applies the platform's own test of a candidate: employed and not on unpaid leave that day. */
class PayrollApproversTest {

    private final RoleDirectory roles = mock(RoleDirectory.class);
    private final AccountEmployees accountEmployees = mock(AccountEmployees.class);
    private final OrgDirectory org = mock(OrgDirectory.class);
    private final PayrollApprovers approvers = new PayrollApprovers(roles, accountEmployees, org,
            Clock.fixed(Instant.parse("2026-11-05T02:00:00Z"), ZoneOffset.UTC), new BusinessZone(ZoneId.of("Asia/Shanghai")));

    private final UserId initiator = new UserId(UUID.randomUUID());
    private final UserId other = new UserId(UUID.randomUUID());

    private void holders(UserId... holders) {
        when(roles.findRoleHolders(eq(new RoleCode("payroll_specialist")))).thenReturn(java.util.Set.of(holders));
        for (UserId holder : holders) {
            when(accountEmployees.employeeOf(holder)).thenReturn(Optional.of(new EmployeeId(UUID.randomUUID())));
        }
    }

    @Test
    @DisplayName("#1436 another holder who is working qualifies; the initiator alone, or another on unpaid leave, does not")
    void onlyAWorkingOtherHolderQualifies() {
        holders(initiator);
        assertThat(approvers.existOtherThan(initiator)).isFalse();

        holders(initiator, other);
        when(org.availabilityOn(any(), any())).thenReturn(Availability.AVAILABLE);
        assertThat(approvers.existOtherThan(initiator)).isTrue();

        when(org.availabilityOn(any(), any())).thenReturn(new Availability(true, false, false));
        assertThat(approvers.existOtherThan(initiator)).isFalse();
    }
}
