package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import java.util.List;

/** What a merge would do on its day (the complete list, where a refusal message names at most twenty): the employed people it would move (anyone with a post in the unit, primary or concurrent, once) with what each holds there, the people who join the merged unit later, who block it until HR has dealt with their commands, and the people in the unit whose separation is held: the merge moves those employed on its day, but one a due-day hold puts back after the merge started stays until HR transfers them out. */
public record OrgMergePreview(List<EmployeeId> employees, List<BlockingArrival> blocking, List<MergedPosts> posts, List<HeldSeparation> held) {

    public OrgMergePreview {
        employees = List.copyOf(employees);
        blocking = List.copyOf(blocking);
        posts = List.copyOf(posts);
        held = List.copyOf(held);
    }
}
