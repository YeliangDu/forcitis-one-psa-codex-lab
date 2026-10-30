package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
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
 * The notice when a release leaves exactly one available super administrator (ADR-0068 item 2.3, ADR-0028 decision 4; user decision 2026-10-30):
 * one WeCom card per release to the usable super_admin and security_auditor holders, each once, without the separating person and without the
 * held; one released and waiting for the date is still in service and is told; the releaser is among them when they hold either role. Operations is reached through the
 * ALERT log line. The card is queued in the release transaction and sent after commit.
 */
@Component
class SeparationHoldNotice {
    private static final Logger LOG = LoggerFactory.getLogger(SeparationHoldNotice.class);
    private static final String TARGET_URL = "https://one.forcitis.com/";

    private final OrgDirectory directory;
    private final WecomGateway wecom;

    SeparationHoldNotice(OrgDirectory directory, WecomGateway wecom) {
        this.directory = directory;
        this.wecom = wecom;
    }

    void remainingOne(SeparationHold hold, Set<EmployeeId> usable, Set<EmployeeId> held, UserId releasedBy) {
        LOG.error("ALERT organization.workforce separation hold {} released by user {}: only 1 available super_admin remains", hold.id(), releasedBy.value());
        List<WecomUserId> recipients = new ArrayList<>();
        for (EmployeeId person : usable) {
            if (held.contains(person)) {
                continue;
            }
            directory.findEmployee(person).map(profile -> profile.wecomUserId()).ifPresent(recipients::add);
        }
        if (recipients.isEmpty()) {
            LOG.error("ALERT organization.workforce separation hold {} card reaches nobody: no usable administrator has a WeCom user id", hold.id());
            return;
        }
        wecom.send(new WecomCard(recipients, "管理员离职放行：剩余 1 名可用超级管理员",
                "一名管理员的离职已放行，放行后仅剩 1 名可用超级管理员。请尽快补足超级管理员。", TARGET_URL,
                DeliverySubject.of(DeliveryPurpose.SEPARATION_HOLD_NOTICE, hold.id().value())));
    }
}
