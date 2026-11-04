package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import java.util.Map;
import java.util.Set;

public interface EmployeeLabels {
    /**
     * Reads display name and employee code solely from the employees row, without versions or assignments.
     * Ids without an employees row are absent from the map. Callers pass at most one page of ids (at most 200)
     * per call. An empty set returns an empty map.
     */
    Map<EmployeeId, EmployeeLabel> labelsOf(Set<EmployeeId> employeeIds);
}
