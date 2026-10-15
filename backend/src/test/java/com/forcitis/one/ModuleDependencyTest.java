package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.web.NotFromQuery;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
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
    @DisplayName("P2 recheck M-1 platform.identity uses platform.web only for the NotFromQuery marker, and only from infrastructure.security")
    void identityUsesTheWebModuleOnlyForTheMarker() {
        Set<String> violations = new TreeSet<>();
        for (JavaClass c : ArchitectureRulesTest.CLASSES) {
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
        assertThat(violations).isEmpty();
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
