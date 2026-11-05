package com.forcitis.one.accounting.ledger;

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

/** #1440: accounting.ledger owns its schema and reaches the workforce only through its api and events (spec accounting-spine section 6). */
class LedgerModuleBoundaryTest {
    private static final String ROOT = "com.forcitis.one.";
    private static final String OWN = ROOT + "accounting.ledger";
    private static final Set<String> PORTS = Set.of("organization.workforce.api", "organization.workforce.events");
    private static final Pattern INTERNAL_NAME = Pattern.compile("com\\.forcitis\\.one\\.[A-Za-z0-9_.*]+(?:\\.[A-Za-z0-9_*]+)*");
    private static final Pattern SCHEMA = Pattern.compile("(?i)\\baccounting_ledger\\s*\\.");

    private static boolean within(String name, String prefix) {
        return name.equals(prefix) || name.startsWith(prefix + ".");
    }

    private static boolean allowed(String name) {
        return !name.startsWith(ROOT) || within(name, OWN) || within(name, ROOT + "kernel") || PORTS.stream().anyMatch(port -> within(name, ROOT + port));
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

    static boolean foreignSchemaRead(String source, boolean ledgerOwned) {
        return !ledgerOwned && SCHEMA.matcher(source).find();
    }

    @Test
    @DisplayName("#1440 the ledger module imports only the workforce api and events")
    void compiledDependenciesUseOnlyTheSignedPorts() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one");
        List<String> violations = new ArrayList<>();
        for (var type : classes) {
            if (within(type.getPackageName(), OWN)) {
                type.getDirectDependenciesFromSelf().stream().filter(d -> !allowed(d.getTargetClass().getName())).forEach(d -> violations.add(d.getDescription()));
            }
        }
        assertThat(violations).as("#1440 signed ledger dependency boundary").isEmpty();
    }

    @Test
    void sourceImportsAndSchemaOwnershipAreGuardedAndTheEdgeIsDeclared() throws IOException {
        Path own = Path.of("src/main/java/com/forcitis/one/accounting/ledger");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (path.startsWith(own)) {
                    assertThat(sourceViolations(source)).as("forbidden import or qualified reference in " + path).isEmpty();
                }
                assertThat(foreignSchemaRead(source, path.startsWith(own))).as("foreign SQL reads the ledger schema in " + path).isFalse();
            }
        }
        String edge = Files.readAllLines(Path.of("architecture/module-dependencies.txt")).stream().filter(line -> line.startsWith("accounting.ledger ->")).findFirst().orElseThrow();
        assertThat(Set.of(edge.split("->")[1].trim().split(",\\s*"))).containsExactlyInAnyOrder("organization.workforce");
    }

    @Test
    void counterexamplesRejectForbiddenImportsAndForeignReads() {
        assertThat(sourceViolations("import com.forcitis.one.organization.workforce.domain.LegalEntity;")).containsExactly("com.forcitis.one.organization.workforce.domain.LegalEntity");
        assertThat(sourceViolations("import com.forcitis.one.platform.audit.api.AuditTrail;")).hasSize(1);
        assertThat(sourceViolations("import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;")).isEmpty();
        assertThat(sourceViolations("import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;")).isEmpty();
        assertThat(foreignSchemaRead("select id from accounting_ledger.ledgers", false)).isTrue();
        assertThat(foreignSchemaRead("select id from accounting_ledger.ledgers", true)).isFalse();
    }
}
