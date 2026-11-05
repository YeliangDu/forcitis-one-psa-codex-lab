package com.forcitis.one;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1326: grant rows are written through one door, so the refusal of a separated account (UserAccount.replaceRoles, called there first) cannot be skipped. */
class GrantWritesGuardTest {

    static ArchRule grantRowsOnlyThroughAccountGrants() {
        return noClasses().that().doNotHaveFullyQualifiedName("com.forcitis.one.platform.identity.application.AccountGrants")
                .should().callMethod(com.forcitis.one.platform.identity.domain.AccountRepository.class, "saveGrants", com.forcitis.one.kernel.UserId.class,
                        com.forcitis.one.platform.identity.domain.GrantChanges.class)
                .because("a grant written elsewhere skips the refusal of a separated account; call AccountGrants.replace, which asks the aggregate first (#1326)");
    }

    @Test
    @DisplayName("#1326 no class but AccountGrants calls AccountRepository.saveGrants")
    void grantRowsAreWrittenOnlyThroughAccountGrants() {
        grantRowsOnlyThroughAccountGrants().check(ArchitectureRulesTest.CLASSES);
    }
}
