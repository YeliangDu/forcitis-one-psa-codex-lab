package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The notice when a revoke or a disable by hand leaves exactly one available super administrator (ADR-0068 item 2.3; user decision 2026-10-30
 * 「加，同离职一样」): one WeCom card per act to the available holders left, without personal data, and an ALERT log line. The card is queued in the
 * transaction of the act and sent after commit.
 */
@Component
class AdministratorFloorNotice {
    private static final Logger LOG = LoggerFactory.getLogger(AdministratorFloorNotice.class);
    private static final String TARGET_URL = "https://one.forcitis.com/";
    private static final String DESCRIPTION = "超级管理员的角色被撤销或账号被停用，之后仅剩 1 名可用超级管理员。请尽快补足超级管理员。";

    private final OrgDirectory directory;
    private final WecomGateway wecom;

    AdministratorFloorNotice(OrgDirectory directory, WecomGateway wecom) {
        this.directory = directory;
        this.wecom = wecom;
    }

    /** grant: the closed super_admin grant of the act, else its security_auditor grant. */
    void roleRevoked(AccountRoleGrantId grant, Set<EmployeeId> recipients, UserId by) {
        send(DeliverySubject.of(DeliveryPurpose.ROLE_REVOCATION_NOTICE, grant.value()), "管理员角色撤销：剩余 1 名可用超级管理员", recipients,
                "grant " + grant + " revoked by user " + by.value());
    }

    void accountDisabled(UserId account, AuditEntryId disableEntry, Set<EmployeeId> recipients, UserId by) {
        send(DeliverySubject.of(DeliveryPurpose.ACCOUNT_DISABLE_NOTICE, account.value(), disableEntry.value()), "管理员账号停用：剩余 1 名可用超级管理员", recipients,
                "account " + account.value() + " disabled by user " + by.value());
    }

    private void send(DeliverySubject subject, String title, Set<EmployeeId> usable, String what) {
        LOG.error("ALERT platform.identity {}: only 1 available super_admin remains", what);
        List<WecomUserId> recipients = new ArrayList<>();
        for (EmployeeId person : usable) {
            directory.findEmployee(person).map(profile -> profile.wecomUserId()).ifPresent(recipients::add);
        }
        if (recipients.isEmpty()) {
            LOG.error("ALERT platform.identity the notice of {} reaches nobody: no usable administrator has a WeCom user id", what);
            return;
        }
        wecom.send(new WecomCard(recipients, title, DESCRIPTION, TARGET_URL, subject));
    }
}
