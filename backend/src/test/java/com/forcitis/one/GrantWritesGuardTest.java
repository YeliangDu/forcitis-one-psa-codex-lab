package com.forcitis.one;

import static com.tngtech.archunit.lang.conditions.ArchPredicates.have;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1326: grant rows are written through one door, so the refusal of a separated account (UserAccount.replaceRoles, called there first) cannot be skipped. */
class GrantWritesGuardTest {

    private static final String DOOR = "com.forcitis.one.platform.identity.application.AccountGrants";
    private static final String STORE = "com.forcitis.one.platform.identity.infrastructure.persistence.JdbcAccountRepository";
    private static final Pattern GRANT_TABLE_WRITE = Pattern.compile("(?is)(insert\\s+into|update|delete\\s+from)\\s+platform_identity\\.account_role_grants\\b");

    /** Any call of a saveGrants of the repository, through the interface or a concrete class. */
    private static DescribedPredicate<JavaCall<?>> savesGrants() {
        return DescribedPredicate.describe("saveGrants of an AccountRepository", call -> call.getName().equals("saveGrants")
                && call.getTargetOwner().isAssignableTo(com.forcitis.one.platform.identity.domain.AccountRepository.class));
    }

    static ArchRule grantRowsOnlyThroughAccountGrants() {
        return noClasses().that().doNotHaveFullyQualifiedName(DOOR)
                .should().callMethodWhere(savesGrants())
                .because("a grant written elsewhere skips the refusal of a separated account; call AccountGrants.replace, which asks the aggregate first (#1326)");
    }

    @Test
    @DisplayName("#1326 no class but AccountGrants calls saveGrants of an AccountRepository, by interface or by concrete class")
    void grantRowsAreWrittenOnlyThroughAccountGrants() {
        grantRowsOnlyThroughAccountGrants().check(ArchitectureRulesTest.CLASSES);
    }

    @Test
    @DisplayName("#1326 SQL that writes platform_identity.account_role_grants appears only in JdbcAccountRepository")
    void grantTableIsWrittenOnlyByTheAccountRepository() throws IOException {
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            List<String> writers = files.filter(path -> path.toString().endsWith(".java")).filter(path -> {
                try {
                    return GRANT_TABLE_WRITE.matcher(Files.readString(path)).find();
                } catch (IOException unreadable) {
                    throw new IllegalStateException(unreadable);
                }
            }).map(path -> root.relativize(path).toString().replace('/', '.').replaceFirst("\\.java$", "")).toList();
            assertThat(writers).as("writers of the grant table").containsExactly(STORE);
        }
    }
}
