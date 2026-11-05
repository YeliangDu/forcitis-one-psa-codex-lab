package com.forcitis.one.accounting.period;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1439: accounting.period owns its schema; it reaches the legal-entity directory through the kernel port and the audit trail through the audit api only. */
class AccountingPeriodModuleBoundaryTest {
    private static final String ROOT = "com.forcitis.one.";
    private static final String OWN = ROOT + "accounting.period";
    private static final Set<String> PORTS = Set.of("platform.audit.api");
    private static final Pattern INTERNAL_NAME = Pattern.compile("com\\.forcitis\\.one\\.[A-Za-z0-9_.*]+(?:\\.[A-Za-z0-9_*]+)*");
    private static final Pattern SCHEMA = Pattern.compile("(?i)\\baccounting_period\\s*\\.");

    private static boolean within(String name, String prefix) {
        return name.equals(prefix) || name.startsWith(prefix + ".");
    }

    private static boolean allowed(String name) {
        return !name.startsWith(ROOT) || within(name, OWN) || within(name, ROOT + "kernel")
                || PORTS.stream().anyMatch(port -> within(name, ROOT + port));
    }

    static List<String> sourceViolations(String source) {
        List<String> violations = new ArrayList<>();
        var references = INTERNAL_NAME.matcher(source);
        while (references.find()) {
            String name = references.group();
            if (!allowed(name)) {
                violations.add(name);
            }
        }
        return violations;
    }

    static boolean foreignSchemaRead(String source, boolean ownSchema) {
        return !ownSchema && SCHEMA.matcher(source).find();
    }

    @Test
    @DisplayName("#1439 accounting.period imports only the kernel and the audit api")
    void compiledDependenciesUseOnlyTheSignedPorts() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one");
        List<String> violations = new ArrayList<>();
        for (var type : classes) {
            if (within(type.getPackageName(), OWN)) {
                type.getDirectDependenciesFromSelf().stream().filter(d -> !allowed(d.getTargetClass().getName()))
                        .forEach(d -> violations.add(d.getDescription()));
            }
        }
        assertThat(violations).as("#1439 accounting.period dependency boundary").isEmpty();
    }

    @Test
    void sourceImportsAndSchemaOwnershipAreGuardedEvenBeforeAProductionClassExists() throws IOException {
        Path own = Path.of("src/main/java/com/forcitis/one/accounting/period");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (path.startsWith(own)) {
                    assertThat(sourceViolations(source)).as("forbidden import or qualified reference in " + path).isEmpty();
                }
                assertThat(foreignSchemaRead(source, path.startsWith(own))).as("foreign SQL reads accounting_period schema in " + path).isFalse();
            }
        }
        String edge = Files.readAllLines(Path.of("architecture/module-dependencies.txt")).stream()
                .filter(line -> line.startsWith("accounting.period ->")).findFirst().orElseThrow();
        assertThat(Set.of(edge.split("->")[1].trim().split(",\\s*")))
                .containsExactly("platform.audit");
    }

    @Test
    void counterexamplesRejectForbiddenImportsAndForeignReads() {
        assertThat(sourceViolations("import com.forcitis.one.platform.identity.api.CurrentUser;"))
                .containsExactly("com.forcitis.one.platform.identity.api.CurrentUser");
        assertThat(sourceViolations("import com.forcitis.one.organization.workforce.api.EmployeeDirectory;"))
                .containsExactly("com.forcitis.one.organization.workforce.api.EmployeeDirectory");
        assertThat(sourceViolations("import com.forcitis.one.platform.audit.infrastructure.JdbcAuditRepository;"))
                .hasSize(1);
        assertThat(sourceViolations("import com.forcitis.one.platform.audit.api.AuditTrail;" )).isEmpty();
        assertThat(sourceViolations("import com.forcitis.one.kernel.KnownLegalEntities;")).isEmpty();
        assertThat(foreignSchemaRead("select id from accounting_period.fiscal_periods", false)).isTrue();
        assertThat(foreignSchemaRead("select id from accounting_period.fiscal_periods", true)).isFalse();
    }
}
