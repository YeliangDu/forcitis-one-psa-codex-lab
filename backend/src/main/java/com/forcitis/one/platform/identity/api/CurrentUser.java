package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.CallerAccount;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;

/** Never taken from request bodies or headers. userId is the account; employeeId is the person behind it, for what belongs to a person. */
public interface CurrentUser extends CallerAccount {

    @Override
    UserId userId();

    EmployeeId employeeId();

    LegalEntityId legalEntityId();
}
