package com.forcitis.one.fixture.doubles;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Org unit n and position n of a test as own UUIDs (R-1b P3b), readable in SQL and in a failure message: 01900000-0000-7000-c000-n for
 * units, …-d000-n for positions (n zero-padded, 1 to 12 digits); any other n gets a name-based UUID. Employees are {@link TestAccounts#employee(long)}.
 */
public final class TestOrgIds {

    private static final String ORG_UNIT = "01900000-0000-7000-c000-";
    private static final String POSITION = "01900000-0000-7000-d000-";
    private static final long MAX_READABLE = 999_999_999_999L;

    private TestOrgIds() {
    }

    public static OrgUnitId orgUnit(long n) {
        return new OrgUnitId(uuid(ORG_UNIT, "test-org-unit:", n));
    }

    public static PositionId position(long n) {
        return new PositionId(uuid(POSITION, "test-position:", n));
    }

    /** The own id of an object the source names: test employee, org unit or position n as platform.integration hands it over. */
    public static OwnObjectId ownEmployee(long n) {
        return new OwnObjectId(TestAccounts.employee(n).value());
    }

    public static OwnObjectId ownOrgUnit(long n) {
        return new OwnObjectId(orgUnit(n).value());
    }

    public static OwnObjectId ownPosition(long n) {
        return new OwnObjectId(position(n).value());
    }

    /** SQL text of org unit n, for statements written in a test. */
    public static String sqlOrgUnit(long n) {
        return "'" + orgUnit(n).value() + "'";
    }

    public static String sqlPosition(long n) {
        return "'" + position(n).value() + "'";
    }

    /** SQL text of test employee n. */
    public static String sqlEmployee(long n) {
        return "'" + TestAccounts.employee(n).value() + "'";
    }

    private static UUID uuid(String prefix, String name, long n) {
        if (n <= 0 || n > MAX_READABLE) {
            return UUID.nameUUIDFromBytes((name + n).getBytes(StandardCharsets.UTF_8));
        }
        return UUID.fromString(prefix + String.format("%012d", n));
    }
}
