package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import java.util.List;

/** What a merge would do on its day (the complete list, where a refusal message names at most twenty): the employed people it would move, and the people who join the merged unit later, who block it until HR has dealt with their commands, and those of the moved people whose separation is held, who stay until HR moves them out. */
public record OrgMergePreview(List<EmployeeId> employees, List<BlockingArrival> blocking, List<HeldSeparation> held) {

    public OrgMergePreview {
        employees = List.copyOf(employees);
        blocking = List.copyOf(blocking);
        held = List.copyOf(held);
    }
}
