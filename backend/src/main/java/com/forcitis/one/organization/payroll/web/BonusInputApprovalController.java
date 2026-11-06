package com.forcitis.one.organization.payroll.web;

import com.forcitis.one.organization.payroll.application.GetBonusInputApproval;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.identity.api.SessionRoles;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signed in is enough: who may read is decided by the task (its assignee or open candidates), not by a payroll permission, and every other caller is answered
 * 404. A session that is not an ordinary one (administrator mode, emergency) is answered 404 too, whoever it belongs to (I-11); the platform approval
 * controller's hasRole guard is not copied because it would answer 403.
 */
@RestController
@RequestMapping("/api/v1/organization/bonus-input-approvals")
class BonusInputApprovalController {

    private final GetBonusInputApproval getBonusInputApproval;

    BonusInputApprovalController(GetBonusInputApproval getBonusInputApproval) {
        this.getBonusInputApproval = getBonusInputApproval;
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("isAuthenticated()")
    BonusInputApprovalResponse get(@PathVariable UUID taskId, Authentication authentication) {
        boolean ordinarySession = authentication.getAuthorities().stream().anyMatch(each -> ("ROLE_" + SessionRoles.ORDINARY).equals(each.getAuthority()));
        return BonusInputApprovalResponse.of(getBonusInputApproval.execute(new ApprovalTaskId(taskId), ordinarySession));
    }
}
