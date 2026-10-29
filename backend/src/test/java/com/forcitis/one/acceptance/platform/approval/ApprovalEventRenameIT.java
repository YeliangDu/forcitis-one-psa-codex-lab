package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P4Names;
import com.forcitis.one.acceptance.P5Names;
import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.approval.application.EnforceDeadlines;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P5 rename (one-glossary-b2; card 3.5 and acceptance item 12): the events of an overdue approval task and of a handled one are
 * published as platform.approval.ApprovalTaskBecameOverdue and platform.approval.TaskHandled. The old names are in neither the event
 * catalogue nor the list of actions that must be audited nor in the audit trail; the reminder card and the timesheet write-back still
 * happen.
 */
class ApprovalEventRenameIT extends P5Support {

    static final Path EVENTS = Path.of("../docs/architecture/events");
    static final Path MUST_AUDIT = Path.of("../docs/architecture/must-audit-actions.yaml");

    static final TestDatabase postgres = TestPostgres.database(ApprovalEventRenameIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    EnforceDeadlines enforceDeadlines;

    @Test
    @DisplayName("R-1b P5 随带改名：事件目录与审计清单只有新名 ApprovalTaskBecameOverdue、TaskHandled，旧名 ApprovalTaskOverdue、TaskResulted 不在（asyncapi.yaml、approval.yaml、MUST_AUDIT_ACTIONS.yaml）")
    void theCataloguesUseOnlyTheNewNames() throws Exception {
        for (Path file : List.of(EVENTS.resolve("asyncapi.yaml"), EVENTS.resolve("approval.yaml"))) {
            String text = Files.readString(file);
            assertThat(text).as(file.toString()).contains(P5Names.OVERDUE_EVENT).contains(P5Names.HANDLED_EVENT)
                    .doesNotContain(P5Names.OLD_OVERDUE_EVENT).doesNotContain(P5Names.OLD_HANDLED_EVENT);
        }
        String mustAudit = Files.readString(MUST_AUDIT);
        assertThat(mustAudit).contains(P5Names.HANDLED_EVENT).doesNotContain(P5Names.OLD_HANDLED_EVENT).doesNotContain(P5Names.OLD_OVERDUE_EVENT);
    }

    @Test
    @DisplayName("R-1b P5 随带改名：任务办理后发布的事件类型是 platform.approval.TaskHandled，审计动作同名，旧名不出现")
    void aHandledTaskIsPublishedAsTaskHandled() throws Exception {
        DocumentReference document = startProjectHours(projectManager);
        UUID task = openTaskIds(document).getFirst();

        actOk(task, projectManagerAccount, "claim", null);
        actOk(task, projectManagerAccount, "decide", "{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}");

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> auditedActions("platform.approval.").contains(P5Names.HANDLED_EVENT));
        assertThat(registeredEnvelopes().stream().map(envelope -> envelope.path("eventType").asString())).contains(P5Names.HANDLED_EVENT)
                .noneMatch(type -> type.endsWith("." + P5Names.OLD_HANDLED_EVENT));
        assertThat(auditedActions("platform.approval.")).noneMatch(action -> action.endsWith("." + P5Names.OLD_HANDLED_EVENT));
    }

    @Test
    @DisplayName("R-1b P5 随带改名：任务超时后发布的事件类型是 platform.approval.ApprovalTaskBecameOverdue，超时提醒卡照常发出（该事件本身不审计，不在审计动作中），旧名不出现")
    void anOverdueTaskIsPublishedAsApprovalTaskBecameOverdue() throws Exception {
        DocumentReference document = startProjectHours(projectManager);
        UUID task = openTaskIds(document).getFirst();
        Timestamp remindAt = (Timestamp) rows("select remind_at from approval_tasks where task_id = ?", task).getFirst().get("remind_at");
        clock.set(remindAt.toInstant().plusSeconds(60));

        enforceDeadlines.execute();

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> registeredEnvelopes().stream().anyMatch(envelope -> envelope.path("eventType").asString().equals(P5Names.OVERDUE_EVENT)));
        assertThat(registeredEnvelopes().stream().map(envelope -> envelope.path("eventType").asString())).contains(P5Names.OVERDUE_EVENT)
                .noneMatch(type -> type.endsWith("." + P5Names.OLD_OVERDUE_EVENT));
        assertThat(auditedActions("platform.approval.")).noneMatch(action -> action.endsWith("." + P5Names.OLD_OVERDUE_EVENT));
        verify(wecomGateway, timeout(15000).atLeastOnce()).send(argThat(card -> card.subject().toString().contains(P4Names.PURPOSE_OVERDUE)));
    }
}
