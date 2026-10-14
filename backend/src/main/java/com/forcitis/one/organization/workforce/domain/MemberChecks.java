package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Optional;
import java.util.function.Predicate;

public final class MemberChecks {
    private MemberChecks() {
    }

    public static boolean hasLogin(String loginName) {
        return loginName != null && !loginName.isBlank();
    }

    public static EmploymentStatus employmentOf(SourceMember member) {
        return hasLogin(member.loginName()) ? EmploymentStatus.EMPLOYED : EmploymentStatus.SEPARATED;
    }

    /** A person whose org unit or position is unknown is isolated: not written, reported, the rest of the batch continues. */
    public static Optional<SyncDiff> isolation(SourceMember member, Predicate<OrgUnitId> orgUnitKnown,
                                               Predicate<PositionId> positionKnown) {
        String subject = "employee:" + member.userId().value();
        if (!orgUnitKnown.test(member.orgUnitId())) {
            return Optional.of(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.ISOLATED_MISSING_ORG_UNIT,
                    "org unit " + member.orgUnitId().value() + " is not in the snapshot; row isolated"));
        }
        if (member.positionId() == null || !positionKnown.test(member.positionId())) {
            return Optional.of(SyncDiff.issue(EntityKind.EMPLOYEE, subject, IssueKind.ISOLATED_MISSING_POSITION,
                    "position " + (member.positionId() == null ? "(none)" : member.positionId().value())
                            + " is not in the snapshot; row isolated"));
        }
        return Optional.empty();
    }

    public static Employee toEmployee(SourceMember member, boolean keepWecomUserId) {
        String login = hasLogin(member.loginName()) ? member.loginName().trim() : null;
        return new Employee(member.userId(), member.employeeCode(), member.displayName(), login, member.email(),
                member.gender(), keepWecomUserId ? member.wecomUserId() : null, employmentOf(member), member.feeEarner());
    }
}
