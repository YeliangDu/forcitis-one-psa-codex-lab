package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.integration.api.SourceGender;
import com.forcitis.one.platform.integration.api.SourcePersonId;
import com.forcitis.one.platform.integration.api.SourcePositionId;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRegionKind;
import com.forcitis.one.platform.integration.api.SourceUnitId;

/** The source speaks in its own ids and enums; the workforce model keeps its own. Absent stays absent. */
public final class SourceTerms {
    private SourceTerms() {
    }

    public static OrgUnitId unit(SourceUnitId id) {
        return id == null ? null : new OrgUnitId(id.value());
    }

    public static EmployeeId person(SourcePersonId id) {
        return id == null ? null : new EmployeeId(id.value());
    }

    public static PositionId position(SourcePositionId id) {
        return id == null ? null : new PositionId(id.value());
    }

    public static OrgUnitStatus unitStatus(SourceRecordState state) {
        return state == SourceRecordState.ACTIVE ? OrgUnitStatus.ACTIVE : OrgUnitStatus.DISABLED;
    }

    public static PositionStatus positionStatus(SourceRecordState state) {
        return switch (state) {
            case ACTIVE -> PositionStatus.ACTIVE;
            case DISABLED -> PositionStatus.DISABLED;
            case DELETED -> PositionStatus.DELETED;
        };
    }

    public static RegionCategory region(SourceRegionKind kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case HEADQUARTERS -> RegionCategory.HEADQUARTERS;
            case REGIONAL -> RegionCategory.REGIONAL;
        };
    }

    public static Gender gender(SourceGender gender) {
        if (gender == null) {
            return null;
        }
        return switch (gender) {
            case MALE -> Gender.MALE;
            case FEMALE -> Gender.FEMALE;
        };
    }
}
