package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries.ActingDutyRow;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves covered names through employee-detail visibility, without treating a hidden name as a denied subject read. */
@Component
class ActingDutyViews {
    private final EmployeeRosterQueries queries;
    private final EmployeeVisibility visibility;
    private final Clock clock;
    private final BusinessZone zone;

    ActingDutyViews(EmployeeRosterQueries queries, EmployeeVisibility visibility, Clock clock, BusinessZone zone) {
        this.queries = queries;
        this.visibility = visibility;
        this.clock = clock;
        this.zone = zone;
    }

    List<ActingDutyView> of(List<ActingDutyRow> rows) {
        LocalDate today = zone.today(clock);
        var visible = visibility.forDetail();
        return rows.stream().map(row -> ActingDutyView.of(row, row.coveredEmployeeId() == null ? null
                : queries.find(visible, row.coveredEmployeeId()).map(EmployeeRosterQueries.Row::displayName).orElse(null), today)).toList();
    }
}
