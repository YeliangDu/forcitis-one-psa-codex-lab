package com.forcitis.one.platform.approval.infrastructure.notification;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ApprovalTexts;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.ApprovalTaskEscalated;
import com.forcitis.one.platform.approval.events.ApprovalTaskBecameOverdue;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import com.forcitis.one.platform.approval.events.TaskClosed;
import com.forcitis.one.platform.approval.events.TaskReleased;
import com.forcitis.one.platform.approval.events.TaskReassigned;
import com.forcitis.one.platform.approval.events.TaskTransferred;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Recipients are exact WeCom user ids, never a broadcast (I-6); the delivery subject per card makes at-least-once sending idempotent. A
 * separated person gets no card (D-1a-141); a card nobody can receive is logged as an error and counted, never dropped quietly.
 */
@SystemActorName("platform.approval.task-notification")
@Component
@EnableConfigurationProperties(NotificationProperties.class)
class TaskNotifier {

    private static final Logger log = LoggerFactory.getLogger(TaskNotifier.class);
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrgDirectory org;
    private final WecomGateway wecom;
    private final NotificationProperties properties;
    private final BusinessZone zone;
    private final MeterRegistry meters;
    private final AccountEmployees accountEmployees;
    private final ApprovalEngine engine;

    TaskNotifier(OrgDirectory org, WecomGateway wecom, NotificationProperties properties, BusinessZone zone, MeterRegistry meters,
            AccountEmployees accountEmployees, ApprovalEngine engine) {
        this.engine = engine;
        this.accountEmployees = accountEmployees;
        this.org = org;
        this.wecom = wecom;
        this.properties = properties;
        this.zone = zone;
        this.meters = meters;
    }

    @ApplicationModuleListener
    void onTaskAssigned(TaskAssigned event) {
        Optional<EmployeeProfile> initiator = org.findEmployee(event.initiator());
        String department = initiator.flatMap(profile -> org.findOrgUnit(profile.orgUnitId())).map(OrgUnitProfile::name).orElse(null);
        send(event.taskId(), event.candidates(), ApprovalTexts.assignedTitle(event.nodeName().value()),
                ApprovalTexts.assignedBody(event.processName().value(), event.summary().value(), initiator.map(EmployeeProfile::name).orElse(null),
                        department, moment(event.envelope().occurredAt()), event.dueAt() == null ? null : moment(event.dueAt())),
                subject(DeliveryPurpose.TASK_ASSIGNED, event.taskId()));
    }

    @ApplicationModuleListener
    void onTaskClaimed(TaskClaimed event) {
        wecom.markHandled(assigned(event.taskId()), ApprovalTexts.handledNote(nameOf(event.claimedBy())));
    }

    @ApplicationModuleListener
    void onTaskTransferred(TaskTransferred event) {
        String name = accountEmployees.employeeOf(event.from()).flatMap(org::findEmployee).map(EmployeeProfile::name).orElse(ApprovalTexts.aColleague());
        send(event.taskId(), List.of(event.to()), ApprovalTexts.transferredTitle(event.document()),
                ApprovalTexts.transferredBody(name, event.reason().value()),
                DeliverySubject.of(DeliveryPurpose.TASK_TRANSFERRED, event.taskId().value(), event.envelope().eventId()));
    }

    @ApplicationModuleListener
    void onTaskReassigned(TaskReassigned event) {
        send(event.taskId(), List.of(event.to()), ApprovalTexts.reassignedTitle(event.document()), ApprovalTexts.reassignedBody(event.reason().value()),
                DeliverySubject.of(DeliveryPurpose.TASK_REASSIGNED, event.taskId().value(), event.envelope().eventId()));
    }

    @ApplicationModuleListener
    void onTaskOverdue(ApprovalTaskBecameOverdue event) {
        send(event.taskId(), event.holders(), ApprovalTexts.overdueTitle(event.document()), ApprovalTexts.overdueBody(moment(event.dueAt())),
                subject(DeliveryPurpose.TASK_OVERDUE, event.taskId()));
    }

