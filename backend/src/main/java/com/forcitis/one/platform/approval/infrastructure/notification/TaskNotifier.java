package com.forcitis.one.platform.approval.infrastructure.notification;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Recipients are exact WeCom user ids, never a broadcast (I-6); the dedup key approval-task:{taskId} makes at-least-once sending idempotent. */
@Component
@EnableConfigurationProperties(NotificationProperties.class)
class TaskNotifier {

    private static final Logger log = LoggerFactory.getLogger(TaskNotifier.class);

    private final OrgDirectory org;
    private final WecomGateway wecom;
    private final NotificationProperties properties;
    private final MeterRegistry meters;

    TaskNotifier(OrgDirectory org, WecomGateway wecom, NotificationProperties properties, MeterRegistry meters) {
        this.org = org;
        this.wecom = wecom;
        this.properties = properties;
        this.meters = meters;
    }

    @ApplicationModuleListener
    void onTaskAssigned(TaskAssigned event) {
        List<WecomUserId> recipients = new ArrayList<>();
        for (UserId candidate : event.candidates()) {
            WecomUserId wecomUserId = org.findEmployee(candidate).map(EmployeeProfile::wecomUserId).orElse(null);
            if (wecomUserId == null) {
                log.warn("Approval task {}: candidate {} has no WeCom user id, no card sent", event.taskId().value(), candidate.value());
                meters.counter("platform.approval.notification.skipped").increment();
            } else {
                recipients.add(wecomUserId);
            }
        }
        if (recipients.isEmpty()) {
            return;
        }
        String initiatorName = org.findEmployee(event.initiator()).map(EmployeeProfile::name).orElse("");
        wecom.send(new WecomCard(recipients, "审批待办：" + event.nodeName(),
                event.processName() + " 单据 " + documentIdOf(event.businessKey()) + (initiatorName.isEmpty() ? "" : "，发起人 " + initiatorName),
                properties.taskUrlTemplate().replace("{taskId}", event.taskId().value()), dedupKey(event.taskId().value())));
    }

    @ApplicationModuleListener
    void onTaskClaimed(TaskClaimed event) {
        String name = org.findEmployee(event.claimedBy()).map(EmployeeProfile::name).orElse("同事");
        wecom.markHandled(dedupKey(event.taskId().value()), "已由 " + name + " 处理");
    }

    /** Phase 1a shows the document id carried by the business key ("timesheet:123" gives 123) until documents have numbers of their own. */
    private static String documentIdOf(String businessKey) {
        return businessKey.substring(businessKey.lastIndexOf(':') + 1);
    }

    private static String dedupKey(String taskId) {
        return "approval-task:" + taskId;
    }
}
