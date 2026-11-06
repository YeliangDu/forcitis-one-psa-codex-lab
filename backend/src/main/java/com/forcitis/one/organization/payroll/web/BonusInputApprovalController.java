package com.forcitis.one.organization.payroll.web;

import com.forcitis.one.organization.payroll.application.GetBonusInputApproval;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signed in is enough: who may read is decided by the task (its assignee or open candidates), not by a payroll permission, and every other caller, in the
 * administrator mode too, is answered 404. The platform approval controller's ordinary-session guard is not copied: it would answer 403.
 */
@RestController
@RequestMapping("/api/v1/organization/payroll/bonus-input-approvals")
class BonusInputApprovalController {

    private final GetBonusInputApproval getBonusInputApproval;

    BonusInputApprovalController(GetBonusInputApproval getBonusInputApproval) {
        this.getBonusInputApproval = getBonusInputApproval;
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("isAuthenticated()")
    BonusInputApprovalResponse get(@PathVariable UUID taskId) {
        return BonusInputApprovalResponse.of(getBonusInputApproval.execute(new ApprovalTaskId(taskId)));
    }
}
