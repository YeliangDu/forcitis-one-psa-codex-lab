package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import org.springframework.stereotype.Component;

@Component
class AccountEmployeeMapping implements AccountEmployees {

    @Override
    public EmployeeId employeeOf(UserId account) {
        return new EmployeeId(account.value());
    }

    @Override
    public UserId accountOf(EmployeeId employee) {
        return new UserId(employee.value());
    }
}
