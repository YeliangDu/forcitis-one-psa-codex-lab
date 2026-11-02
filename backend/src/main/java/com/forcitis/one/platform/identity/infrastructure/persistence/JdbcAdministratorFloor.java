package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.Availability;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AdministratorFloor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcAdministratorFloor implements AdministratorFloor {

    private final JdbcClient jdbc;
    private final SeparationReview review;
    private final OrgDirectory directory;
    private final AccountRepository accounts;
    private final Clock clock;
    private final BusinessZone zone;
    private final AccountEmployees accountEmployees;

    JdbcAdministratorFloor(JdbcClient jdbc, SeparationReview review, OrgDirectory directory, AccountRepository accounts, Clock clock, BusinessZone zone,
            AccountEmployees accountEmployees) {
        this.directory = directory;
        this.accounts = accounts;
        this.clock = clock;
        this.zone = zone;
        this.jdbc = jdbc;
        this.review = review;
        this.accountEmployees = accountEmployees;
    }

    @Override
    public void lockDecisions() {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", SeparationReview.SEPARATION_LOCK_KEY).query().singleRow();
    }

    @Override
    public Judgement judge(UserId person, boolean losesSuperAdmin) {
        Optional<EmployeeId> found = accountEmployees.employeeOf(person);
        if (found.isEmpty()) {
            return Judgement.UNAFFECTED;
        }
        Set<EmployeeId> excluded = new HashSet<>();
        Set<EmployeeId> held = new HashSet<>();
        LocalDate today = zone.dateOf(clock.instant());
        Set<UserId> holders = new HashSet<>(accounts.findEnabledHolders(Role.SUPER_ADMIN));
        holders.addAll(accounts.findEnabledHolders(Role.SECURITY_AUDITOR));
        for (UserId holder : holders) {
            accountEmployees.employeeOf(holder).ifPresent(employee -> {
                Availability availability = directory.availabilityOn(employee, today);
                if (availability.leaving()) {
                    excluded.add(employee);
                }
                if (availability.separationHeld()) {
                    held.add(employee);
                }
            });
        }
        if (excluded.contains(found.get())) {
            return Judgement.UNAFFECTED;
        }
        SeparationReview.Verdict without = review.review(List.of(found.get()), excluded, null);
        if (without.separatedAdministrators().isEmpty()) {
            return Judgement.UNAFFECTED;
        }
        int remaining = losesSuperAdmin ? without.remainingAdministrators() : review.review(List.of(), excluded, null).remainingAdministrators();
        if (remaining == 0) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_LAST_SUPER_ADMIN_PROTECTED, ErrorKind.UNPROCESSABLE, "no available super_admin would remain");
        }
        if (remaining != 1) {
            return Judgement.UNAFFECTED;
        }
        Set<EmployeeId> recipients = new HashSet<>(without.noticeRecipients());
        recipients.removeAll(held);
        return new Judgement(recipients);
    }
}
