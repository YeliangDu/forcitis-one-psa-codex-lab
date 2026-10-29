package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.P5Names;
import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.application.CancelTimesheet;
import com.forcitis.one.cost.timesheet.application.GetTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import com.forcitis.one.kernel.Hours;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P5 rename B2-B07 (card section 14, RULING-P5-4): the owner pulling a submitted timesheet out of its approval is published as
 * cost.timesheet.TimesheetApprovalWithdrawn. The old name is in neither the event catalogues, the aggregate model, the list of actions
 * that must be audited, the registry of published events nor the audit trail.
 */
@Testcontainers
class TimesheetApprovalWithdrawnRenameIT extends P5Support {

    static final Path EVENTS = Path.of("../docs/architecture/events");
    static final Path AGGREGATES = Path.of("../docs/architecture/ssot/aggregates/built.yaml");
    static final Path MUST_AUDIT = Path.of("../docs/architecture/must-audit-actions.yaml");
    static final String NEW_NAME = "TimesheetApprovalWithdrawn";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    @Autowired
    SubmitTimesheet submitTimesheet;

    @Autowired
    GetTimesheet getTimesheet;

    @Autowired
    CancelTimesheet cancelTimesheet;

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

    @Test
    @DisplayName("R-1b P5 随带改名 B2-B07：事件目录、聚合模型与审计清单只有新名 TimesheetApprovalWithdrawn，旧名 TimesheetCancelled 不在")
    void theCataloguesUseOnlyTheNewName() throws Exception {
        for (Path file : List.of(EVENTS.resolve("asyncapi.yaml"), EVENTS.resolve("timesheet.yaml"), AGGREGATES, MUST_AUDIT)) {
            assertThat(Files.readString(file)).as(file.toString()).contains(NEW_NAME).doesNotContain(P5Names.OLD_CANCELLED_EVENT);
        }
        assertThat(Files.readString(MUST_AUDIT)).contains(P5Names.WITHDRAWN_EVENT);
    }

    @Test
    @DisplayName("R-1b P5 随带改名 B2-B07：本人取消审批中的工时单后发布的事件类型是 cost.timesheet.TimesheetApprovalWithdrawn，审计动作同名，旧名不出现")
    void aCancelledSubmissionIsPublishedAsTimesheetApprovalWithdrawn() throws Exception {
        TimesheetId timesheet = new TimesheetId(UUID.randomUUID());
        actInSession(initiatorAccount);
        submitTimesheet.execute(new SubmitTimesheetCommand(timesheet, TODAY.minusDays(3), Hours.of("8"), employeeIdOf(projectManager)));
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> {
            actInSession(initiatorAccount);
            return getTimesheet.execute(timesheet).canWithdraw();
        });

        actInSession(initiatorAccount);
        cancelTimesheet.execute(timesheet, "synthetic: not needed", OptionalLong.empty());

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> auditedActions("cost.timesheet.").contains(P5Names.WITHDRAWN_EVENT));
        assertThat(registeredEnvelopes().stream().map(envelope -> envelope.path("eventType").asString())).contains(P5Names.WITHDRAWN_EVENT)
                .noneMatch(type -> type.endsWith("." + P5Names.OLD_CANCELLED_EVENT));
        assertThat(auditedActions("cost.timesheet.")).noneMatch(action -> action.endsWith("." + P5Names.OLD_CANCELLED_EVENT));
    }
}
