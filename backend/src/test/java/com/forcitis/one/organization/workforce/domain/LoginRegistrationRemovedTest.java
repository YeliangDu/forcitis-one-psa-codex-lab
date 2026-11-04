package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.organization.workforce.api.LoginLookup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0069 s7 to s9: a login never creates an employee, the short names are not refreshed from the old system, the login name is not a lookup key. */
class LoginRegistrationRemovedTest {
    private static final String BASE = "com.forcitis.one.organization.workforce.";

    @Test
    @DisplayName("ADR-0069 s8/s9 the classes that registered an employee at login and refreshed short names from the old system are gone")
    void theRemovedClassesStayRemoved() {
        for (String removed : new String[] {"application.RegisterEmployeeOnLogin", "application.RegisterEmployeeOnLoginName", "application.LoginMissRegistration",
                "application.PersistRegisteredEmployee", "application.RefreshShortNames", "domain.RegisteredEmployee", "domain.ShortNameSource",
                "events.EmployeeRegistered", "infrastructure.external.ShortNameAdapter", "infrastructure.scheduling.ShortNameRefreshJob"}) {
            assertThatThrownBy(() -> Class.forName(BASE + removed)).as(removed).isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    @DisplayName("ADR-0069 s7 a login is looked up by the WeCom user id only")
    void loginLookupHasOnlyTheWecomKey() {
        assertThat(LoginLookup.class.getPermittedSubclasses()).extracting(Class::getSimpleName).containsExactly("ByWecomUser");
    }
}
