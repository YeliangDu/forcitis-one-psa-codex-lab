package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RoundNumber;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P3b, ADR-0033 section 3 item 3 (card section 4 item 6): the people a form field names are employee ids, canonical UUIDs. A value
 * that is not one (the old whole number, a negative number, any other text) makes the assignment fail with
 * PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID and an alert; it is not skipped while the valid values go on, and no approval or task is written.
 * Until P3b such a value is dropped without a word. Started through the approval api as the timesheet module does (no HTTP start, I-5).
 */
@Testcontainers
class AssigneeInputUuidIT extends AccountsAcceptanceSupport {

    static final String ASSIGNEE_INPUT_INVALID = "PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID";
    static final String FORM_FIELD = "projectManagerId";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    @Autowired
    ApprovalProcesses approvals;

    LegalEntityId legalEntity;
    UUID initiator;
    UUID projectManager;

    @BeforeEach
    void anInitiatorAndAProjectManager() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID maintainer = newEmployeeId();
        employee(maintainer, unit, null);
        grantByOperations(maintainer, "org_data_admin");
        SignedIn maintainerSession = signIn(maintainer);
        maintainWorkCalendar(maintainerSession);
        legalEntity = new LegalEntityId(createLegalEntity(maintainerSession, newLegalEntityCode("AI"), "Synthetic assignee input"));
        UUID supervisor = newEmployeeId();
        initiator = newEmployeeId();
        projectManager = newEmployeeId();
        employee(supervisor, unit, null);
        employee(initiator, unit, supervisor);
        employee(projectManager, unit, null);
        signIn(supervisor);
        signIn(initiator);
        signIn(projectManager);
    }

    private static DocumentReference newDocument() {
        return new DocumentReference(new DocumentTypeCode("TIMESHEET"), newDocumentId());
    }

    private void start(DocumentReference document, List<String> projectManagers) {
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "acceptance.timesheet-submission")) {
            approvals.start(new ApprovalStartRequest(TimesheetApprovalProcess.KEY, document, legalEntity, employeeIdOf(initiator), new RoundNumber(1),
                    UUID.randomUUID(), Set.of("projectHours"), List.of(new ApprovalField(FORM_FIELD, projectManagers)),
                    new ContentHash("synthetic-content"), new ApprovalSummary("Synthetic timesheet " + document.documentId())));
        }
    }

    private int rows(String table, DocumentReference document) throws Exception {
        return approvalRowsOf(table, document);
    }

    private static String errorCodeIn(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DomainException refused) {
                return refused.errorCode().code();
            }
        }
        return null;
    }

    private void assertRefusedAsInvalidInput(List<String> values) throws Exception {
        DocumentReference document = newDocument();
        int mark = logMark();

        Throwable failure = catchThrowable(() -> start(document, values));

        assertThat(failure).as("starting with %s=%s", FORM_FIELD, values).isNotNull();
        assertThat(errorCodeIn(failure)).as("the refusal of %s", values).isEqualTo(ASSIGNEE_INPUT_INVALID);
        assertThat(alertsSince(mark)).as("an alert for %s", values).isNotEmpty();
        assertThat(rows("approval_tasks", document)).as("no task is written for %s", values).isZero();
        assertThat(rows("approval_requests", document)).as("no approval is written for %s", values).isZero();
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 3 条：表单选人字段混有非 uuid 的值（旧整数形态）时指派整体失败并告警（PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID），不跳过该值、不写审批与任务")
    void aNonUuidValueBesideAValidOneFailsTheAssignment() throws Exception {
        assertRefusedAsInvalidInput(List.of(text(projectManager), "424242"));
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 3 条：表单选人字段只有非 uuid 的值（负数、任意文本、大写 uuid）时同样失败并告警，不落到流程管理员兜底")
    void onlyNonUuidValuesFailTheAssignment() throws Exception {
        assertRefusedAsInvalidInput(List.of("-5"));
        assertRefusedAsInvalidInput(List.of("not-an-employee"));
        assertRefusedAsInvalidInput(List.of(projectManager.toString().toUpperCase(java.util.Locale.ROOT)));
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 3 条（对照）：表单选人字段是员工的规范 uuid 时照常指派，写一条待办任务")
    void aCanonicalUuidIsAssigned() throws Exception {
        assertThat(UUID_IDS).as("the employee ids the form names are UUIDs").isTrue();

        DocumentReference document = newDocument();
        start(document, List.of(projectManager.toString()));

        assertThat(rows("approval_tasks", document)).isEqualTo(1);
    }
}
