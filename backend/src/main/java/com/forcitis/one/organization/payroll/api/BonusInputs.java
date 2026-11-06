package com.forcitis.one.organization.payroll.api;

import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.time.LocalDate;
import java.util.List;

/**
 * The manual bonus input port (PY3). A row exists only after its approval passed; there is no pending row. Every method needs a permission of the payroll role
 * (organization.payroll:bonus-input:submit, :update, :read) and fails with AccessDeniedException without it.
 */
public interface BonusInputs {

    /** Starts the approval of one input inside the caller's transaction; a refusal of the approval is the command's refusal. A retry with the same key returns the same instance. */
    ApprovalInstanceId request(RequestBonusInput command);

    /** ACTIVE to VOIDED once; a second void is refused with ORGANIZATION_PAYROLL_BONUS_INPUT_ALREADY_VOIDED. */
    void voidInput(VoidBonusInput command);

    /** ORGANIZATION_PAYROLL_BONUS_INPUT_NOT_FOUND for an unknown id. */
    BonusInputSummary get(BonusInputId id);

    /** The inputs of one relationship with a payroll month in [fromMonth, toMonth], both included, newest month first, then by id. */
    List<BonusInputSummary> listForRelationship(EmploymentRelationshipId relationship, LocalDate fromMonth, LocalDate toMonth);
}
