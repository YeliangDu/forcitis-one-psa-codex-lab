package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The notice when an unpaid-leave start leaves exactly one available super administrator (ADR-0068 item 2.3; user decision 2026-10-30 「加，同离职一样」):
 * one WeCom card per start to the same people as the notice of a released separation, without personal data in the card. Operations is reached through
 * the ALERT log line. The card is queued in the entering transaction and sent after commit.
 */
@Component
class UnpaidLeaveStartNotice {
    private static final Logger LOG = LoggerFactory.getLogger(UnpaidLeaveStartNotice.class);
    private static final String TARGET_URL = "https://one.forcitis.com/";

    private final OrgDirectory directory;
    private final WecomGateway wecom;

    UnpaidLeaveStartNotice(OrgDirectory directory, WecomGateway wecom) {
        this.directory = directory;
        this.wecom = wecom;
    }

    void remainingOne(PersonnelCommand start, Set<EmployeeId> usable, Set<EmployeeId> held, UserId enteredBy) {
        LOG.error("ALERT organization.workforce unpaid leave start {} entered by user {}: only 1 available super_admin remains", start.id(), enteredBy.value());
        List<WecomUserId> recipients = RemainingOneCard.recipients(directory, usable, held);
        if (recipients.isEmpty()) {
            LOG.error("ALERT organization.workforce unpaid leave start {} card reaches nobody: no usable administrator has a WeCom user id", start.id());
            return;
        }
        wecom.send(new WecomCard(recipients, "管理员停薪留职：剩余 1 名可用超级管理员",
                "一名管理员的停薪留职已开始或已录入，之后仅剩 1 名可用超级管理员。请尽快补足超级管理员。", TARGET_URL,
                DeliverySubject.of(DeliveryPurpose.UNPAID_LEAVE_START_NOTICE, start.id().value())));
    }
}
