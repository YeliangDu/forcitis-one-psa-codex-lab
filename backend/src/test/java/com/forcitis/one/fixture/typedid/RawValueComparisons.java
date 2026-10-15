package com.forcitis.one.fixture.typedid;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import java.util.Objects;

/** Comparisons (between long-backed ids, the source object id being the one left after R-1b P3b) that take the raw value out of a typed id (the first six) and those that do not (the rest). */
public final class RawValueComparisons {

    private RawValueComparisons() {
    }

    public static boolean twoRawLongs(SourceObjectId user, SourceObjectId employee) {
        return user.value() == employee.value();
    }

    public static boolean twoRawLongsAsNotEqual(SourceObjectId user, SourceObjectId employee) {
        if (user.value() != employee.value()) {
            return false;
        }
        return true;
    }

    public static boolean boxedThroughObjects(SourceObjectId user, SourceObjectId employee) {
        return Objects.equals(user.value(), employee.value());
    }

    public static boolean uuidValuesByEquals(LegalEntityId first, LegalEntityId second) {
        return first.value().equals(second.value());
    }

    public static boolean plainLongAgainstTheId(long subjectKey, SourceObjectId actor) {
        return subjectKey == actor.value();
    }

    public static boolean theIdAgainstAPlainLong(SourceObjectId actor, long subjectKey) {
        return actor.value() == subjectKey;
    }

    public static boolean typedIdsCompared(OrgUnitId first, OrgUnitId second) {
        return first.equals(second);
    }

    public static boolean againstAConstant(SourceObjectId user) {
        return user.value() == 0 || user.value() != -1;
    }

    public static boolean ordering(SourceObjectId first, SourceObjectId second) {
        return first.value() < second.value();
    }

    public static boolean rawValuesOfNoId(String first, String second) {
        return first.length() == second.length() && Objects.equals(first, second);
    }

    public static String rawValueAsText(SourceObjectId user) {
        return "user " + user.value();
    }
}
