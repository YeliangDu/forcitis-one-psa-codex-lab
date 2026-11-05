package com.forcitis.one.organization.recruitment;

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

/** #1450 R1 (organization-recruitment.md section 6): recruitment owns its schema, uses only the signed ports, and nothing in workforce, approval or numbering points back at it. */
class RecruitmentModuleBoundaryTest {
    private static final String ROOT = "com.forcitis.one.";
    private static final String OWN = ROOT + "organization.recruitment";
    private static final Set<String> PORTS = Set.of("organization.workforce.api", "organization.workforce.events", "platform.approval.api", "platform.approval.events",
            "platform.audit.api", "platform.numbering.api");
    private static final Pattern INTERNAL_NAME = Pattern.compile("com\\.forcitis\\.one\\.[A-Za-z0-9_.*]+(?:\\.[A-Za-z0-9_*]+)*");
    private static final Pattern SCHEMA = Pattern.compile("(?i)\\borganization_recruitment\\s*\\.");

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
            if (!allowed(references.group())) {
                violations.add(references.group());
            }
        }
        return violations;
    }

    static boolean foreignSchemaRead(String source, boolean owned) {
        return !owned && SCHEMA.matcher(source).find();
    }

    @Test
    @DisplayName("#1450 recruitment imports only the workforce, approval, audit and numbering ports")
    void compiledDependenciesUseOnlyTheSignedPorts() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one");
        List<String> violations = new ArrayList<>();
        for (var type : classes) {
            if (within(type.getPackageName(), OWN)) {
                type.getDirectDependenciesFromSelf().stream().filter(d -> !allowed(d.getTargetClass().getName())).forEach(d -> violations.add(d.getDescription()));
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("#1450 only recruitment reads its schema, and the edge list holds the signed line and no edge back")
    void sourceSchemaOwnershipAndEdgesAreGuarded() throws IOException {
        Path own = Path.of("src/main/java/com/forcitis/one/organization/recruitment");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                if (path.startsWith(own)) {
                    assertThat(sourceViolations(source)).as("forbidden reference in " + path).isEmpty();
                }
                assertThat(foreignSchemaRead(source, path.startsWith(own))).as("foreign SQL reads the recruitment schema in " + path).isFalse();
            }
        }
        List<String> edges = Files.readAllLines(Path.of("architecture/module-dependencies.txt"));
        String edge = edges.stream().filter(line -> line.startsWith("organization.recruitment ->")).findFirst().orElseThrow();
        assertThat(Set.of(edge.split("->")[1].trim().split(",\\s*")))
                .containsExactlyInAnyOrder("organization.workforce", "platform.approval", "platform.audit", "platform.numbering");
        assertThat(edges.stream().filter(line -> !line.startsWith("#") && !line.startsWith("organization.recruitment ->"))
                .filter(line -> line.indexOf("->") > 0 && line.substring(line.indexOf("->")).contains("organization.recruitment"))).as("nothing points to recruitment").isEmpty();
    }

    @Test
    @DisplayName("#1450 counterexamples: an internal class or a foreign read is a violation")
    void counterexamplesAreRejected() {
        assertThat(sourceViolations("import com.forcitis.one.organization.workforce.domain.Employee;")).hasSize(1);
        assertThat(sourceViolations("import com.forcitis.one.platform.approval.infrastructure.X;")).hasSize(1);
        assertThat(sourceViolations("import com.forcitis.one.platform.numbering.api.DocumentNumbers;")).isEmpty();
        assertThat(foreignSchemaRead("select id from organization_recruitment.recruitment_requisitions", false)).isTrue();
        assertThat(foreignSchemaRead("select id from organization_recruitment.recruitment_requisitions", true)).isFalse();
    }
}
