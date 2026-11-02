package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LeavingEmployees;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AdministratorFloor;
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
    private final LeavingEmployees leaving;
    private final AccountEmployees accountEmployees;

    JdbcAdministratorFloor(JdbcClient jdbc, SeparationReview review, LeavingEmployees leaving, AccountEmployees accountEmployees) {
        this.jdbc = jdbc;
        this.review = review;
        this.leaving = leaving;
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
        Set<EmployeeId> excluded = leaving.excluded();
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
        recipients.removeAll(leaving.held());
        return new Judgement(recipients);
    }
}
