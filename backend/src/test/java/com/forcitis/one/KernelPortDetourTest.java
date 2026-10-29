package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A kernel port exists for a module that may not depend on the module answering it (ADR-0016 section 8). When every module that asks
 * already has an edge to the module that answers (architecture/module-dependencies.txt), the port is a detour around the context map:
 * the question belongs to the answering module's api (P2-H2).
 */
class KernelPortDetourTest {
    static final JavaClasses CLASSES = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.forcitis.one");
    static final String ROOT = "com.forcitis.one.";

    static String moduleOf(JavaClass type) {
        String[] parts = type.getPackageName().substring(ROOT.length()).split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : parts[0];
    }

    static Map<String, Set<String>> edges() throws IOException {
        Map<String, Set<String>> edges = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("architecture/module-dependencies.txt"))) {
            if (line.isBlank() || line.startsWith("#") || !line.contains("->")) {
                continue;
            }
            String[] sides = line.split("->", 2);
            Set<String> targets = new HashSet<>();
            for (String target : sides[1].split(",")) {
                if (!target.isBlank()) {
                    targets.add(target.strip());
                }
            }
            edges.put(sides[0].strip(), targets);
        }
        return edges;
    }

    static List<String> detours(JavaClasses classes, Map<String, Set<String>> edges) {
        List<String> found = new ArrayList<>();
        for (JavaClass port : classes) {
            if (!port.isInterface() || !port.getPackageName().equals(ROOT + "kernel")) {
                continue;
            }
            Set<String> answering = new TreeSet<>();
            Set<String> asking = new TreeSet<>();
            for (JavaClass type : classes) {
                if (type.getPackageName().startsWith(ROOT + "kernel")) {
                    continue;
                }
                if (!type.isInterface() && type.getAllRawInterfaces().contains(port)) {
                    answering.add(moduleOf(type));
                }
                for (JavaConstructor constructor : type.getConstructors()) {
                    if (constructor.getRawParameterTypes().contains(port)) {
                        asking.add(moduleOf(type));
                    }
                }
            }
            asking.removeAll(answering);
            if (answering.size() != 1 || asking.isEmpty()) {
                continue;
            }
            String answer = answering.iterator().next();
            if (asking.stream().allMatch(module -> edges.getOrDefault(module, Set.of()).contains(answer))) {
                found.add(port.getSimpleName() + ": asked by " + asking + ", answered by " + answer + ", and every asker may use " + answer + "'s api");
            }
        }
        return found;
    }

    @Test
    @DisplayName("P2-H2 kernel 端口的提问方与实现方之间已有依赖边时不得经 kernel 绕行，问题放进实现方的 api")
    void P2_H2_noKernelPortDetoursAnExistingEdge() throws IOException {
        assertThat(detours(CLASSES, edges()))
                .as(GuardMessage.of("P2-H2",
                        "a kernel port is a detour around an existing module dependency edge: every asking module may already use the answering module",
                        "delete the kernel port, put the question on the answering module's api and let the askers call it"))
                .isEmpty();
    }

    @Test
    @DisplayName("P2 recheck L-6 positive control: were every module allowed to use every other, CallerPermissions would be reported as a detour")
    void theGuardReportsADetourWhenTheEdgeExists() {
        Set<String> modules = new TreeSet<>();
        for (JavaClass type : CLASSES) {
            if (type.getPackageName().startsWith(ROOT) && !type.getPackageName().startsWith(ROOT + "kernel")) {
                modules.add(moduleOf(type));
            }
        }
        Map<String, Set<String>> everyEdge = new HashMap<>();
        modules.forEach(module -> everyEdge.put(module, modules));
        assertThat(detours(CLASSES, everyEdge)).anySatisfy(detour -> assertThat(detour).startsWith("CallerPermissions: "));
    }
}
