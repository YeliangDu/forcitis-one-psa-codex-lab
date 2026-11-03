package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;

/**
 * Who may read and who may handle which item (F-1 card invariants 19, 22 and 33; one-design ruling #730 comment 6602368110). A holder of the
 * read code reads every item except those about themselves; anyone reads the items addressed to them. A scope review addressed to a person is
 * handled by that person only; every other item by a holder of the update code. The list query in the repository states the read rule in SQL.
 */
public record PermissionAdjustmentReader(UserId user, EmployeeId employee, boolean holdsRead, boolean holdsUpdate) {

    public boolean isSubject(PermissionAdjustment item) {
        return user.equals(item.user());
    }

    public boolean canRead(PermissionAdjustment item) {
        return employee.equals(item.recipient()) || (holdsRead && !isSubject(item));
    }

    public boolean canHandle(PermissionAdjustment item) {
        boolean addressed = item.kind() == PermissionAdjustmentKind.SCOPE_REVIEW && item.origin() == PermissionAdjustmentOrigin.ASSIGNMENT_CHANGE
                && item.recipient() != null;
        return addressed ? employee.equals(item.recipient()) : holdsUpdate;
    }
}
