package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The WeCom notice of an open item that went overdue and was escalated (ADR-0032 item 3 clause 3; user decision 2026-11-03 on #1151): one card per item to the enabled
 * super_admin holders, by role as {@link AdministratorFloorNotice} does, who are employed and not on unpaid leave that day; the person the item is about is left out.
 * The card is queued in the transaction that marks the item escalated and sent after commit. Nobody to tell, or a recipient without a WeCom user id, is an ALERT log
 * line only: the item stays escalated and is not sent again. The card carries no permission code and no scope (ADR-0032 line 94).
 */
@Component
class PermissionAdjustmentEscalationNotice {
    private static final Logger LOG = LoggerFactory.getLogger(PermissionAdjustmentEscalationNotice.class);
    private static final String TARGET_URL = "https://one.forcitis.com/";

    private final AccountRepository accounts;
    private final AccountEmployees accountEmployees;
    private final OrgDirectory directory;
    private final WecomGateway wecom;
    private final Clock clock;
    private final BusinessZone zone;

    PermissionAdjustmentEscalationNotice(AccountRepository accounts, AccountEmployees accountEmployees, OrgDirectory directory, WecomGateway wecom, Clock clock,
            BusinessZone zone) {
        this.accounts = accounts;
        this.accountEmployees = accountEmployees;
        this.directory = directory;
        this.wecom = wecom;
        this.clock = clock;
        this.zone = zone;
    }

    void send(PermissionAdjustment item) {
        LocalDate today = zone.dateOf(clock.instant());
        Set<EmployeeId> eligible = new LinkedHashSet<>();
        for (UserId holder : accounts.findEnabledHolders(Role.SUPER_ADMIN)) {
            if (!holder.equals(item.user())) {
                accountEmployees.employeeOf(holder).filter(employee -> usable(employee, today)).ifPresent(eligible::add);
            }
        }
        List<WecomUserId> recipients = new ArrayList<>();
        for (EmployeeId employee : eligible) {
            directory.findEmployee(employee).map(EmployeeProfile::wecomUserId).ifPresent(recipients::add);
        }
        if (recipients.isEmpty()) {
            LOG.error("ALERT P3 platform.identity the escalation notice of permission adjustment item {} reaches nobody: {} eligible super_admin holder(s), none with a WeCom user id",
                    item.id().value(), eligible.size());
            return;
        }
        if (recipients.size() < eligible.size()) {
            LOG.error("ALERT P3 platform.identity the escalation notice of permission adjustment item {} does not reach {} of {} eligible super_admin holder(s): no WeCom user id",
                    item.id().value(), eligible.size() - recipients.size(), eligible.size());
        }
        wecom.send(new WecomCard(recipients, "待调整权限：逾期升级", "一项待调整权限已超过期限，仍待办理。请尽快办理。", TARGET_URL,
                DeliverySubject.of(DeliveryPurpose.PERMISSION_ADJUSTMENT_OVERDUE_NOTICE, item.id().value())));
    }

    private boolean usable(EmployeeId employee, LocalDate today) {
        Optional<EmployeeProfile> profile = directory.findEmployee(employee);
        return profile.filter(found -> found.employmentStatus() == EmploymentStatus.EMPLOYED).isPresent() && !directory.availabilityOn(employee, today).onUnpaidLeave();
    }
}
