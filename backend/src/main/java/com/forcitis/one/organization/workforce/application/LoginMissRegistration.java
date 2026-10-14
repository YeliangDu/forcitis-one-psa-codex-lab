package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.MemberChecks;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.StaffMember;
import com.forcitis.one.organization.workforce.domain.SyncDiff;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Doubtful or out-of-scope people answer "unknown": login fails closed and the next sync decides. */
@Component
class LoginMissRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(LoginMissRegistration.class);

    private final OrgSnapshotRepository snapshots;
    private final PersistRegisteredEmployee persist;

    LoginMissRegistration(OrgSnapshotRepository snapshots, PersistRegisteredEmployee persist) {
        this.snapshots = snapshots;
        this.persist = persist;
    }

    Optional<StaffMember> register(List<SourceMember> found, String keyKind) {
        Optional<SourceMember> chosen = choose(found, keyKind);
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
                new Assignment(member.userId(), member.orgUnitId(), member.positionId(), validSupervisor(member)));
        persist.execute(staff);
        return Optional.of(staff);
    }

    /** Several rows for one key are ambiguous unless exactly one of them is employed. */
    private static Optional<SourceMember> choose(List<SourceMember> found, String keyKind) {
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
        LOG.warn("organization.workforce login-miss lookup is ambiguous ({} rows); the {} is not trusted", found.size(), keyKind);
        return Optional.empty();
    }

    private UserId validSupervisor(SourceMember member) {
        UserId leader = member.supervisorId();
        if (leader == null || leader.equals(member.userId())) {
            return null;
        }
        return snapshots.findStaff(leader)
                .filter(s -> s.employee().employmentStatus() == EmploymentStatus.EMPLOYED)
                .map(StaffMember::userId)
                .orElse(null);
    }
}
