package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceGender;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourcePosition;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRelationship;
import com.forcitis.one.platform.integration.api.SourceRelationshipKind;
import java.util.ArrayList;
import java.util.List;

public final class Fixtures {
    private Fixtures() {
    }

    public static OrgUnitId unitId(long id) {
        return TestOrgIds.orgUnit(id);
    }

    public static EmployeeId user(long id) {
        return TestAccounts.employee(id);
    }

    public static OwnObjectId sourceUnit(long id) {
        return TestOrgIds.ownOrgUnit(id);
    }

    public static OwnObjectId sourcePerson(long id) {
        return TestOrgIds.ownEmployee(id);
    }

    public static OwnObjectId sourcePosition(long id) {
        return TestOrgIds.ownPosition(id);
    }

    public static SourceOrgUnit unit(long id, Long parent) {
        return unit(id, parent, SourceRecordState.ACTIVE);
    }

    public static SourceOrgUnit unit(long id, Long parent, SourceRecordState state) {
        return new SourceOrgUnit(sourceUnit(id), "C" + id, "Unit " + id, parent == null ? null : sourceUnit(parent), (int) id, state,
                List.of(), null, null, false, null);
    }

    public static SourcePosition post(long id) {
        return post(id, SourceRecordState.ACTIVE);
    }

    public static SourcePosition post(long id, SourceRecordState state) {
        return new SourcePosition(sourcePosition(id), "P" + id, "Post " + id, (int) id, state);
    }

    public static SourcePerson person(long id, long unit, Long post, Long leader) {
        return person(id, unit, post, leader, "139" + id, "wx-" + id);
    }

    public static SourcePerson person(long id, long unit, Long post, Long leader, String login, String wecom) {
        return new SourcePerson(sourcePerson(id), "E" + id, "Person " + id, sourceUnit(unit),
                post == null ? null : sourcePosition(post), leader == null ? null : sourcePerson(leader), login,
                "p" + id + "@example.test", SourceGender.MALE, wecom == null ? null : new WecomUserId(wecom), false);
    }

    public static SourceRelationship head(long unit, long person) {
        return new SourceRelationship(SourceRelationshipKind.UNIT_HEAD, sourceUnit(unit), sourcePerson(person), false);
    }

    public static SourceRelationship manager(long unit, long person) {
        return new SourceRelationship(SourceRelationshipKind.REGIONAL_RECEIVABLES_MANAGER, sourceUnit(unit), sourcePerson(person), false);
    }

    public static SourceOrgSnapshot source(List<SourceOrgUnit> units, List<SourceRelationship> relationships,
                                 List<SourcePosition> positions, List<SourcePerson> staff) {
        return new SourceOrgSnapshot(units, relationships, positions, staff);
    }

    public static SourceOrgSnapshot tree(SourcePerson... staff) {
        return source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L)), List.of(),
                List.of(post(100), post(101)), List.of(staff));
    }
}
