package com.forcitis.one.organization.payroll.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.payroll.api.BonusInputApprovalProcess;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Whether the payroll stage of a bonus input will have somebody to ask. The platform leaves the stage to nobody (no supervisor, no
 * administrator) when no holder but the initiator qualifies, so the request is refused up front by the same test the platform applies:
 * employed (the role directory) and not on unpaid leave that day.
 */
@Component
class PayrollApprovers {

    private final RoleDirectory roles;
    private final AccountEmployees accountEmployees;
    private final OrgDirectory org;
    private final Clock clock;
    private final BusinessZone zone;

    PayrollApprovers(RoleDirectory roles, AccountEmployees accountEmployees, OrgDirectory org, Clock clock, BusinessZone zone) {
        this.roles = roles;
        this.accountEmployees = accountEmployees;
        this.org = org;
        this.clock = clock;
        this.zone = zone;
    }

    boolean existOtherThan(UserId initiator) {
        return roles.findRoleHolders(new RoleCode(BonusInputApprovalProcess.APPROVER_ROLE)).stream()
                .filter(holder -> !holder.equals(initiator))
                .anyMatch(holder -> accountEmployees.employeeOf(holder).map(employee -> !org.availabilityOn(employee, zone.today(clock)).onUnpaidLeave()).orElse(false));
    }
}
