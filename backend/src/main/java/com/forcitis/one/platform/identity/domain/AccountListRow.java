package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.List;

/** One account of the account list: when it was opened, its employee, its status and the codes of the roles it holds now, sorted. */
public record AccountListRow(UserId userId, Instant createdAt, EmployeeId employeeId, AccountStatus status, List<String> roleCodes) {
    public AccountListRow {
        roleCodes = List.copyOf(roleCodes);
    }
}
