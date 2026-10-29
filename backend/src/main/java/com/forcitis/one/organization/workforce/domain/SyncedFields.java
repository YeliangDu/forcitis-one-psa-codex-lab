package com.forcitis.one.organization.workforce.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Single list of synced fields: decides what is written, diffed and when the event is published; shortName and bookkeeping are not in it. */
public final class SyncedFields {
    public record Field<T>(String name, Function<T, @Nullable Object> reader) {
    }

    public static final List<Field<OrgUnit>> ORG_UNIT = List.of(
            new Field<>("code", OrgUnit::code),
            new Field<>("name", OrgUnit::name),
            new Field<>("parentOrgUnitId", OrgUnit::parentOrgUnitId),
            new Field<>("sortOrder", OrgUnit::sortOrder),
            new Field<>("status", OrgUnit::status),
            new Field<>("headEmployeeId", OrgUnit::headEmployeeId),
            new Field<>("regionalReceivablesManagerId", OrgUnit::regionalReceivablesManagerId),
            new Field<>("regionCategory", OrgUnit::regionCategory),
            new Field<>("deliveryUnit", OrgUnit::deliveryUnit));

    public static final List<Field<StaffEmployee>> STAFF = List.of(
            new Field<>("employeeCode", s -> s.employee().employeeCode()),
            new Field<>("displayName", s -> s.employee().displayName()),
            new Field<>("loginName", s -> s.employee().loginName()),
            new Field<>("email", s -> s.employee().email()),
            new Field<>("gender", s -> s.employee().gender()),
            new Field<>("wecomUserId", s -> s.employee().wecomUserId()),
            new Field<>("employmentStatus", s -> s.employee().employmentStatus()),
            new Field<>("feeEarner", s -> s.employee().feeEarner()),
            new Field<>("orgUnitId", s -> s.assignment().orgUnitId()),
            new Field<>("positionId", s -> s.assignment().positionId()),
            new Field<>("reportsToEmployeeId", s -> s.assignment().reportsToEmployeeId()));

    public static final List<Field<Position>> POSITION = List.of(
            new Field<>("code", Position::code),
            new Field<>("name", Position::name),
            new Field<>("sortOrder", Position::sortOrder),
            new Field<>("status", Position::status));

    private SyncedFields() {
    }

    public static <T> List<String> differences(List<Field<T>> fields, T before, T after) {
        List<String> changed = new ArrayList<>();
        for (Field<T> field : fields) {
            if (!Objects.equals(field.reader().apply(before), field.reader().apply(after))) {
                changed.add(field.name());
            }
        }
        return changed;
    }
}
