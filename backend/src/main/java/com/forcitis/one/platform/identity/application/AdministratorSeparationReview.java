package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.SingleOperatorMode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A personnel command that would separate a holder of super_admin or of the security auditor role waits for a person (ADR-0027 section 1,
 * Q-3), and so does one that would leave fewer than two usable super_admin holders. Only a retained holder of one of the two
 * roles may lift that hold (ADR-0028).
 */
@Service
class AdministratorSeparationReview implements SeparationReview {

    static final int MINIMUM_SUPER_ADMINISTRATORS = 2;
    static final int MINIMUM_SUPER_ADMINISTRATORS_SINGLE_OPERATOR = 1;

    private final AccountRepository accounts;
    private final OrgDirectory directory;
    private final SessionContext session;
    private final AccountEmployees accountEmployees;
    private final SingleOperatorMode mode;
    private final Clock clock;
    private final BusinessZone zone;

    AdministratorSeparationReview(AccountRepository accounts, OrgDirectory directory, SessionContext session,
            AccountEmployees accountEmployees, SingleOperatorMode mode, Clock clock, BusinessZone zone) {
        this.clock = clock;
        this.zone = zone;
        this.mode = mode;
        this.accountEmployees = accountEmployees;
        this.accounts = accounts;
        this.directory = directory;
        this.session = session;
    }

    @Override
    @Transactional(readOnly = true)
    public Verdict review(List<EmployeeId> separating, Set<EmployeeId> held, UserId releaser, @Nullable LocalDate nullableSeparatingDay) {
        LocalDate today = zone.dateOf(clock.instant());
        LocalDate separatingDay = nullableSeparatingDay == null ? today : nullableSeparatingDay;
        Set<EmployeeId> superAdministrators = usableHolders(Role.SUPER_ADMIN, today, separating, separatingDay);
        Set<EmployeeId> everyone = new HashSet<>(superAdministrators);
        everyone.addAll(usableHolders(Role.SECURITY_AUDITOR, today, separating, separatingDay));
        Set<EmployeeId> separatedAdministrators = new HashSet<>(separating);
        separatedAdministrators.retainAll(everyone);
        Set<EmployeeId> remaining = new HashSet<>(superAdministrators);
        remaining.removeAll(separating);
        remaining.removeAll(held);
        Set<EmployeeId> noticeRecipients = new HashSet<>(everyone);
        noticeRecipients.removeAll(separating);
        EmployeeId releasingEmployee = releaser == null ? null : accountEmployees.employeeOf(releaser).orElse(null);
        boolean retained = releasingEmployee != null && !separating.contains(releasingEmployee) && !held.contains(releasingEmployee)
                && everyone.contains(releasingEmployee);
        int minimum = mode.enabled() ? MINIMUM_SUPER_ADMINISTRATORS_SINGLE_OPERATOR : MINIMUM_SUPER_ADMINISTRATORS;
        String shortage = remaining.size() < minimum
                ? "; only " + remaining.size() + " usable super_admin holder(s) would remain, at least " + minimum + " are needed"
                : "";
        String reason = separatedAdministrators.isEmpty() ? "" : separatedAdministrators.size() + " separated person(s) hold super_admin or security_auditor" + shortage
                + "; another holder must confirm";
        return new Verdict(separatedAdministrators, retained, remaining.size(), releaser == null ? "" : session.modeIfSignedIn().map(Enum::name).orElse(""), reason,
                noticeRecipients);
    }

    /**
     * Enabled holders who are employed and not on unpaid leave that day: a person on leave counts neither as retained nor as an available administrator (ADR-0068 section 3).
     * The separating are read on their last working day when that is not today (the due-day re-judgement, #967), everyone else today.
     */
    private Set<EmployeeId> usableHolders(RoleCode role, LocalDate today, List<EmployeeId> separating, LocalDate separatingDay) {
        Set<EmployeeId> usable = new HashSet<>();
        for (UserId holder : accounts.findEnabledHolders(role)) {
            accountEmployees.employeeOf(holder)
                    .filter(employee -> {
                        if (separating.contains(employee) && !separatingDay.equals(today)) {
                            return directory.employmentOn(employee, separatingDay).map(status -> status == EmploymentStatus.EMPLOYED).orElse(false)
                                    && !directory.availabilityOn(employee, separatingDay).onUnpaidLeave();
                        }
                        return directory.findEmployee(employee).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false)
                                && !directory.availabilityOn(employee, today).onUnpaidLeave();
                    })
                    .ifPresent(usable::add);
        }
        return usable;
    }
}
