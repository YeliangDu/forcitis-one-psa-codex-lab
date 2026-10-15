package com.forcitis.one.fixture.doubles;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;

/** The employee of an account and the account of an employee, with the two numbers apart by a fixed distance (0 keeps them equal). */
public final class TestAccounts implements AccountEmployees {

    private final long distance;

    private TestAccounts(long distance) {
        this.distance = distance;
    }

    public static TestAccounts sameNumber() {
        return new TestAccounts(0);
    }

    /** The account of employee n is n + distance, so a comparison on the wrong side finds nobody equal. */
    public static TestAccounts apart(long distance) {
        if (distance == 0) {
            throw new IllegalArgumentException("a distance of 0 does not keep the two ids apart");
        }
        return new TestAccounts(distance);
    }

    @Override
    public EmployeeId employeeOf(UserId account) {
        return new EmployeeId(account.value() - distance);
    }

    @Override
    public UserId accountOf(EmployeeId employee) {
        return new UserId(employee.value() + distance);
    }
}
