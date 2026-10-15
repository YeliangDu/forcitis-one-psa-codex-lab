package com.forcitis.one.fixture.typedid;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import java.util.Objects;

/** Comparisons that take the raw value out of a typed id (the first six) and those that do not (the rest). */
public final class RawValueComparisons {

    private RawValueComparisons() {
    }

    public static boolean twoRawLongs(UserId user, EmployeeId employee) {
        return user.value() == employee.value();
    }

    public static boolean twoRawLongsAsNotEqual(UserId user, EmployeeId employee) {
        if (user.value() != employee.value()) {
            return false;
        }
        return true;
    }

    public static boolean boxedThroughObjects(UserId user, EmployeeId employee) {
        return Objects.equals(user.value(), employee.value());
    }

    public static boolean uuidValuesByEquals(LegalEntityId first, LegalEntityId second) {
        return first.value().equals(second.value());
    }

    public static boolean plainLongAgainstTheId(long subjectKey, UserId actor) {
        return subjectKey == actor.value();
    }

    public static boolean theIdAgainstAPlainLong(UserId actor, long subjectKey) {
        return actor.value() == subjectKey;
    }

    public static boolean typedIdsCompared(UserId first, UserId second) {
        return first.equals(second);
    }

    public static boolean againstAConstant(UserId user) {
        return user.value() == 0 || user.value() != -1;
    }

    public static boolean ordering(UserId first, UserId second) {
        return first.value() < second.value();
    }

    public static boolean rawValuesOfNoId(String first, String second) {
        return first.length() == second.length() && Objects.equals(first, second);
    }

    public static String rawValueAsText(UserId user) {
        return "user " + user.value();
    }
}
