package com.forcitis.one.platform.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.Availability;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AdministratorFloor;
import com.forcitis.one.platform.identity.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

@DisplayName("#732 the floor judges only people who still count as available (user decision 2026-10-30 「统一都算已走」)")
class JdbcAdministratorFloorTest {

    static final UserId PERSON = TestAccounts.account(1001);
    static final EmployeeId EMPLOYEE = TestAccounts.employee(1001);

    final SeparationReview review = mock(SeparationReview.class);
    final OrgDirectory directory = mock(OrgDirectory.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final JdbcAdministratorFloor floor = new JdbcAdministratorFloor(mock(JdbcClient.class), review, directory, accounts,
            Clock.fixed(Instant.parse("2026-11-02T02:00:00Z"), ZoneOffset.UTC), new BusinessZone(ZoneId.of("Asia/Shanghai")), TestAccounts.everyoneHasOne());

    JdbcAdministratorFloorTest() {
        when(accounts.findEnabledHolders(Role.SUPER_ADMIN)).thenReturn(Set.of(PERSON));
        when(accounts.findEnabledHolders(Role.SECURITY_AUDITOR)).thenReturn(Set.of());
        when(review.review(any(), any(), any())).thenReturn(new SeparationReview.Verdict(Set.of(EMPLOYEE), false, 0, "", "", Set.of()));
    }

    @Test
    @DisplayName("an available super administrator who is the last one is protected")
    void theLastAvailableOneIsRefused() {
        when(directory.availabilityOn(any(), any())).thenReturn(Availability.AVAILABLE);

        assertThatThrownBy(() -> floor.judge(PERSON, true)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("a person already counted gone (held, released-pending, unpaid-leave start entered) is not judged: no refusal and no notice")
    void aPersonAlreadyCountedGoneIsNotJudged() {
        when(directory.availabilityOn(any(), any())).thenReturn(new Availability(false, true, true));

        AdministratorFloor.Judgement judgement = floor.judge(PERSON, true);

        assertThat(judgement).isEqualTo(AdministratorFloor.Judgement.UNAFFECTED);
        assertThat(judgement.noticeDue()).isFalse();
    }
}
