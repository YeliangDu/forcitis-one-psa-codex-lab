package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import java.util.List;

/** What a merge would do on its day (the complete list, where a refusal message names at most twenty): the employed people it would move, and the people who join the merged unit later, who block it until HR has dealt with their commands. */
public record OrgMergePreview(List<EmployeeId> employees, List<BlockingArrival> blocking) {

    public OrgMergePreview {
        employees = List.copyOf(employees);
        blocking = List.copyOf(blocking);
    }
}
