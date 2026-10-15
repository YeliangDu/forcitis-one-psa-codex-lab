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
 * The employee of an account and the account of an employee. The account of employee n is the UUID 01900000-0000-7000-8000-n (n in the
 * last group, decimal, zero-padded), so the two ids never look alike, a comparison on the wrong side finds nobody equal and SQL in a test
 * can name the account (a number that does not fit, such as a negative source id, gets a name-based UUID); employees listed as without an
 * account have none.
 */
public final class TestAccounts implements AccountEmployees {

    private static final String ACCOUNT = "01900000-0000-7000-8000-";
    private static final String UNKNOWN = "01900000-0000-7000-9000-";
    private static final long MAX_READABLE = 999_999_999_999L;
    private static final Map<UserId, Long> OTHER_NUMBERS = new ConcurrentHashMap<>();

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

    /** The account id the double gives employee n. */
    public static UserId account(long employee) {
        if (employee <= 0 || employee > MAX_READABLE) {
            UserId other = new UserId(UUID.nameUUIDFromBytes(("test-account:" + employee).getBytes(StandardCharsets.UTF_8)));
            OTHER_NUMBERS.put(other, employee);
            return other;
        }
        return new UserId(UUID.fromString(ACCOUNT + digits(employee)));
    }

    /** The account of employee n as SQL text, for statements written in a test. */
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
        Long other = OTHER_NUMBERS.get(account);
        if (!text.startsWith(ACCOUNT) && other == null) {
            return Optional.empty();
        }
        EmployeeId employee = new EmployeeId(other != null ? other : Long.parseLong(text.substring(ACCOUNT.length())));
        return withoutAccount.contains(employee) ? Optional.empty() : Optional.of(employee);
    }

    @Override
    public Optional<UserId> accountOf(EmployeeId employee) {
        return withoutAccount.contains(employee) ? Optional.empty() : Optional.of(account(employee.value()));
    }
}
