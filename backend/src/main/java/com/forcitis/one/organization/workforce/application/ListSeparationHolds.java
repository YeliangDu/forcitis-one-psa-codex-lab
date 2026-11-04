package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.application.CommandReadViews.Hold;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListSeparationHolds {
    public static final String LIST = "separation-holds";
    private final CommandReadQueries queries;
    private final SeparationHoldReview review;
    private final CurrentUser currentUser;

    ListSeparationHolds(CommandReadQueries queries, SeparationHoldReview review, CurrentUser currentUser) {
        this.queries = queries;
        this.review = review;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + SeparationHoldService.PERMISSION + "')")
    public CursorPage<Hold> execute(@Nullable String status, PageRequest page) {
        if (status != null && !status.equals("HELD")) {
            throw new InvalidRequestException("status must be HELD");
        }
        List<CommandReadQueries.Hold> found = queries.holds(CommandReadViews.position(page.cursorValue()), page.limit() + 1);
        List<CommandReadQueries.Hold> shown = found.stream().limit(page.limit()).toList();
        String next = found.size() > shown.size() ? PageRequest.encode(LIST, shown.getLast().createdAt() + "|" + shown.getLast().separationHoldId().value()) : null;
        return CursorPage.of(shown.stream().map(row -> Hold.of(row, review.review(row.employeeId(), currentUser.userId()).remainingAdministrators())).toList(), next);
    }
}
