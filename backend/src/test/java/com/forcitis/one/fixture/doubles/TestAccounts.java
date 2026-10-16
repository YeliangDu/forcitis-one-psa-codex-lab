package com.forcitis.one.fixture.doubles;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The employee of an account and the account of an employee. The account of test employee n is the UUID 01900000-0000-7000-8000-n (n in
 * the last group, decimal, zero-padded), so SQL in a test can name the account; employees listed as without an account have none.
 * <p>
 * R-1b P3b (RULING-P3B-1): test employee n is {@link #employee(long)}, a UUID of its own (01900000-0000-7000-b000-n). Any other employee
 * id gets an account id derived from it that never equals it, so a comparison on the wrong side finds nobody equal; a number that does
 * not fit (a negative source id, say) gets a name-based UUID.
 */
public final class TestAccounts implements AccountEmployees {

    private static final String ACCOUNT = "01900000-0000-7000-8000-";
    private static final String UNKNOWN = "01900000-0000-7000-9000-";
    private static final String EMPLOYEE = "01900000-0000-7000-b000-";
    private static final long MAX_READABLE = 999_999_999_999L;
    private static final long HIGH_MASK = 0x0F0F_0000_0000_0000L;
    private static final long LOW_MASK = 0x00F0_0000_0000_0F0FL;
    private static final Map<UserId, EmployeeId> OTHERS = new ConcurrentHashMap<>();

    private final Set<EmployeeId> withoutAccount;

    private TestAccounts(Set<EmployeeId> withoutAccount) {
        this.withoutAccount = Set.copyOf(withoutAccount);
    }

    public static TestAccounts everyoneHasOne() {
        return new TestAccounts(Set.of());
    }

    public static TestAccounts except(EmployeeId... withoutAccount) {
        return new TestAccounts(Set.of(withoutAccount));
    }

    /** Test employee n as a UUID made from it. */
    public static EmployeeId employee(long n) {
        if (n <= 0 || n > MAX_READABLE) {
            return new EmployeeId(UUID.nameUUIDFromBytes(("test-employee:" + n).getBytes(StandardCharsets.UTF_8)));
        }
        return new EmployeeId(UUID.fromString(EMPLOYEE + digits(n)));
    }

    /** The account id the double gives test employee n. */
    public static UserId account(long employee) {
        if (employee <= 0 || employee > MAX_READABLE) {
            UserId other = new UserId(UUID.nameUUIDFromBytes(("test-account:" + employee).getBytes(StandardCharsets.UTF_8)));
            OTHERS.put(other, employee(employee));
            return other;
        }
        return new UserId(UUID.fromString(ACCOUNT + digits(employee)));
    }

    /** The account id the double gives an employee: that of test employee n, or one derived from the employee's UUID that never equals it. */
    public static UserId account(EmployeeId employee) {
        UUID id = employee.value();
        String text = id.toString();
        if (text.startsWith(EMPLOYEE)) {
            return account(Long.parseLong(text.substring(EMPLOYEE.length())));
        }
        UserId derived = new UserId(new UUID(id.getMostSignificantBits() ^ HIGH_MASK, id.getLeastSignificantBits() ^ LOW_MASK));
        OTHERS.put(derived, employee);
        return derived;
    }

    /** The account of test employee n as SQL text, for statements written in a test. */
    public static String sql(long employee) {
        return "'" + account(employee).value() + "'";
    }

    /** An account id that carries n but belongs to no employee: what a comparison of raw numbers would wrongly match. */
    public static UserId unknownAccount(long n) {
        return new UserId(UUID.fromString(UNKNOWN + digits(n)));
    }

    private static String digits(long n) {
        if (n <= 0 || n > MAX_READABLE) {
            throw new IllegalArgumentException("a test employee number is 1 to 12 digits: " + n);
        }
        return String.format("%012d", n);
    }

    @Override
    public Optional<EmployeeId> employeeOf(UserId account) {
        String text = account.value().toString();
        EmployeeId employee;
        if (text.startsWith(ACCOUNT)) {
            employee = employee(Long.parseLong(text.substring(ACCOUNT.length())));
        } else {
            employee = OTHERS.get(account);
        }
        if (employee == null) {
            return Optional.empty();
        }
        return withoutAccount.contains(employee) ? Optional.empty() : Optional.of(employee);
    }

    @Override
    public Optional<UserId> accountOf(EmployeeId employee) {
        return withoutAccount.contains(employee) ? Optional.empty() : Optional.of(account(employee));
    }
}
