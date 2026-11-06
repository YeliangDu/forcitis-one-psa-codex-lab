package com.forcitis.one.organization.payroll.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.Money;
import com.forcitis.one.organization.payroll.api.BonusInputApprovalProcess;
import com.forcitis.one.organization.payroll.api.PayrollError;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * #1436: the approvers of a bonus input see whose bonus, which month, the amount and the reason, which the task detail and the cards leave out. Only the person
 * who may act on the open task right now (its assignee, or a candidate while nobody holds it), in an ordinary session, reads it; everybody else, whatever their payroll permissions, is
 * told the same as for an unknown task. There is no permission to check: the task decides who sees the data.
 */
@Service
public class GetBonusInputApproval {

    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;

    GetBonusInputApproval(ApprovalProcesses approvals, CurrentUser currentUser) {
        this.approvals = approvals;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public BonusInputApprovalView execute(ApprovalTaskId taskId, boolean ordinarySession) {
        // The administrator mode and an emergency session handle no approvals (I-11): the data is not shown there even to a person who is a candidate.
        if (!ordinarySession) {
            throw notFound();
        }
        ApprovalInstanceId instance = approvals.instanceOfTaskOpenTo(BonusInputApprovalProcess.KEY, taskId, currentUser.userId()).orElseThrow(GetBonusInputApproval::notFound);
        Map<String, List<String>> fields = approvals.formFields(instance);
        return new BonusInputApprovalView(new EmploymentRelationshipId(UUID.fromString(value(fields, BonusInputApprovalProcess.EMPLOYMENT_RELATIONSHIP_ID))),
                LocalDate.parse(value(fields, BonusInputApprovalProcess.PAYROLL_MONTH_START_DATE)),
                Money.of(value(fields, BonusInputApprovalProcess.BONUS_AMOUNT), value(fields, BonusInputApprovalProcess.BONUS_CURRENCY)),
                value(fields, BonusInputApprovalProcess.REASON_KEY));
    }

    private static DomainException notFound() {
        return new DomainException(PayrollError.ORGANIZATION_PAYROLL_BONUS_INPUT_NOT_FOUND, ErrorKind.NOT_FOUND, "no such approval task");
    }

    private static String value(Map<String, List<String>> fields, String name) {
        List<String> values = fields.get(name);
        if (values == null || values.size() != 1) {
            throw new IllegalStateException("the bonus input approval carries no single value for " + name);
        }
        return values.getFirst();
    }
}
