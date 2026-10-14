package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The per-person rules shared by the bulk sync and the single-person lookup at login (REIMAGINED_ARCHITECTURE
 * §3.1 / §3.3: "校验规则与同步相同").
 */
public final class MemberChecks {

    private MemberChecks() {
    }

    /** One normalised "has a login name" test (RULE-363): null, empty and blank all mean no. */
    public static boolean hasLogin(String loginName) {
        return loginName != null && !loginName.isBlank();
    }

    public static EmploymentStatus employmentOf(SourceMember member) {
        return hasLogin(member.loginName()) ? EmploymentStatus.EMPLOYED : EmploymentStatus.SEPARATED;
    }

    /**
     * A person whose org unit or position is unknown is isolated: not written, reported, and the rest of the batch
     * continues (RULE-365). Empty means the row is fine.
     */
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

    /** The stored employee for a person that passed the checks. */
    public static Employee toEmployee(SourceMember member, boolean keepWecomUserId) {
        String login = hasLogin(member.loginName()) ? member.loginName().trim() : null;
        return new Employee(member.userId(), member.employeeCode(), member.displayName(), login, member.email(),
                member.gender(), keepWecomUserId ? member.wecomUserId() : null, employmentOf(member), member.feeEarner());
    }
}
