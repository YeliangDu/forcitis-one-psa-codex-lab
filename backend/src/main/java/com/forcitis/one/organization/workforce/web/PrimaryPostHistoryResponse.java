package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries.PrimaryPost;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Newest start first; {@code effectiveTo} is exclusive and null while the post is open. */
record PrimaryPostHistoryResponse(List<PrimaryPostResponse> items) {
    static PrimaryPostHistoryResponse of(List<PrimaryPost> posts) {
        return new PrimaryPostHistoryResponse(posts.stream().map(p -> new PrimaryPostResponse(p.orgUnitId().value(), p.orgUnitName(),
                p.positionId() == null ? null : p.positionId().value(), p.positionName(), p.effectiveFrom(), p.effectiveTo(), p.current())).toList());
    }

    record PrimaryPostResponse(UUID orgUnitId, String orgUnitName, @Nullable UUID positionId, @Nullable String positionName,
                LocalDate effectiveFrom, @Nullable LocalDate effectiveTo, boolean current) {
    }
}
