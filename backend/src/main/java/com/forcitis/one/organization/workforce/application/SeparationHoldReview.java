package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class SeparationHoldReview {
    private final PersonnelCommandRepository commands;
    private final SeparationReview review;

    SeparationHoldReview(PersonnelCommandRepository commands, SeparationReview review) {
        this.commands = commands;
        this.review = review;
    }

    SeparationReview.Verdict review(EmployeeId employee, UserId releaser) {
        return review.review(List.of(employee), commands.excludedEmployees(), releaser);
    }
}
