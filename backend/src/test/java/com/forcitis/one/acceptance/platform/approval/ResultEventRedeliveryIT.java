package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.approval.api.TaskEffect;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * RULE-304 as corrected 2026-10-21: delivery of results is the event publication register's job. An event is written in the same
 * transaction as the decision and resent from the register until acknowledged; nothing is rebuilt from the engine's own records, so a
 * completion the engine made without a decision never goes out as a result. The register is read and the redelivery is asked through
 * the framework's public IncompleteEventPublications (the redelivery job calls exactly that).
 */
@Testcontainers
class ResultEventRedeliveryIT extends P5Support {

    static final String APPROVAL_COMPLETED = "ApprovalCompleted";
    static final String TASK_HANDLED = "TaskHandled";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    @Autowired
    IncompleteEventPublications incomplete;

    private List<Map<String, Object>> publicationsOf(String table, String eventType, DocumentReference document) throws Exception {
        return query("platform_integration", "select id, listener_id, event_type, serialized_event, completion_date from " + table
                + " where event_type like ? and serialized_event like ? order by publication_date, id", "%." + eventType, "%" + document.documentId() + "%");
    }

    private List<Map<String, Object>> allPublicationsOf(String eventType, DocumentReference document) throws Exception {
        List<Map<String, Object>> all = new java.util.ArrayList<>(publicationsOf("event_publication", eventType, document));
        all.addAll(publicationsOf("event_publication_archive", eventType, document));
        return all;
    }

    private DocumentReference decidedByTheProjectManager() throws Exception {
        DocumentReference document = startProjectHours(projectManager);
        UUID task = openTaskIds(document).getFirst();
        actOk(task, projectManagerAccount, "claim", null);
        actOk(task, projectManagerAccount, "decide", "{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}");
        return document;
    }

    @Test
    @DisplayName("RULE-304：任务由人办结、结果事件在登记簿里但未确认时，重投作业运行后同一个事件原样重发（事件编号、内容不变），确认后不再有未完成的登记")
    void anUnacknowledgedResultIsResentUnchanged() throws Exception {
        DocumentReference document = decidedByTheProjectManager();
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> {
            List<Map<String, Object>> done = publicationsOf("event_publication_archive", APPROVAL_COMPLETED, document);
            return !done.isEmpty() && publicationsOf("event_publication", APPROVAL_COMPLETED, document).isEmpty();
        });
        List<Map<String, Object>> delivered = publicationsOf("event_publication_archive", APPROVAL_COMPLETED, document);
        Map<String, Object> row = delivered.getFirst();
        UUID publicationId = (UUID) row.get("id");
        String content = (String) row.get("serialized_event");
        // The listener's acknowledgement never came: the row is back among the open ones.
        try (var registry = schemaConnection("platform_integration")) {
            registry.setAutoCommit(false);
            try (var move = registry.createStatement()) {
                move.executeUpdate("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date, status, completion_attempts) "
                        + "select id, listener_id, event_type, serialized_event, publication_date, null, 'FAILED', 1 from event_publication_archive where id = '" + publicationId + "'");
                move.executeUpdate("delete from event_publication_archive where id = '" + publicationId + "'");
            }
            registry.commit();
        }
        assertThat(publicationsOf("event_publication", APPROVAL_COMPLETED, document)).as("the unacknowledged publication").hasSize(1);

        incomplete.resubmitIncompletePublications(publication -> publication.getIdentifier().equals(publicationId));

        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> publicationsOf("event_publication", APPROVAL_COMPLETED, document).isEmpty());
        List<Map<String, Object>> after = allPublicationsOf(APPROVAL_COMPLETED, document);
        assertThat(after).as("still the one publication of the one event, now acknowledged").hasSize(delivered.size());
        assertThat(after).extracting(found -> found.get("id")).contains(publicationId);
        assertThat(after.stream().filter(found -> found.get("id").equals(publicationId)).findFirst().orElseThrow().get("serialized_event"))
                .as("resent unchanged").isEqualTo(content);
        assertThat(after.stream().filter(found -> found.get("id").equals(publicationId)).findFirst().orElseThrow().get("completion_date")).isNotNull();
    }

    private static long distinctEvents(List<Map<String, Object>> publications) {
        return publications.stream().map(found -> JSON.readTree(found.get("serialized_event").toString()).path("envelope").path("eventId").asString()).distinct().count();
    }

    @Test
    @DisplayName("RULE-304：任务办结后，登记簿里该单据按事件编号去重恰好有 1 个 TaskHandled 与 1 个 ApprovalCompleted，内容为通过（同事务写入的失败注入未做，此例只断言登记的事实）")
    void theDecisionIsRegisteredAsExactlyOneTaskResultAndOneProcessResult() throws Exception {
        DocumentReference document = decidedByTheProjectManager();

        List<Map<String, Object>> handled = allPublicationsOf(TASK_HANDLED, document);
        List<Map<String, Object>> completed = allPublicationsOf(APPROVAL_COMPLETED, document);

        assertThat(distinctEvents(handled)).as("task results of the decision").isEqualTo(1);
        assertThat(distinctEvents(completed)).as("process results of the decision").isEqualTo(1);
        assertThat(handled.stream().map(found -> found.get("serialized_event").toString()).toList()).allSatisfy(json -> assertThat(json).contains("APPROVED"));
        assertThat(completed.stream().map(found -> found.get("serialized_event").toString()).toList()).allSatisfy(json -> assertThat(json).contains("APPROVED"));
    }

    @Test
    @DisplayName("RULE-304 D-1a-66：任务结果事件的办理效果恰好是 BINDING、ADVISORY、AUTOMATIC——没有「跳过」类取值，因为无决定的跳过不发任何事件")
    void theTaskEffectsAreExactlyBindingAdvisoryAndAutomatic() {
        assertThat(Arrays.stream(TaskEffect.values()).map(Enum::name).toList()).containsExactlyInAnyOrder("BINDING", "ADVISORY", "AUTOMATIC");
    }

    @Test
    @DisplayName("RULE-304 D-1a-66：人办结后，登记簿里该单据的 TaskHandled 事件内容含 BINDING（人的决定按自身发布，不是 AUTOMATIC 或 ADVISORY）")
    void aHumanDecisionIsPublishedAsBinding() throws Exception {
        DocumentReference document = decidedByTheProjectManager();

        List<Map<String, Object>> handled = allPublicationsOf(TASK_HANDLED, document);

        assertThat(handled).as("the register holds the task result of the decision").isNotEmpty();
        assertThat(handled.stream().map(found -> found.get("serialized_event").toString()).toList()).allSatisfy(json -> {
            assertThat(json).contains("BINDING");
            assertThat(json).doesNotContain("AUTOMATIC").doesNotContain("ADVISORY");
        });
    }
}
