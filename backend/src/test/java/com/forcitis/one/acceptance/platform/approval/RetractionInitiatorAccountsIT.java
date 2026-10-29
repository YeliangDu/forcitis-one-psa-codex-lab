package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RetractionResult;
import com.forcitis.one.platform.approval.api.RoundNumber;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P2 review P2-M2 (NEXT_BATCH_PLAN 7.2 C4 撤回时发起人的比较; ADR-0033 section 3 item 3): only the initiator withdraws or cancels an
 * approval. The initiator is an employee, the person asking is an account: the account is turned into its employee before the
 * comparison. Real accounts (WeCom login), whose UUIDs equal no employee id.
 */
class RetractionInitiatorAccountsIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(RetractionInitiatorAccountsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ApprovalProcesses approvals;

    @Test
    @DisplayName("P2-M2 C4 撤回时发起人的比较（真实账号）：A 发起的审批 B 撤回与撤销都被拒（抛 AccessDeniedException，审批仍在进行），各写一条 platform.approval.request.denied、操作人为 B 的 uuid；A 本人撤回成功")
    void onlyTheInitiatorRetracts() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID maintainer = newEmployeeId();
        UUID initiator = newEmployeeId();
        UUID stranger = newEmployeeId();
        UUID projectManager = newEmployeeId();
        for (UUID person : new UUID[] {maintainer, initiator, stranger, projectManager}) {
            employee(person, unit, null);
        }
        grantByOperations(maintainer, "org_data_admin");
        SignedIn maintainerSession = signIn(maintainer);
        SignedIn initiatorAccount = signIn(initiator);
        SignedIn strangerAccount = signIn(stranger);
        signIn(projectManager);
        maintainWorkCalendar(maintainerSession);
        LegalEntityId legalEntity = new LegalEntityId(createLegalEntity(maintainerSession, newLegalEntityCode("RI"), "Synthetic retraction"));
        DocumentReference document = new DocumentReference(new DocumentTypeCode("TIMESHEET"), newDocumentId());
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "acceptance.timesheet-submission")) {
            approvals.start(new ApprovalStartRequest(TimesheetApprovalProcess.KEY, document, legalEntity, employeeIdOf(initiator), new RoundNumber(1),
                    UUID.randomUUID(), Set.of("projectHours"), List.of(new ApprovalField("projectManagerId", List.of(text(projectManager)))),
                    new ContentHash("synthetic-content"), new ApprovalSummary("Synthetic timesheet " + document.documentId())));
        }

        actInSession(strangerAccount);
        assertThatThrownBy(() -> approvals.withdraw(TimesheetApprovalProcess.KEY, document, new UserId(strangerAccount.userId()), "synthetic: not mine"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> approvals.cancel(TimesheetApprovalProcess.KEY, document, new UserId(strangerAccount.userId()), "synthetic: not mine"))
                .isInstanceOf(AccessDeniedException.class);

        List<Map<String, Object>> denied = Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> auditQuery("actorId", strangerAccount.userId().toString(), "platform.approval.request.denied"), found -> found.size() >= 2);
        assertThat(denied).hasSize(2).allSatisfy(entry -> assertThat(entry.get("actorId")).isEqualTo(strangerAccount.userId().toString()));
        assertThat(approvals.progress(TimesheetApprovalProcess.KEY, document).orElseThrow().status()).as("the approval still runs")
                .isEqualTo(ApprovalStatus.RUNNING);

        actInSession(initiatorAccount);
        assertThat(approvals.withdraw(TimesheetApprovalProcess.KEY, document, new UserId(initiatorAccount.userId()), "synthetic: wrong hours"))
                .isEqualTo(RetractionResult.DONE);
    }
}