    @ApplicationModuleListener
    void onTaskEscalated(ApprovalTaskEscalated event) {
        send(event.taskId(), event.escalatedTo(), ApprovalTexts.escalatedTitle(event.document()), ApprovalTexts.escalatedBody(event.target()),
                subject(DeliveryPurpose.TASK_ESCALATED, event.taskId()));
        List<String> names = event.escalatedTo().stream().map(this::nameOf).toList();
        wecom.markHandled(assigned(event.taskId()), ApprovalTexts.escalatedNote(names));
    }

    @ApplicationModuleListener
    void onTaskReleased(TaskReleased event) {
        wecom.markHandled(assigned(event.taskId()), ApprovalTexts.releasedNote(nameOf(event.releasedBy())));
    }

    @ApplicationModuleListener
    void onTaskClosed(TaskClosed event) {
        wecom.markHandled(assigned(event.taskId()), ApprovalTexts.closedNote());
    }

    @ApplicationModuleListener
    void onApprovalCompleted(ApprovalCompleted event) {
        Optional<UserId> initiator = accountEmployees.accountOf(event.initiator());
        if (initiator.isEmpty()) {
            log.warn("Card {}: initiator {} has no account, no card sent", completed(event), event.initiator().value());
            meters.counter("forcitis.platform.approval.notification.skipped").increment();
            return;
        }
        send(null, List.of(initiator.get()), ApprovalTexts.completedTitle(event.outcome()),
                ApprovalTexts.completedBody(engine.processNameOf(event.processKey()), event.document(), event.round().value(),
                        event.reason() == null ? null : event.reason().value()),
                completed(event));
    }

    private void send(ApprovalTaskId taskId, List<UserId> recipients, String title, String description, DeliverySubject subject) {
        List<WecomUserId> wecomIds = new ArrayList<>();
        for (UserId recipient : recipients) {
            Optional<EmployeeProfile> profile = accountEmployees.employeeOf(recipient).flatMap(org::findEmployee);
            if (profile.isPresent() && profile.get().employmentStatus() != EmploymentStatus.EMPLOYED) {
                log.info("Card {}: {} has separated, no card sent", subject, recipient.value());
                meters.counter("forcitis.platform.approval.notification.skipped").increment();
            } else if (profile.isEmpty() || profile.get().wecomUserId() == null) {
                log.warn("Card {}: {} has no WeCom user id, no card sent", subject, recipient.value());
                meters.counter("forcitis.platform.approval.notification.skipped").increment();
            } else {
                wecomIds.add(profile.get().wecomUserId());
            }
        }
        if (wecomIds.isEmpty()) {
            log.error("ALERT card {} reaches nobody: none of {} can receive it", subject, recipients);
            meters.counter("forcitis.platform.approval.notification.failed").increment();
            return;
        }
        String target = taskId == null ? properties.taskUrlTemplate().replace("/{taskId}", "") : properties.taskUrlTemplate().replace("{taskId}", taskId.toString());
        wecom.send(new WecomCard(wecomIds, title, description, target, subject));
    }

    private String nameOf(UserId user) {
        return accountEmployees.employeeOf(user).flatMap(org::findEmployee).map(EmployeeProfile::name).orElse(ApprovalTexts.aColleague());
    }

    private String moment(Instant instant) {
        return MOMENT.format(instant.atZone(zone.zone()));
    }

    private static DeliverySubject subject(DeliveryPurpose purpose, ApprovalTaskId taskId) {
        return DeliverySubject.of(purpose, taskId.value());
    }

    private static DeliverySubject assigned(ApprovalTaskId taskId) {
        return subject(DeliveryPurpose.TASK_ASSIGNED, taskId);
    }

    private static DeliverySubject completed(ApprovalCompleted event) {
        return DeliverySubject.of(DeliveryPurpose.APPROVAL_COMPLETED, event.instanceId().value());
    }
}
