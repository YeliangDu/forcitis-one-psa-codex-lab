package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class AccountEmployeeMapping implements AccountEmployees {

    private final AccountRepository accounts;

    AccountEmployeeMapping(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<EmployeeId> employeeOf(UserId account) {
        return accounts.findEmployee(account);
    }

    @Override
    public Optional<UserId> accountOf(EmployeeId employee) {
        return accounts.findByEmployee(employee);
    }
}
