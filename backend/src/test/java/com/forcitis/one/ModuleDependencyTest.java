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
        Map<String, Set<String>> allowed = declaredGraph(Files.readAllLines(Path.of("architecture/module-dependencies.txt")));
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
            throw new AssertionError(GuardMessage.of("D1",
                    "dependencies outside the approved context map (backend/architecture/module-dependencies.txt):\n  " + String.join("\n  ", violations) + "\n",
                    "remove the dependency and call the other module's api or events; only after design review add the edge to backend/architecture/module-dependencies.txt"));
        }
    }

    static final Set<String> SIGNING_MAY_REACH = Set.of("platform.audit", "platform.identity", "platform.integration", "platform.file",
            "platform.web", "organization.workforce");

    @Test
    @DisplayName("ADR-0064 decision 1 item 3 and ADR-0066: the signing service reaches only foundation modules and organization.workforce, and only business modules depend on it")
    void signingServiceStaysAtTheFoundationLayer() throws IOException {
        assertThat(signingViolations(declaredGraph(Files.readAllLines(Path.of("architecture/module-dependencies.txt")))))
                .as(GuardMessage.of("ADR-0064",
                        "platform.signing reaches a module outside the foundation layer, or a platform module depends on platform.signing",
                        "remove that edge from backend/architecture/module-dependencies.txt; signing may reach only " + SIGNING_MAY_REACH + " and only business modules may depend on it"))
                .isEmpty();
    }

    @Test
    @DisplayName("ADR-0064 decision 1 item 3 counterexamples: a transitive edge to approval or a business module, or a platform module depending on signing, is reported")
    void signingServiceViolationsAreReported() {
        assertThat(signingViolations(declaredGraph(java.util.List.of(
                "platform.signing -> platform.identity", "platform.identity -> platform.approval", "platform.approval ->")))).isNotEmpty();
        assertThat(signingViolations(declaredGraph(java.util.List.of(
                "platform.signing -> platform.audit", "platform.audit -> organization.administration", "organization.administration ->")))).isNotEmpty();
        assertThat(signingViolations(declaredGraph(java.util.List.of(
                "platform.signing ->", "platform.audit -> platform.signing")))).isNotEmpty();
        assertThat(signingViolations(declaredGraph(java.util.List.of(
                "platform.signing -> platform.audit", "platform.audit ->", "organization.administration -> platform.signing")))).isEmpty();
    }

    static Set<String> signingViolations(Map<String, Set<String>> declared) {
        Set<String> violations = new TreeSet<>();
        reachableFrom("platform.signing", declared).stream().filter(m -> !SIGNING_MAY_REACH.contains(m))
                .forEach(m -> violations.add("platform.signing reaches " + m));
        declared.forEach((module, targets) -> {
            if (module.startsWith("platform.") && targets.contains("platform.signing")) {
                violations.add(module + " depends on platform.signing");
            }
        });
        return violations;
    }

    private static Map<String, Set<String>> declaredGraph(java.util.List<String> lines) {
        Map<String, Set<String>> declared = new HashMap<>();
        for (String line : lines) {
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
        assertThat(identityUsesOfTheWebModule(ArchitectureRulesTest.CLASSES))
                .as(GuardMessage.of("D1",
                        "platform.identity uses platform.web beyond the NotFromQuery marker used from infrastructure.security (P2 recheck M-1)",
                        "remove the use of the other platform.web type, or use the NotFromQuery marker only in platform.identity.infrastructure.security"))
                .isEmpty();
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
