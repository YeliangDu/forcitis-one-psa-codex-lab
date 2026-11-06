package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries.PrimaryAssignmentVersion;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A primary assignment over {@code [effectiveFrom, effectiveTo)}, open while {@code effectiveTo} is null; {@code current} on the business day. */
public record PrimaryAssignmentView(OrgUnitId orgUnitId, String orgUnitName, @Nullable PositionId positionId, @Nullable String positionName,
                                    LocalDate effectiveFrom, @Nullable LocalDate effectiveTo, boolean current) {
    static PrimaryAssignmentView of(PrimaryAssignmentVersion v) {
        return new PrimaryAssignmentView(v.orgUnitId(), v.orgUnitName(), v.positionId(), v.positionName(), v.effectiveFrom(), v.effectiveTo(), v.current());
    }
}
