package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.integration.api.SourcePerson;
import java.util.Optional;
import java.util.function.Predicate;

public final class EmployeeChecks {
    private EmployeeChecks() {
    }

    public static boolean hasLogin(String loginName) {
        return loginName != null && !loginName.isBlank();
    }

    public static EmploymentStatus employmentOf(SourcePerson person) {
        return hasLogin(person.loginName()) ? EmploymentStatus.EMPLOYED : EmploymentStatus.SEPARATED;
    }

    /** A person whose org unit or position is unknown is isolated: not written, reported, the rest of the batch continues. */
    public static Optional<SyncDiff> isolation(SourcePerson person, Predicate<OrgUnitId> orgUnitKnown,
                                               Predicate<PositionId> positionKnown) {
        String subject = "employee:" + person.personId().value();
        OrgUnitId orgUnitId = SourceTerms.unit(person.unitId());
        PositionId positionId = SourceTerms.position(person.positionId());
        if (!orgUnitKnown.test(orgUnitId)) {
            return Optional.of(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.ISOLATED_MISSING_ORG_UNIT,
                    "org unit " + orgUnitId.value() + " is not in the snapshot; row isolated"));
        }
        if (positionId == null || !positionKnown.test(positionId)) {
            return Optional.of(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.ISOLATED_MISSING_POSITION,
                    "position " + (positionId == null ? "(none)" : positionId.value()) + " is not in the snapshot; row isolated"));
        }
        return Optional.empty();
    }

    public static Employee toEmployee(SourcePerson person, boolean keepWecomUserId) {
        String login = hasLogin(person.loginName()) ? person.loginName().trim() : null;
        return new Employee(SourceTerms.person(person.personId()), person.employeeCode(), person.displayName(), login, person.email(),
                SourceTerms.gender(person.gender()), keepWecomUserId ? person.wecomUserId() : null, employmentOf(person), person.feeEarner());
    }
}
