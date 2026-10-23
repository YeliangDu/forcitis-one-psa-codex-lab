package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import java.util.Optional;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskPolicyTest {

    static TaskView task(UserId assignee, Set<UserId> candidates) {
        return new TaskView(com.forcitis.one.TestApprovalIds.task("t1"), com.forcitis.one.TestApprovalIds.instance("i1"), TimesheetApprovalProcess.KEY,
                new DocumentReference(new DocumentTypeCode("TIMESHEET"), com.forcitis.one.TestApprovalIds.document("doc-1")), new LegalEntityId(TestIds.uuid(1)), "approverReview",
                "Approver review", TestAccounts.employee(1), Optional.ofNullable(assignee), candidates, Instant.EPOCH, true, Optional.empty(), false,
                new ContentHash("hash"));
    }

    @Test
    @DisplayName("RULE-276/I-7 before a claim every candidate may act, a stranger and a null actor may not")
    void candidatesMayActBeforeTheClaim() {
        TaskView open = task(null, Set.of(TestAccounts.account(2), TestAccounts.account(3)));
        assertThat(TaskActors.mayAct(open, TestAccounts.account(2))).isTrue();
        assertThat(TaskActors.mayAct(open, TestAccounts.account(3))).isTrue();
        assertThat(TaskActors.mayAct(open, TestAccounts.account(4))).isFalse();
        assertThat(TaskActors.mayAct(open, null)).isFalse();
    }

    @Test
    @DisplayName("RULE-276/I-7 after a claim only the claimer may act, not even the other candidates or the initiator")
    void onlyTheClaimerMayActAfterTheClaim() {
        TaskView claimed = task(TestAccounts.account(2), Set.of(TestAccounts.account(2), TestAccounts.account(3)));
        assertThat(TaskActors.mayAct(claimed, TestAccounts.account(2))).isTrue();
        assertThat(TaskActors.mayAct(claimed, TestAccounts.account(3))).isFalse();
        assertThat(TaskActors.mayAct(claimed, TestAccounts.account(1))).isFalse();
    }
}
