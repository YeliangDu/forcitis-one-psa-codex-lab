package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.RoleHolders;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The WeCom notice of one escalation level of an open countersign request (one-design rulings on #896 item 3; user answers A and B of 2026-11-02): level 1 reaches the
 * finance head of the row's legal entity in today's version, level 2 every holder of the business administrator role, level 3 the finance head again. The drafter is not
 * told. The card is queued in the transaction that raised the level and sent after commit; the subject is the request and each level has its own purpose, so one level is
 * delivered once.
 */
@Component
class CountersignEscalationNotice {
    private static final Logger LOG = LoggerFactory.getLogger(CountersignEscalationNotice.class);
    private static final String TARGET_URL = "https://one.forcitis.com/";

    private final AttributionCountersignRepository countersigns;
    private final RoleHolders roles;
    private final AccountEmployees accountEmployees;
    private final OrgDirectory directory;
    private final WecomGateway wecom;

    CountersignEscalationNotice(AttributionCountersignRepository countersigns, RoleHolders roles, AccountEmployees accountEmployees, OrgDirectory directory, WecomGateway wecom) {
        this.countersigns = countersigns;
        this.roles = roles;
        this.accountEmployees = accountEmployees;
        this.directory = directory;
        this.wecom = wecom;
    }

    void send(AttributionCountersign row, int level, LocalDate today) {
        List<EmployeeId> people = level == 2 ? administrators() : countersigns.financeHeadOn(row.legalEntityId(), today).stream().toList();
        List<WecomUserId> recipients = new ArrayList<>();
        for (EmployeeId person : people) {
            directory.findEmployee(person).map(profile -> profile.wecomUserId()).ifPresent(recipients::add);
        }
        if (recipients.isEmpty()) {
            LOG.error("ALERT organization.workforce the level {} notice of countersign request {} reaches nobody: no recipient has a WeCom user id", level, row.id().value());
            return;
        }
        wecom.send(new WecomCard(recipients, title(level), "一项法人归属会签已超过期限，仍待决定。请尽快处理。", TARGET_URL, DeliverySubject.of(purpose(level), row.id().value())));
    }

    private List<EmployeeId> administrators() {
        return roles.enabledHolders(CountersignDeciders.ESCALATED_ROLE).stream().flatMap(account -> accountEmployees.employeeOf(account).stream()).distinct().toList();
    }

    private static DeliveryPurpose purpose(int level) {
        return switch (level) {
            case 1 -> DeliveryPurpose.COUNTERSIGN_OVERDUE_REMINDER;
            case 2 -> DeliveryPurpose.COUNTERSIGN_ESCALATED_TO_ADMINS;
            case AttributionCountersign.LAST_ESCALATION_LEVEL -> DeliveryPurpose.COUNTERSIGN_ESCALATED_FINAL;
            default -> throw new IllegalArgumentException("no notice for escalation level " + level);
        };
    }

    private static String title(int level) {
        return switch (level) {
            case 1 -> "法人归属会签逾期提醒";
            case 2 -> "法人归属会签逾期升级：业务管理员";
            default -> "法人归属会签逾期升级：最后一级";
        };
    }
}
