package com.forcitis.one.platform.approval;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Skeletons of rules moved out of Phase 1a; enable and fill each when its capability is built. */
class PendingApprovalRulesTest {

    @Test
    @Disabled("pending RULE-251: Phase 2 first consumer (timesheet row-level approval)")
    @DisplayName("RULE-251 a row reaches a node when its routing value activates a path to it")
    void rowReachability() {
        // Given a gateway flow routed by the overheadHours flag to node supervisorReview, rows r1 [overheadHours] and r2 [projectHours]; Then only r1 reaches supervisorReview.
    }

    @Test
    @Disabled("pending RULE-252: Phase 2 first consumer (per-row approver resolution; the non-row part is RULE-249 and done)")
    @DisplayName("RULE-252 row tasks resolve their own approver per row from the row's field value")
    void perRowApproverResolution() {
        // Given a department-head rule whose department field names the unit per row, rows r1 {unit:15} and r2 {unit:16}; Then r1 and r2 go to their department heads.
    }

    @Test
    @Disabled("pending RULE-263: Phase 2 first consumer (row units required, row set narrowed by routing)")
    @DisplayName("RULE-263 a row-approval node needs row units; rows that do not route to it get no task")
    void rowApprovalNodeNeedsRowUnits() {
        // Given rowApproval=true and no rowUnits; Then the start fails loudly. Given rows r1,r2,r3 of which r1,r3 route here; Then 2 tasks.
    }

    @Test
    @Disabled("pending RULE-270: 1b first consumer (explicit proxy approval; own-task batches are covered by TaskActorAuthorizationIT)")
    @DisplayName("RULE-270 a batch approves only the caller's own tasks; an administrator acts only through an explicit proxy, audited with both ids")
    void batchApprovesOnlyOwnTasks() {
        // Given a filter matching tasks of 3 users; When user 1 runs the batch; Then only user 1's tasks are approved.
        // Proxy: AuditEntry(actorId = administrator, onBehalfOfId = assignee) (D-07).
    }

    @Test
    @Disabled("pending RULE-274: Phase 2 first consumer (row-by-row approval covers every pending row)")
    @DisplayName("RULE-274 the approver decides all pending rows at once; any rejected row rejects the node")
    void rowByRowApprovalCoversEveryRow() {
        // Given 3 pending rows; When only R1 and R2 are submitted; Then INCOMPLETE(3, 2).
    }

    @Test
    @Disabled("pending RULE-281: 1b first consumer (node field permissions, unlisted fields read-only, fail closed)")
    @DisplayName("RULE-281 field permissions of a node: unlisted fields are read-only; the configuration is a full overwrite per process type")
    void nodeFieldPermissions() {
        // The fail-closed field-permission check of RULE-280 belongs here: if permissions cannot be loaded, the approval is refused.
    }

    @Test
    @Disabled("pending RULE-282/296: 1b first consumer (batch submission follows single-submit semantics)")
    @DisplayName("RULE-282/296 a batch submission returns the live instance, starts a new round for an ended one")
    void batchSubmission() {
        // Reviewed: no silently returned dead instance; the single-start semantics of RULE-266 apply per document.
    }

    @Test
    @Disabled("pending RULE-284/298: 1b first consumer (return to a specific earlier node)")
    @DisplayName("RULE-284/298 return to an earlier node: single-instance source and target only")
    void returnToEarlierNode() {
        // Given nodes completed Initiator -> Dept head -> Finance and the GM task; Then returnable targets are in completion order.
    }

    @Test
    @Disabled("pending RULE-286/313: 1b first consumer (add countersign on a running parallel multi-instance node)")
    @DisplayName("RULE-286 countersign can only target a running parallel multi-instance node, never a row node")
    void addCountersign() {
        // I-7: only the current assignee adds; duplicates and nulls are dropped.
    }

    @Test
    @Disabled("pending RULE-293: Phase 2 first consumer (row approval: the other unreviewed rows become 已退回, whole sheet re-approved)")
    @DisplayName("RULE-293 return to initiator marks the remaining unreviewed rows RETURNED; the resubmission re-approves the whole sheet")
    void returnMarksRemainingRows() {
    }

    @Test
    @Disabled("pending RULE-297: replaced by RULE-269 under M3: a withdraw ends the round and the initiator starts the next; keeping the process alive is not built")
    @DisplayName("RULE-297 the initiator pulls the approval back to their own node to edit it")
    void withdrawToEdit() {
    }

    @Test
    @Disabled("pending RULE-299: 1b first consumer (notify nodes become unread to-dos until the detail is opened)")
    @DisplayName("RULE-299 a notify task completes automatically with result NOTIFY and shows as unread until the detail page is opened")
    void notifyTasks() {
    }

    @Test
    @Disabled("pending RULE-301: 1b first consumer (auto-complete cascade for notify nodes, skip-initiator-approval and empty nodes)")
    @DisplayName("RULE-301 after a task is created, notify / skip-initiator-approval / empty nodes complete automatically in a fixed order")
    void autoCompleteCascade() {
    }

    @Test
    @Disabled("pending RULE-310: 1b first consumer (document detail visible to function-permission holders or process participants)")
    @DisplayName("RULE-310 a business document is visible to holders of its permission or to participants; others get 'not found'")
    void documentDetailVisibility() {
    }

    @Test
    @Disabled("pending RULE-315: Phase 2 (the old to-be-sent table is replaced by idempotent starts plus transactional events)")
    @DisplayName("RULE-315 asynchronous timesheet process creation")
    void asynchronousTimesheetStart() {
    }
}
