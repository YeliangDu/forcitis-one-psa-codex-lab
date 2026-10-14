package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.MemberChecks;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import com.forcitis.one.organization.workforce.domain.StaffMember;
import com.forcitis.one.organization.workforce.domain.SyncDiff;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Login miss (a new hire not synced yet): ask the same A8 view for this one person, apply the same checks as the
 * sync (scope, org unit and position known, one normalised login test, the WeCom id identifies one person) and, if
 * everything holds, register the person and audit it (REIMAGINED_ARCHITECTURE §3.1, review #14). Anything doubtful
 * answers "unknown": login fails closed and the next sync decides.
 */
@Service
public class RegisterEmployeeOnLogin {

    private static final Logger LOG = LoggerFactory.getLogger(RegisterEmployeeOnLogin.class);

    private final OrgSource source;
    private final OrgSnapshotRepository snapshots;
    private final PersistRegisteredEmployee persist;

    RegisterEmployeeOnLogin(OrgSource source, OrgSnapshotRepository snapshots, PersistRegisteredEmployee persist) {
        this.source = source;
        this.snapshots = snapshots;
        this.persist = persist;
    }

    public Optional<StaffMember> execute(WecomUserId wecomUserId) {
        List<SourceMember> found;
        try {
            found = source.fetchMembersByWecomUserId(wecomUserId);
        } catch (SourceUnavailableException unavailable) {
            LOG.warn("organization.workforce login-miss lookup skipped, A8 not available: {}", unavailable.getMessage());
            return Optional.empty();
        }
        Optional<SourceMember> chosen = choose(found);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        SourceMember member = chosen.get();
        var orgUnits = snapshots.findOrgUnitIds();
        var positions = snapshots.findPositionIds();
        Optional<SyncDiff> isolation = MemberChecks.isolation(member, orgUnits::contains, positions::contains);
        if (isolation.isPresent()) {
            LOG.warn("organization.workforce login-miss person {} not registered: {}", member.userId().value(),
                    isolation.get().detail());
            return Optional.empty();
        }
        StaffMember staff = new StaffMember(MemberChecks.toEmployee(member, true),
                new Assignment(member.userId(), member.orgUnitId(), member.positionId(), validLeader(member)));
        persist.execute(staff);
        return Optional.of(staff);
    }

    /** Several rows for one WeCom id are ambiguous unless exactly one of them is employed. */
    private static Optional<SourceMember> choose(List<SourceMember> found) {
        if (found.isEmpty()) {
            return Optional.empty();
        }
        List<SourceMember> employed = found.stream().filter(m -> MemberChecks.employmentOf(m) == EmploymentStatus.EMPLOYED).toList();
        if (employed.size() == 1) {
            return Optional.of(employed.getFirst());
        }
        if (employed.isEmpty() && found.size() == 1) {
            return Optional.of(found.getFirst());
        }
        LOG.warn("organization.workforce login-miss lookup is ambiguous ({} rows); the WeCom user id is not trusted", found.size());
        return Optional.empty();
    }

    /** Same leader rule as the sync (RULE-372): exists, employed, not the person. A new person cannot close a loop. */
    private UserId validLeader(SourceMember member) {
        UserId leader = member.leaderId();
        if (leader == null || leader.equals(member.userId())) {
            return null;
        }
        return snapshots.findStaff(leader)
                .filter(s -> s.employee().employmentStatus() == EmploymentStatus.EMPLOYED)
                .map(StaffMember::userId)
                .orElse(null);
    }
}
