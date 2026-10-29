package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.application.GetTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * RULE-308 as corrected 2026-10-21: there is no generic write-back to a business table. When a process ends, a typed result event is
 * published and the module that owns the document type updates its own document, with its own status check; nothing is built from a
 * table name held in configuration. A real timesheet goes through a real approval here: the approval module only publishes, the
 * timesheet module changes the timesheet.
 */
class TypedResultEventIT extends P5Support {

    static final TestDatabase postgres = TestPostgres.database(TypedResultEventIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    SubmitTimesheet submitTimesheet;

    @Autowired
    GetTimesheet getTimesheet;

    @BeforeEach
    void theInitiatorHasALegalEntityAndTimesheetsAreNumbered() throws Exception {
        UUID maintainer = newEmployeeId();
        employee(maintainer, unit, null);
        grantByOperations(maintainer, "org_data_admin");
        grantByOperations(maintainer, "business_admin");
        SignedIn maintainerSession = signIn(maintainer);
        attributeEmployee(maintainerSession, initiator, legalEntity.value());
        confirmSeededNumberingRule("TIMESHEET", maintainerSession);
    }

    private TimesheetStatus statusOf(TimesheetId timesheet) {
        actInSession(initiatorAccount);
        return getTimesheet.execute(timesheet).status();
    }

    private TimesheetId submittedTimesheet() {
        TimesheetId timesheet = new TimesheetId(UUID.randomUUID());
        actInSession(initiatorAccount);
        submitTimesheet.execute(new SubmitTimesheetCommand(timesheet, TODAY.minusDays(3), Hours.of("8"), employeeIdOf(projectManager)));
        return timesheet;
    }

    private DocumentReference documentOf(TimesheetId timesheet) {
        return new DocumentReference(new DocumentTypeCode("TIMESHEET"), documentIdOf(timesheet.value()));
    }

    private UUID openTaskOf(TimesheetId timesheet) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> !openTaskIds(documentOf(timesheet)).isEmpty());
        try {
            return openTaskIds(documentOf(timesheet)).getFirst();
        } catch (java.sql.SQLException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private void approveAsProjectManager(UUID task) throws Exception {
        actOk(task, projectManagerAccount, "claim", null);
        actOk(task, projectManagerAccount, "decide", "{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}");
    }

    @Test
    @DisplayName("RULE-308：流程以通过结束，发布带类型的结果事件 ApprovalCompleted（单据类型 TIMESHEET、结果 APPROVED），拥有工时单的模块自己把工时单改为已通过")
    void theOwningModuleAppliesATypedResultToItsOwnDocument() throws Exception {
        TimesheetId timesheet = submittedTimesheet();
        assertThat(statusOf(timesheet)).isEqualTo(TimesheetStatus.SUBMITTED);

        approveAsProjectManager(openTaskOf(timesheet));

        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> statusOf(timesheet) == TimesheetStatus.APPROVED);
        List<Map<String, Object>> published = query("platform_integration", """
                select event_type, serialized_event from event_publication_archive where event_type like '%.ApprovalCompleted' and serialized_event like ?
                union all select event_type, serialized_event from event_publication where event_type like '%.ApprovalCompleted' and serialized_event like ?""",
                "%" + timesheet.value() + "%", "%" + timesheet.value() + "%");
        assertThat(published).as("typed result events of the timesheet's approval").isNotEmpty();
        assertThat(published.stream().map(row -> (String) row.get("serialized_event")).toList()).allSatisfy(json -> {
            assertThat(json).contains("TIMESHEET");
            assertThat(json).contains("APPROVED");
        });
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> registeredEnvelopes("TimesheetApproved").stream()
                        .anyMatch(envelope -> envelope.path("aggregateId").asString().equals(timesheet.value().toString())));
    }

    @Test
    @DisplayName("RULE-308：晚到或重复的结果事件不能覆盖较新的状态——已通过的工时单收到同一轮的迟到「驳回」结果，状态仍是已通过（单据所属模块自己做状态检查）")
    void aLateResultCannotOverwriteANewerStatus() throws Exception {
        TimesheetId timesheet = submittedTimesheet();
        approveAsProjectManager(openTaskOf(timesheet));
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> statusOf(timesheet) == TimesheetStatus.APPROVED);
        EventEnvelope envelope = (EventEnvelope) envelopeFor("platform.approval.ApprovalCompleted", "ApprovalRequest", UUID.randomUUID());
        ApprovalCompleted late = new ApprovalCompleted(envelope, documentOf(timesheet), TimesheetApprovalProcess.KEY,
                new ApprovalInstanceId(UUID.randomUUID()), new RoundNumber(1), employeeIdOf(initiator), ApprovalOutcome.REJECTED,
                new Reason("synthetic late rejection"), null);

        transaction.executeWithoutResult(status -> publisher.publishEvent(late));

        // ApprovalCompleted has two listeners; the check is only meaningful after the timesheet's own listener has acknowledged this event.
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() ->
                !query("platform_integration", "select 1 from event_publication_archive where listener_id like '%ApplyApprovalResult%' and serialized_event like ?",
                        "%" + envelope.eventId() + "%").isEmpty()
                        && query("platform_integration", "select 1 from event_publication where completion_date is null and serialized_event like ?",
                                "%" + envelope.eventId() + "%").isEmpty());
        assertThat(statusOf(timesheet)).as("the late rejection changed nothing").isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    @DisplayName("RULE-308：不存在按配置里的表名拼 SQL 的通用回写——审批模式下没有存放业务表名或状态列名的列，流程定义里没有表名或 approve_status 一类的属性")
    void nothingHoldsATableNameToWriteBackTo() throws Exception {
        List<Map<String, Object>> columns = query("platform_approval", """
                select table_schema || '.' || table_name || '.' || column_name as column_path from information_schema.columns
                 where table_schema in ('platform_approval', 'platform_integration')
                   and (column_name ~* '(business|target|callback|write_?back|source)_?(table|column)' or column_name ~* '^(table_name|status_column|approve_status)$')""");
        assertThat(columns).as("columns that could hold the name of a business table to write to").isEmpty();

        Pattern writeBack = Pattern.compile("(?i)table_?name|approve_?status|write_?back|updateSql");
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:processes/*.bpmn20.xml")) {
            String text = readText(resource);
            assertThat(writeBack.matcher(text).find()).as("process definition %s names a table or a status column to write", resource.getFilename()).isFalse();
        }
    }

    private static String readText(org.springframework.core.io.Resource resource) throws IOException {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
