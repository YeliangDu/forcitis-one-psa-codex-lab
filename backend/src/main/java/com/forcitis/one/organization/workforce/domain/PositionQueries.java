package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Company reference data, read from the non-superseded version in force today. */
public interface PositionQueries {
    /** {@code status} is the name of a {@link PositionStatus}, or null for every position. */
    List<Row> list(@Nullable String status, @Nullable PositionId after, int limit);

    Optional<Row> find(PositionId position);

    record Row(PositionId positionId, @Nullable String code, String name, PositionStatus status, LocalDate effectiveFromDate,
               @Nullable LocalDate effectiveToDate) {
    }
}
