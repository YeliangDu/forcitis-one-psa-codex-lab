package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.application.CommandReadViews.Hold;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetSeparationHold {
    private final CommandReadQueries queries;
    private final SeparationHoldReview review;
    private final CurrentUser currentUser;

    GetSeparationHold(CommandReadQueries queries, SeparationHoldReview review, CurrentUser currentUser) {
        this.queries = queries;
        this.review = review;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + SeparationHoldService.PERMISSION + "')")
    public Hold execute(SeparationHoldId id) {
        CommandReadQueries.Hold row = queries.hold(id).orElseThrow(CommandReadViews::holdNotFound);
        return Hold.of(row, review.review(row.employeeId(), currentUser.userId()).remainingAdministrators());
    }
}
