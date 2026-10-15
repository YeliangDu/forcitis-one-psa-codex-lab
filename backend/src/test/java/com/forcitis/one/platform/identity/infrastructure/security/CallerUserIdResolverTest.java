package com.forcitis.one.platform.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.platform.identity.api.IdentityError;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;

class CallerUserIdResolverTest {

    final CallerUserIdResolver resolver = new CallerUserIdResolver(new SessionCurrentUser(mock(EmployeeLegalEntities.class), TestAccounts.everyoneHasOne(),
            new BusinessZone(ZoneId.of("Asia/Shanghai")), Clock.systemUTC(), mock(CachedSessionPrincipals.class)));

    @SuppressWarnings("unused")
    void handler(UserId caller, @PathVariable UserId named) {
    }

    private MethodParameter parameter(int index) throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("handler", UserId.class, UserId.class);
        return new MethodParameter(method, index);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("P2-M1 a bare UserId parameter of a controller is the signed-in account, read from the session")
    void P2_M1_theCallerIsTheSessionAccount() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(TestAccounts.account(7).value().toString(), null, List.of()));

        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).as("an annotated UserId comes from the request, not the session").isFalse();
        assertThat(resolver.resolveArgument(parameter(0), null, null, null)).isEqualTo(TestAccounts.account(7));
    }

    @Test
    @DisplayName("P2-M1 a principal that is not a canonical account id is refused as not signed in (RULING-P2-4)")
    void P2_M1_aBrokenPrincipalIsNotSignedIn() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("70001", null, List.of()));

        assertThatThrownBy(() -> resolver.resolveArgument(parameter(0), null, null, null)).isInstanceOfSatisfying(DomainException.class,
                refused -> assertThat(refused.errorCode()).isEqualTo(IdentityError.PLATFORM_IDENTITY_NOT_SIGNED_IN));
    }
}
