package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.web.NotFromQuery;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModuleDependencyTest {
    static final String ROOT = ArchitectureRulesTest.ROOT + ".";

    @Test
    void dependenciesFollowTheApprovedGraph() throws IOException {
        Map<String, Set<String>> allowed = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("architecture/module-dependencies.txt"))) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("->", -1);
            allowed.put(parts[0].trim(), Arrays.stream(parts[1].split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet()));
        }
        Set<String> violations = new TreeSet<>();
        for (JavaClass c : ArchitectureRulesTest.CLASSES) {
            Optional<String> from = moduleOf(c);
            if (from.isEmpty() || from.get().equals("kernel")) {
                continue;
            }
            if (!allowed.containsKey(from.get())) {
                violations.add("module " + from.get() + " is not declared in architecture/module-dependencies.txt");
                continue;
            }
            for (Dependency d : c.getDirectDependenciesFromSelf()) {
                Optional<String> to = moduleOf(d.getTargetClass());
                if (to.isPresent() && !to.get().equals(from.get()) && !to.get().equals("kernel")
                        && !allowed.get(from.get()).contains(to.get())) {
                    violations.add(from.get() + " -> " + to.get() + " (" + c.getSimpleName() + " uses " + d.getTargetClass().getSimpleName() + ")");
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError("Dependencies outside the approved context map (edit architecture/module-dependencies.txt only "
                    + "after design review):\n  " + String.join("\n  ", violations));
        }
    }

    @Test
    @DisplayName("ADR-0064 decision 1 item 3 and ADR-0066: the signing service reaches only foundation modules and organization.workforce among business modules, and platform.approval never reaches it")
    void signingServiceStaysAtTheFoundationLayer() throws IOException {
        Map<String, Set<String>> declared = declaredGraph();
        Set<String> reachable = reachableFrom("platform.signing", declared);
        Set<String> businessModules = reachable.stream().filter(m -> !m.startsWith("platform.")).collect(Collectors.toCollection(TreeSet::new));
        assertThat(businessModules).isSubsetOf("organization.workforce");
        assertThat(declared.get("platform.signing")).isNotNull()
                .isSubsetOf("platform.audit", "platform.identity", "platform.integration", "platform.file", "organization.workforce");
        assertThat(reachableFrom("platform.approval", declared)).doesNotContain("platform.signing");
    }

    private static Map<String, Set<String>> declaredGraph() throws IOException {
        Map<String, Set<String>> declared = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("architecture/module-dependencies.txt"))) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("->", -1);
            declared.put(parts[0].trim(), Arrays.stream(parts[1].split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet()));
        }
        return declared;
    }

    private static Set<String> reachableFrom(String start, Map<String, Set<String>> declared) {
        Set<String> seen = new TreeSet<>();
        java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>(declared.getOrDefault(start, Set.of()));
        while (!todo.isEmpty()) {
            String next = todo.pop();
            if (seen.add(next)) {
                todo.addAll(declared.getOrDefault(next, Set.of()));
            }
        }
        return seen;
    }

    @Test
    @DisplayName("P2 recheck M-1 platform.identity uses platform.web only for the NotFromQuery marker, and only from infrastructure.security")
    void identityUsesTheWebModuleOnlyForTheMarker() {
        assertThat(identityUsesOfTheWebModule(ArchitectureRulesTest.CLASSES)).isEmpty();
    }

    @Test
    @DisplayName("P2 recheck M-1 counterexamples: another platform.web type, or the marker outside infrastructure.security, is reported")
    void otherUsesOfTheWebModuleAreReported() throws ClassNotFoundException {
        JavaClasses samples = new ClassFileImporter().importClasses(
                Class.forName(ROOT + "platform.identity.infrastructure.security.WebSamplesInSecurity$Marked"),
                Class.forName(ROOT + "platform.identity.infrastructure.security.WebSamplesInSecurity$UsesAnotherWebType"),
                Class.forName(ROOT + "platform.identity.application.WebMarkerOutsideSecurity"));
        assertThat(identityUsesOfTheWebModule(samples)).containsExactlyInAnyOrder(
                ROOT + "platform.identity.application.WebMarkerOutsideSecurity uses " + NotFromQuery.class.getName(),
                ROOT + "platform.identity.infrastructure.security.WebSamplesInSecurity$UsesAnotherWebType uses " + ROOT + "platform.web.WebTypeSample");
    }

    static Set<String> identityUsesOfTheWebModule(JavaClasses classes) {
        Set<String> violations = new TreeSet<>();
        for (JavaClass c : classes) {
            if (!moduleOf(c).equals(Optional.of("platform.identity"))) {
                continue;
            }
            for (Dependency d : c.getDirectDependenciesFromSelf()) {
                if (moduleOf(d.getTargetClass()).equals(Optional.of("platform.web"))
                        && (!d.getTargetClass().getName().equals(NotFromQuery.class.getName())
                        || !c.getPackageName().equals(ROOT + "platform.identity.infrastructure.security"))) {
                    violations.add(c.getName() + " uses " + d.getTargetClass().getName());
                }
            }
        }
        return violations;
    }

    static Optional<String> moduleOf(JavaClass c) {
        String name = c.getPackageName();
        if (!name.startsWith(ROOT)) {
            return Optional.empty();
        }
        String[] p = name.substring(ROOT.length()).split("\\.");
        if (p[0].equals("kernel")) {
            return Optional.of("kernel");
        }
        return p.length >= 2 ? Optional.of(p[0] + "." + p[1]) : Optional.empty();
    }
}
