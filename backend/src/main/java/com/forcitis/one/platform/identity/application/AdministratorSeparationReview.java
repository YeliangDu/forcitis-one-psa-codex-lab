package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A sync run that would separate a holder of super_admin or of the security auditor role waits for a person (ADR-0027 section 1,
 * Q-3), and so does one that would leave fewer than two usable super_admin holders. Only a retained holder of one of the two
 * roles may lift that hold (ADR-0028).
 */
@Service
class AdministratorSeparationReview implements SeparationReview {

    static final int MINIMUM_SUPER_ADMINISTRATORS = 2;

    private final AccountRepository accounts;
    private final OrgDirectory directory;
    private final SessionContext session;

    AdministratorSeparationReview(AccountRepository accounts, OrgDirectory directory, SessionContext session) {
        this.accounts = accounts;
        this.directory = directory;
        this.session = session;
    }

    @Override
    @Transactional(readOnly = true)
    public Verdict review(List<UserId> separating, UserId releaser) {
        Set<UserId> superAdministrators = usableHolders(Role.SUPER_ADMIN);
        Set<UserId> everyone = new HashSet<>(superAdministrators);
        everyone.addAll(usableHolders(Role.SECURITY_AUDITOR));
        Set<UserId> separatedAdministrators = new HashSet<>(separating);
        separatedAdministrators.retainAll(everyone);
        Set<UserId> remaining = new HashSet<>(superAdministrators);
        remaining.removeAll(separating);
        boolean retained = releaser != null && !separating.contains(releaser) && everyone.contains(releaser);
        String shortage = remaining.size() < MINIMUM_SUPER_ADMINISTRATORS
                ? "; only " + remaining.size() + " usable super_admin holder(s) would remain, at least " + MINIMUM_SUPER_ADMINISTRATORS + " are needed"
                : "";
        String reason = separatedAdministrators.isEmpty() ? "" : separatedAdministrators.size() + " separated person(s) hold super_admin or security_auditor" + shortage
                + "; another holder must confirm";
        return new Verdict(separatedAdministrators, retained, remaining.size(), releaser == null ? "" : session.modeIfSignedIn().map(Enum::name).orElse(""), reason);
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
}
