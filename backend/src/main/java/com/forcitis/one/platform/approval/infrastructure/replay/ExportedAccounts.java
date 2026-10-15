package com.forcitis.one.platform.approval.infrastructure.replay;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;

/** The export names a person by one number, which is both the employee and the account. */
final class ExportedAccounts implements AccountEmployees {

    @Override
    public EmployeeId employeeOf(UserId account) {
        return new EmployeeId(account.value());
    }

    @Override
    public UserId accountOf(EmployeeId employee) {
        return new UserId(employee.value());
    }
}
