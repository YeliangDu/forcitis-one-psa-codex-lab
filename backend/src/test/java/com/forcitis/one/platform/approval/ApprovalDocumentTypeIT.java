package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AP-14 (ADR-0030 U-3): until the quote module goes live, approval takes TIMESHEET documents only; the guard is the write path, not a database list. */
class ApprovalDocumentTypeIT extends ApprovalIntegrationSupport {

    private ApprovalStartRequest requestFor(String type) {
        return new ApprovalStartRequest(TimesheetApprovalProcess.KEY, new DocumentReference(new DocumentTypeCode(type), new DocumentId(UUID.randomUUID())),
                LEGAL_ENTITY, TestAccounts.employee(1), new RoundNumber(1), UUID.randomUUID(), Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(2).toString()))),
                new ContentHash("hash"), new ApprovalSummary("summary"));
    }

    @Test
    @DisplayName("a registered type without an approval process is refused with PLATFORM_APPROVAL_NO_PROCESS, and no request row is written")
    void quoteAndJournalVoucherAreRefused() {
        for (String type : new String[] {"QUOTE", "JOURNAL_VOUCHER"}) {
            assertThatThrownBy(() -> tx.execute(status -> approvals.start(requestFor(type))))
                    .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NO_PROCESS));
        }
    }

    @Test
    @DisplayName("a document type without a process is reported before the round: a QUOTE asked for in round 2 gets NO_PROCESS, not ROUND_MISMATCH")
    void typeMismatchIsReportedBeforeTheRound() {
        ApprovalStartRequest secondRound = new ApprovalStartRequest(TimesheetApprovalProcess.KEY,
                new DocumentReference(new DocumentTypeCode("QUOTE"), new DocumentId(UUID.randomUUID())), LEGAL_ENTITY, TestAccounts.employee(1),
                new RoundNumber(2), UUID.randomUUID(), Set.of("no-such-flag"), List.of(), new ContentHash("hash"), new ApprovalSummary("summary"));

        assertThatThrownBy(() -> tx.execute(status -> approvals.start(secondRound)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NO_PROCESS));
    }

    @Test
    @DisplayName("a code that is not registered at all is refused too (a timesheet start is what every other approval IT does)")
    void unregisteredCodeIsRefused() {
        assertThatThrownBy(() -> tx.execute(status -> approvals.start(requestFor("PAYMENT"))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NO_PROCESS));
    }
}
