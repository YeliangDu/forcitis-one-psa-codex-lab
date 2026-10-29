package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * The session principal is built only by identity's IdentityAuthentication; a token made anywhere else in the main code would reach
 * SessionCurrentUser's fallback branch, which looks the employee up again instead of using the session (P2 recheck L-7).
 */
class AuthenticationTokenGuardTest {
    static final String ALLOWED = "com.forcitis.one.platform.identity.infrastructure.security.IdentityAuthentication";
    static final Set<String> FACTORIES = Set.of("authenticated", "unauthenticated");

    static List<String> tokensMade(JavaClasses classes) {
        List<String> found = new ArrayList<>();
        for (JavaClass type : classes) {
            if (type.getName().equals(ALLOWED)) {
                continue;
            }
            for (JavaCall<?> call : type.getCodeUnitCallsFromSelf()) {
                boolean token = call.getTargetOwner().isAssignableTo(Authentication.class) && !call.getTargetOwner().getName().equals(ALLOWED);
                if (token && (call instanceof JavaConstructorCall || FACTORIES.contains(call.getName()))) {
                    found.add(call.getDescription());
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("P2 recheck L-7 the main code makes no Authentication token other than IdentityAuthentication")
    void onlyIdentityMakesTheSessionPrincipal() {
        assertThat(tokensMade(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one")))
                .as(GuardMessage.of("P2 recheck L-7",
                        "main code other than IdentityAuthentication makes an Authentication token (constructor or authenticated/unauthenticated factory)",
                        "remove it and build the session principal only through platform.identity.infrastructure.security.IdentityAuthentication"))
                .isEmpty();
    }

    static class MakesAToken {
        Authentication make() {
            return UsernamePasswordAuthenticationToken.authenticated("someone", "n/a", List.of());
        }
    }

    @Test
    @DisplayName("P2 recheck L-7 positive control: a class making a UsernamePasswordAuthenticationToken is reported")
    void aTokenMadeElsewhereIsReported() {
        assertThat(tokensMade(new ClassFileImporter().importClasses(MakesAToken.class))).singleElement().asString()
                .contains("UsernamePasswordAuthenticationToken.authenticated");
    }
}
