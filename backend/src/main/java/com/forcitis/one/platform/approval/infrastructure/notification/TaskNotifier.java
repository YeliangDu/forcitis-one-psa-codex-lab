package com.forcitis.one.platform.approval.infrastructure.notification;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.events.ApprovalTaskEscalated;
import com.forcitis.one.platform.approval.events.ApprovalTaskOverdue;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import com.forcitis.one.platform.approval.events.TaskTransferred;
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

/** Recipients are exact WeCom user ids, never a broadcast (I-6); the dedup key per card makes at-least-once sending idempotent. */
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
        String initiatorName = org.findEmployee(event.initiator()).map(EmployeeProfile::name).orElse("");
        send(event.taskId(), event.candidates(), "审批待办：" + event.nodeName(),
                event.processName() + " 单据 " + event.document().documentId().value()
                        + (initiatorName.isEmpty() ? "" : "，发起人 " + initiatorName), dedupKey(event.taskId()));
    }

    @ApplicationModuleListener
    void onTaskClaimed(TaskClaimed event) {
        String name = org.findEmployee(event.claimedBy()).map(EmployeeProfile::name).orElse("同事");
        wecom.markHandled(dedupKey(event.taskId()), "已由 " + name + " 处理");
    }

    @ApplicationModuleListener
    void onTaskTransferred(TaskTransferred event) {
        String name = org.findEmployee(event.from()).map(EmployeeProfile::name).orElse("同事");
        send(event.taskId(), List.of(event.to()), "审批待办（转交）：" + event.nodeKey(),
                "单据 " + event.document().documentId().value() + "，由 " + name + " 转交给你", dedupKey(event.taskId()) + ":transfer:" + event.envelope().eventId());
    }

    @ApplicationModuleListener
    void onTaskOverdue(ApprovalTaskOverdue event) {
        send(event.taskId(), event.holders(), "审批已超时：" + event.nodeKey(),
                "单据 " + event.document().documentId().value() + " 的审批已超过时限，请尽快处理", dedupKey(event.taskId()) + ":overdue");
    }

    @ApplicationModuleListener
    void onTaskEscalated(ApprovalTaskEscalated event) {
        send(event.taskId(), event.escalatedTo(), "审批升级：" + event.nodeKey(),
                "单据 " + event.document().documentId().value() + " 的审批超时未处理，已升级给你"
                        + (event.toProcessAdministrator() ? "（流程管理员）" : ""), dedupKey(event.taskId()) + ":escalated");
    }

    private void send(ApprovalTaskId taskId, List<UserId> recipients, String title, String description, String dedupKey) {
        List<WecomUserId> wecomIds = new ArrayList<>();
        for (UserId recipient : recipients) {
            WecomUserId wecomUserId = org.findEmployee(recipient).map(EmployeeProfile::wecomUserId).orElse(null);
            if (wecomUserId == null) {
                log.warn("Approval task {}: {} has no WeCom user id, no card sent", taskId.value(), recipient.value());
                meters.counter("platform.approval.notification.skipped").increment();
            } else {
                wecomIds.add(wecomUserId);
            }
        }
        if (wecomIds.isEmpty()) {
            return;
        }
        wecom.send(new WecomCard(wecomIds, title, description, properties.taskUrlTemplate().replace("{taskId}", taskId.value()), dedupKey));
    }

    private static String dedupKey(ApprovalTaskId taskId) {
        return "approval-task:" + taskId.value();
    }
}
