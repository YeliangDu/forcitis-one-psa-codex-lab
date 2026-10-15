package com.forcitis.one.platform.approval.infrastructure.replay;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import java.util.Optional;
import java.util.UUID;

/** The export names a person by one number; the offline replay gives each person an account id made from that number, and back. */
final class ExportedAccounts implements AccountEmployees {

    static UserId account(long person) {
        return new UserId(new UUID(0L, person));
    }

    static long person(UserId account) {
        if (account.value().getMostSignificantBits() != 0L) {
            throw new IllegalArgumentException("account " + account.value() + " was not made by the replay");
        }
        return account.value().getLeastSignificantBits();
    }

    @Override
    public Optional<EmployeeId> employeeOf(UserId account) {
        return Optional.of(ExportedIds.employee(person(account)));
    }

    @Override
    public Optional<UserId> accountOf(EmployeeId employee) {
        return Optional.of(account(ExportedIds.person(employee)));
    }
}
