package com.forcitis.one.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1429 (PY0): payroll reaches only the audit port outside itself and the kernel; later slices add their ports with the code that uses them (#1436 adds the approval and workforce apis). */
class PayrollModuleBoundaryTest {
    private static final String ROOT = "com.forcitis.one.";
    private static final String OWN = ROOT + "organization.payroll";

    private static boolean within(String name, String prefix) {
        return name.equals(prefix) || name.startsWith(prefix + ".");
    }

    @Test
    @DisplayName("#1429 payroll imports only the kernel, the audit, approval and workforce apis and itself")
    void compiledDependenciesUseOnlyTheAuditPort() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one");
        List<String> violations = new ArrayList<>();
        for (var type : classes) {
            if (within(type.getPackageName(), OWN)) {
                type.getDirectDependenciesFromSelf().stream()
                        .map(d -> d.getTargetClass().getName())
                        .filter(name -> name.startsWith(ROOT) && !within(name, OWN) && !within(name, ROOT + "kernel") && !within(name, ROOT + "platform.audit.api")
                                && !within(name, ROOT + "platform.approval.api") && !within(name, ROOT + "platform.approval.events") && !within(name, ROOT + "platform.identity.api") && !within(name, ROOT + "organization.workforce.api"))
                        .forEach(violations::add);
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("#1429 the approved edges of payroll are the audit, approval and workforce modules, and no other module's code names the payroll schema")
    void edgeAndSchemaOwnership() throws IOException {
        String edge = Files.readAllLines(Path.of("architecture/module-dependencies.txt")).stream()
                .filter(line -> line.startsWith("organization.payroll ->")).findFirst().orElseThrow();
        assertThat(edge.split("->")[1].trim()).isEqualTo("platform.audit, platform.approval, platform.identity, organization.workforce");
        Path own = Path.of("src/main/java/com/forcitis/one/organization/payroll");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java") && !p.startsWith(own)).toList()) {
                assertThat(Files.readString(path)).as("payroll schema named outside the module in " + path).doesNotContain("organization_payroll.");
            }
        }
    }
}
