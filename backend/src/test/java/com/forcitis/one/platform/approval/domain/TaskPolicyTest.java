package com.forcitis.one.platform.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import java.util.Optional;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskPolicyTest {

    static TaskView task(UserId assignee, Set<UserId> candidates) {
        return new TaskView(new ApprovalTaskId("t1"), new ApprovalInstanceId("i1"), ProcessKey.COST_TIMESHEET_APPROVAL,
                new DocumentReference(DocumentType.TIMESHEET, new DocumentId("doc-1")), new LegalEntityId(TestIds.uuid(1)), "approverReview",
                "Approver review", new EmployeeId(1), Optional.ofNullable(assignee), candidates, Instant.EPOCH, true, Optional.empty(), false,
                new ContentHash("hash"));
    }

    @Test
    @DisplayName("RULE-276/I-7 before a claim every candidate may act, a stranger and a null actor may not")
    void candidatesMayActBeforeTheClaim() {
        TaskView open = task(null, Set.of(new UserId(2), new UserId(3)));
        assertThat(TaskActors.mayAct(open, new UserId(2))).isTrue();
        assertThat(TaskActors.mayAct(open, new UserId(3))).isTrue();
        assertThat(TaskActors.mayAct(open, new UserId(4))).isFalse();
        assertThat(TaskActors.mayAct(open, null)).isFalse();
    }

    @Test
    @DisplayName("RULE-276/I-7 after a claim only the claimer may act, not even the other candidates or the initiator")
    void onlyTheClaimerMayActAfterTheClaim() {
        TaskView claimed = task(new UserId(2), Set.of(new UserId(2), new UserId(3)));
        assertThat(TaskActors.mayAct(claimed, new UserId(2))).isTrue();
        assertThat(TaskActors.mayAct(claimed, new UserId(3))).isFalse();
        assertThat(TaskActors.mayAct(claimed, new UserId(1))).isFalse();
    }

    @Test
    @DisplayName("RULE-261 row tasks of one (instance, node) are one to-do represented by the newest; other nodes stay separate")
    void rowTasksMergePerInstanceAndNode() {
        LegacyInstanceId p1 = new LegacyInstanceId("P1");
        LegacyInstanceId p2 = new LegacyInstanceId("P2");
        List<LegacyTodo> merged = InboxMerger.mergeRowTasks(List.of(
                new LegacyTodo("t1", p1, "N", "Node", "title", Instant.parse("2026-10-01T00:00:00Z"), null),
                new LegacyTodo("t2", p1, "N", "Node", "title", Instant.parse("2026-10-03T00:00:00Z"), null),
                new LegacyTodo("t3", p1, "N", "Node", "title", Instant.parse("2026-10-02T00:00:00Z"), null),
                new LegacyTodo("t4", p2, "N", "Node", "title", Instant.parse("2026-10-04T00:00:00Z"), null),
                new LegacyTodo("t5", p1, "M", "Other", "title", Instant.parse("2026-10-05T00:00:00Z"), null)));
        assertThat(merged).extracting(LegacyTodo::taskKey).containsExactly("t5", "t4", "t2");
    }
}
