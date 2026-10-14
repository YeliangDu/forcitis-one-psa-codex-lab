package com.forcitis.one;

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
