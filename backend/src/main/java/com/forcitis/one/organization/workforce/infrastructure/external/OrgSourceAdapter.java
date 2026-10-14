package com.forcitis.one.organization.workforce.infrastructure.external;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.Gender;
import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.domain.PositionId;
import com.forcitis.one.organization.workforce.domain.PositionStatus;
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.organization.workforce.domain.RelationshipKind;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceOrgUnit;
import com.forcitis.one.organization.workforce.domain.SourcePosition;
import com.forcitis.one.organization.workforce.domain.SourceRelationship;
import com.forcitis.one.organization.workforce.domain.SourceSnapshot;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import com.forcitis.one.platform.integration.api.SourceGender;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourcePersonId;
import com.forcitis.one.platform.integration.api.SourcePositionId;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRegionKind;
import com.forcitis.one.platform.integration.api.SourceUnitId;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class OrgSourceAdapter implements OrgSource {

    private final com.forcitis.one.platform.integration.api.OrgSource integration;

    OrgSourceAdapter(com.forcitis.one.platform.integration.api.OrgSource integration) {
        this.integration = integration;
    }

    @Override
    public SourceSnapshot fetchAll() {
        SourceOrgSnapshot snapshot;
        try {
            snapshot = integration.readSnapshot();
        } catch (ExternalSystemUnavailableException unavailable) {
            throw new SourceUnavailableException(unavailable.getMessage(), unavailable);
        }
        return new SourceSnapshot(
                snapshot.units().stream().map(OrgSourceAdapter::unit).toList(),
                snapshot.relationships().stream().map(r -> new SourceRelationship(
                        r.kind() == com.forcitis.one.platform.integration.api.SourceRelationshipKind.UNIT_HEAD
                                ? RelationshipKind.HEAD : RelationshipKind.REGIONAL_RECEIVABLES_MANAGER,
                        unitId(r.unitId()), userId(r.personId()), r.personDeleted())).toList(),
                snapshot.positions().stream().map(p -> new SourcePosition(positionId(p.positionId()), p.code(), p.name(),
                        p.sortOrder(), positionStatus(p.state()))).toList(),
                snapshot.staff().stream().map(OrgSourceAdapter::member).toList());
    }

    @Override
    public List<SourceMember> fetchMembersByWecomUserId(WecomUserId wecomUserId) {
        try {
            return integration.findStaffByWecomUserId(wecomUserId).stream().map(OrgSourceAdapter::member).toList();
        } catch (ExternalSystemUnavailableException unavailable) {
            throw new SourceUnavailableException(unavailable.getMessage(), unavailable);
        }
    }

    @Override
    public List<SourceMember> fetchMembersByLoginName(String loginName) {
        try {
            return integration.findStaffByLoginName(loginName).stream().map(OrgSourceAdapter::member).toList();
        } catch (ExternalSystemUnavailableException unavailable) {
            throw new SourceUnavailableException(unavailable.getMessage(), unavailable);
        }
    }

    private static SourceOrgUnit unit(com.forcitis.one.platform.integration.api.SourceOrgUnit u) {
        return new SourceOrgUnit(unitId(u.unitId()), u.code(), u.name(), u.parentUnitId() == null ? null : unitId(u.parentUnitId()),
                u.sortOrder(), u.state() == SourceRecordState.ACTIVE ? OrgUnitStatus.ACTIVE : OrgUnitStatus.DISABLED,
                u.emptyFlags(), region(u.regionKind()), u.rawRegion(), u.deliveryUnit(),
                u.duplicateOfUnitId() == null ? null : unitId(u.duplicateOfUnitId()));
    }

    private static SourceMember member(SourcePerson p) {
        return new SourceMember(userId(p.personId()), p.employeeCode(), p.displayName(), unitId(p.unitId()),
                p.positionId() == null ? null : positionId(p.positionId()), p.supervisorId() == null ? null : userId(p.supervisorId()),
                p.loginName(), p.email(), gender(p.gender()), p.wecomUserId(), p.feeEarner());
    }

    private static OrgUnitId unitId(SourceUnitId id) {
        return new OrgUnitId(id.value());
    }

    private static PositionId positionId(SourcePositionId id) {
        return new PositionId(id.value());
    }

    private static UserId userId(SourcePersonId id) {
        return new UserId(id.value());
    }

    private static PositionStatus positionStatus(SourceRecordState state) {
        return switch (state) {
            case ACTIVE -> PositionStatus.ACTIVE;
            case DISABLED -> PositionStatus.DISABLED;
            case DELETED -> PositionStatus.DELETED;
        };
    }

    private static RegionCategory region(SourceRegionKind kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case HEADQUARTERS -> RegionCategory.HEADQUARTERS;
            case REGIONAL -> RegionCategory.REGIONAL;
        };
    }

    private static Gender gender(SourceGender gender) {
        if (gender == null) {
            return null;
        }
        return switch (gender) {
            case MALE -> Gender.MALE;
            case FEMALE -> Gender.FEMALE;
        };
    }
}
