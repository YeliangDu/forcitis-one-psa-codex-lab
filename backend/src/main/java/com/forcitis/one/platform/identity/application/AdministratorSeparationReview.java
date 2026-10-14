package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A sync run that would separate a holder of super_admin or of the security auditor role waits for a person (ADR-0027 section 1,
 * Q-3), and so does one that would leave fewer than two usable super_admin holders. Only a remaining holder of one of the two
 * roles may release it.
 */
@Service
class AdministratorSeparationReview implements SeparationReview {

    static final int MINIMUM_SUPER_ADMINISTRATORS = 2;
    static final RoleCode SECURITY_AUDITOR = new RoleCode("security_auditor");

    private final AccountRepository accounts;
    private final OrgDirectory directory;

    AdministratorSeparationReview(AccountRepository accounts, OrgDirectory directory) {
        this.accounts = accounts;
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> holdReason(List<UserId> separating, UserId releaser) {
        Set<UserId> superAdministrators = usableHolders(Role.SUPER_ADMIN);
        Set<UserId> involved = new HashSet<>(separating);
        involved.retainAll(union(superAdministrators, usableHolders(SECURITY_AUDITOR)));
        if (involved.isEmpty() || mayRelease(releaser, separating)) {
            return Optional.empty();
        }
        Set<UserId> remaining = new HashSet<>(superAdministrators);
        remaining.removeAll(separating);
        String shortage = remaining.size() < MINIMUM_SUPER_ADMINISTRATORS
                ? "; only " + remaining.size() + " usable super_admin holder(s) would remain, at least " + MINIMUM_SUPER_ADMINISTRATORS + " are needed"
                : "";
        return Optional.of(involved.size() + " separated person(s) hold super_admin or security_auditor" + shortage
                + "; another holder must confirm");
    }

    private boolean mayRelease(UserId releaser, List<UserId> separating) {
        return releaser != null && !separating.contains(releaser)
                && union(usableHolders(Role.SUPER_ADMIN), usableHolders(SECURITY_AUDITOR)).contains(releaser);
    }

    private Set<UserId> usableHolders(RoleCode role) {
        Set<UserId> usable = new HashSet<>();
        for (UserId holder : accounts.findEnabledHolders(role)) {
            if (directory.findEmployee(holder).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false)) {
                usable.add(holder);
            }
        }
        return usable;
    }

    private static Set<UserId> union(Set<UserId> first, Set<UserId> second) {
        Set<UserId> all = new HashSet<>(first);
        all.addAll(second);
        return all;
    }
}
