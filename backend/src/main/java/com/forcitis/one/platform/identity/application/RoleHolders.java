package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RoleHolders implements RoleDirectory {

    private final AccountRepository accounts;
    private final OrgDirectory directory;
    private final AccountEmployees accountEmployees;

    RoleHolders(AccountRepository accounts, OrgDirectory directory, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.accounts = accounts;
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UserId> findRoleHolders(RoleCode roleCode) {
        return accounts.findEnabledHolders(roleCode).stream()
                .filter(userId -> directory.findEmployee(accountEmployees.employeeOf(userId))
                        .map(employee -> employee.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false))
                .collect(Collectors.toUnmodifiableSet());
    }
}
