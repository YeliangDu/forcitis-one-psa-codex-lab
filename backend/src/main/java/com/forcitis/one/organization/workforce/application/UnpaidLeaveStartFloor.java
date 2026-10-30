package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Starting unpaid leave for an available super_admin or security_auditor holder is floor-protected like a separation (user decision 2026-10-30
 * 「加，同离职一样」; ADR-0068 items 2.2 and 2.3): it is refused when no available super administrator would remain, and the remaining one is told. The
 * caller holds the separation lock. The count is the one of {@link PersonnelCommandRepository#leavingEmployees()}, so another holder's entered unpaid-leave
 * start, today or later, already counts as gone; the person's own entered start is subtracted once. An ordinary employee is not asked about.
 */
@Component
class UnpaidLeaveStartFloor {
    private final SeparationReview review;
    private final PersonnelCommandRepository commands;
    private final UnpaidLeaveStartNotice notice;

    UnpaidLeaveStartFloor(SeparationReview review, PersonnelCommandRepository commands, UnpaidLeaveStartNotice notice) {
        this.review = review;
        this.commands = commands;
        this.notice = notice;
    }

    /** Refuses, with nothing written yet, when the start would leave no available super administrator; otherwise returns the verdict for {@link #announce}. */
    SeparationReview.Verdict check(PersonnelCommand start) {
        SeparationReview.Verdict verdict = review.review(List.of(start.employeeId()), commands.leavingEmployees(), null);
        if (!verdict.separatedAdministrators().isEmpty() && verdict.remainingAdministrators() == 0) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE, "no available super_admin would remain");
        }
        return verdict;
    }

    /** Tells the remaining administrator once the start is recorded, when the start was of an administrator and one remains. */
    void announce(PersonnelCommand start, SeparationReview.Verdict verdict, UserId enteredBy) {
        if (!verdict.separatedAdministrators().isEmpty() && verdict.remainingAdministrators() == 1) {
            notice.remainingOne(start, verdict.noticeRecipients(), commands.heldEmployees(), enteredBy);
        }
    }
}
