package com.forcitis.one.platform.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.platform.identity.api.IdentityError;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionCurrentUserTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-31T16:30:00Z"), ZoneOffset.UTC);
    final EmployeeLegalEntities employeeLegalEntities = mock(EmployeeLegalEntities.class);
    final SessionCurrentUser currentUser = new SessionCurrentUser(employeeLegalEntities, TestAccounts.everyoneHasOne(),
            new BusinessZone(ZoneId.of("Asia/Shanghai")), CLOCK, mock(CachedSessionPrincipals.class));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(String userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    @DisplayName("B-1 the legal entity is the signed-in employee's on today's business day, cut in the business zone")
    void resolvedFromTheOrganisationOnTheBusinessDay() {
        signIn(TestAccounts.account(1001).value().toString());
        when(employeeLegalEntities.on(new EmployeeId(1001), LocalDate.of(2027, 1, 1))).thenReturn(new LegalEntityId(TestIds.uuid(7)));

        assertThat(currentUser.legalEntityId()).isEqualTo(new LegalEntityId(TestIds.uuid(7)));
    }

    @Test
    @DisplayName("B-1 when no legal entity can be resolved the failure reaches the caller; nothing is defaulted")
    void unresolvedFails() {
        signIn(TestAccounts.account(1001).value().toString());
        DomainException unresolved = new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED,
                ErrorKind.UNPROCESSABLE, "none");
        when(employeeLegalEntities.on(new EmployeeId(1001), LocalDate.of(2027, 1, 1))).thenThrow(unresolved);

        assertThatThrownBy(currentUser::legalEntityId).isSameAs(unresolved);
    }

    @Test
    @DisplayName("the signed-in person is the employee behind the account: the two ids are told apart, not read from one number")
    void employeeIsNotTheAccountNumber() {
        signIn(TestAccounts.account(1001).value().toString());

        assertThat(currentUser.userId()).isEqualTo(TestAccounts.account(1001));
        assertThat(currentUser.employeeId()).isEqualTo(new EmployeeId(1001));
    }

    @Test
    @DisplayName("an anonymous or missing session is refused as not signed in, not as a parse error")
    void notSignedIn() {
        assertThatThrownBy(currentUser::userId).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(IdentityError.PLATFORM_IDENTITY_NOT_SIGNED_IN));
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThatThrownBy(currentUser::userId).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.kind()).isEqualTo(ErrorKind.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("P2-M4 in a session the employee comes with the session principal, resolved once and cached; the account table is not asked on every call")
    void P2_M4_employeeIdIsResolvedOncePerSession() {
        com.forcitis.one.kernel.AccountEmployees accounts = org.mockito.Mockito.spy(TestAccounts.everyoneHasOne());
        com.forcitis.one.platform.identity.application.SessionPrincipals resolver = mock(com.forcitis.one.platform.identity.application.SessionPrincipals.class);
        UserId account = TestAccounts.account(1001);
        when(resolver.resolve(org.mockito.ArgumentMatchers.eq(account), org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.forcitis.one.platform.identity.application.SessionPrincipal(com.forcitis.one.platform.identity.application.Eligibility.ACTIVE,
                        java.util.Set.of(), new EmployeeId(1001)));
        CachedSessionPrincipals principals = new CachedSessionPrincipals(resolver, new com.forcitis.one.platform.identity.application.SessionSettings(
                java.time.Duration.ofHours(10), java.time.Duration.ofHours(24), java.time.Duration.ofSeconds(60), true, "Lax"), CLOCK);
        SessionCurrentUser inSession = new SessionCurrentUser(employeeLegalEntities, accounts, new BusinessZone(ZoneId.of("Asia/Shanghai")), CLOCK, principals);
        SecurityContextHolder.getContext().setAuthentication(new IdentityAuthentication(account.value().toString(), CLOCK.instant(), "WECOM_WORKBENCH",
                null, List.of(), true));

        for (int call = 0; call < 3; call++) {
            assertThat(inSession.employeeId()).isEqualTo(new EmployeeId(1001));
        }

        org.mockito.Mockito.verify(accounts, org.mockito.Mockito.never()).employeeOf(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(resolver, org.mockito.Mockito.times(1)).resolve(org.mockito.ArgumentMatchers.eq(account), org.mockito.ArgumentMatchers.any());
    }
}
