package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.SystemActors;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.yaml.snakeyaml.Yaml;

/** RULING-P2-C5: every name a job, a listener or an operations entry acts under is declared in architecture/system-actors.txt. */
class SystemActorCatalogueTest {
    static final Path CATALOGUE = Path.of("architecture/system-actors.txt");
    static final Path TEST_CATALOGUE = Path.of("src/test/resources/architecture/system-actors.txt");
    static final Path MAIN = Path.of("src/main/java");
    static final Path TESTS = Path.of("src/test/java");
    static final Path ASYNCAPI = Path.of("../docs/modernization/events/asyncapi.yaml");
    /** Old names the acceptance tests still use; the test agent renames them there, then the line and this entry go (P2 recheck L-2). */
    static final Set<String> ACCEPTANCE_NAMES_TO_RENAME = Set.of("organization.workforce.sync", "organization.workforce.test", "platform.integration.outbound");
    static final JavaClasses CLASSES = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.forcitis.one");
    static final Pattern ENTERED = Pattern.compile("WorkContext\\.enter\\([^;{]*?\"([^\"]+)\"\\)", Pattern.DOTALL);
    static final Pattern ACTOR_CONSTANT = Pattern.compile("static final String ACTOR = \"([^\"]+)\"");
    static final Pattern LOCK_NAME = Pattern.compile("@SchedulerLock\\(name = \"([^\"]+)\"");
    static final Pattern ACTOR_ANNOTATION = Pattern.compile("@SystemActorName\\(\"([^\"]+)\"\\)");
    static final Pattern ID_OF = Pattern.compile("SystemActors\\.idOf\\(\"([^\"]+)\"\\)");

    record Line(String name, UUID id, String module) {
    }

    static List<Line> catalogue() throws IOException {
        return catalogue(CATALOGUE);
    }

    static List<Line> catalogue(Path file) throws IOException {
        List<Line> lines = new ArrayList<>();
        for (String row : Files.readAllLines(file)) {
            if (row.isBlank() || row.startsWith("#")) {
                continue;
            }
            String[] fields = row.split("\\|");
            assertThat(fields).as(row).hasSize(4);
            lines.add(new Line(fields[0].strip(), UUID.fromString(fields[1].strip()), fields[2].strip()));
        }
        return lines;
    }

    static Set<String> declaredNames() throws IOException {
        Set<String> names = new HashSet<>();
        catalogue().forEach(line -> names.add(line.name()));
        return names;
    }

    @Test
    @DisplayName("RULING-P2-C5 the catalogue: each UUID is v5 of its name, names and UUIDs are unique, a name starts with its module, and the kernel reads the same lines")
    void theCatalogueIsConsistent() throws IOException {
        List<Line> lines = catalogue();
        Map<UUID, String> byId = new HashMap<>();
        Set<String> names = new HashSet<>();
        List<String> problems = new ArrayList<>();
        for (Line line : lines) {
            if (!names.add(line.name())) {
                problems.add(line.name() + " is declared twice");
            }
            if (byId.put(line.id(), line.name()) != null) {
                problems.add(line.id() + " is used twice");
            }
            if (!line.name().startsWith(line.module() + ".")) {
                problems.add(line.name() + " does not start with its module " + line.module());
            }
            if (!line.id().equals(SystemActors.idOf(line.name()))) {
                problems.add(line.name() + " is not v5 of its name");
            }
        }
        assertThat(problems).isEmpty();
        assertThat(lines).isNotEmpty();
    }

    @Test
    @DisplayName("RULING-P2-C5 every @SchedulerLock name and every @ApplicationModuleListener class's @SystemActorName is declared")
    void jobsAndListenersAreDeclared() throws IOException {
        Set<String> declared = declaredNames();
        List<String> missing = new ArrayList<>();
        Set<JavaClass> listeners = new HashSet<>();
        for (JavaClass type : CLASSES) {
            for (JavaMethod method : type.getMethods()) {
                if (method.isAnnotatedWith(SchedulerLock.class)) {
                    String name = method.getAnnotationOfType(SchedulerLock.class).name();
                    if (!declared.contains(name)) {
                        missing.add("job " + name + " (" + type.getSimpleName() + "." + method.getName() + ")");
                    }
                }
                if (method.isAnnotatedWith(ApplicationModuleListener.class)) {
                    listeners.add(type);
                }
            }
        }
        for (JavaClass listener : listeners) {
            if (!listener.isAnnotatedWith(SystemActorName.class)) {
                missing.add("listener " + listener.getName() + " has no @SystemActorName");
            } else if (!declared.contains(listener.getAnnotationOfType(SystemActorName.class).value())) {
                missing.add("listener " + listener.getName() + " acts as " + listener.getAnnotationOfType(SystemActorName.class).value());
            }
        }
        assertThat(listeners).isNotEmpty();
        assertThat(missing).as("names to declare in " + CATALOGUE).isEmpty();
    }

    @Test
    @DisplayName("RULING-P2-C5 every name written into WorkContext.enter, directly or through an ACTOR constant, is declared")
    void namesEnteredInCodeAreDeclared() throws IOException {
        List<String> missing = new ArrayList<>();
        assertThat(namesWritten(MAIN, List.of(ENTERED, ACTOR_CONSTANT), declaredNames(), missing)).isGreaterThan(3);
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("P2 recheck B-1 every name the tests act under (WorkContext.enter, ACTOR, @SchedulerLock, @SystemActorName, SystemActors.idOf) is in the main or the test catalogue")
    void namesInTheTestsAreDeclared() throws IOException {
        Set<String> declared = declaredNames();
        catalogue(TEST_CATALOGUE).forEach(line -> declared.add(line.name()));
        List<String> missing = new ArrayList<>();
        assertThat(namesWritten(TESTS, List.of(ENTERED, ACTOR_CONSTANT, LOCK_NAME, ACTOR_ANNOTATION, ID_OF), declared, missing)).isGreaterThan(10);
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("P2 recheck L-2 the test catalogue: each UUID is v5 of its name, no name is also in the main catalogue, and a name starts with test. or acceptance.")
    void theTestCatalogueCannotPassForProduction() throws IOException {
        Set<String> production = declaredNames();
        List<String> problems = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Line line : catalogue(TEST_CATALOGUE)) {
            if (!names.add(line.name())) {
                problems.add(line.name() + " is declared twice");
            }
            if (production.contains(line.name())) {
                problems.add(line.name() + " is also in " + CATALOGUE);
            }
            if (!line.id().equals(SystemActors.idOf(line.name()))) {
                problems.add(line.name() + " is not v5 of its name");
            }
            boolean testName = line.name().startsWith("test.") || line.name().startsWith("acceptance.");
            if (!testName && !ACCEPTANCE_NAMES_TO_RENAME.contains(line.name())) {
                problems.add(line.name() + " does not start with test. or acceptance.");
            }
        }
        assertThat(names).containsAll(ACCEPTANCE_NAMES_TO_RENAME);
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("P2 recheck M-1 x-system-actors in asyncapi.yaml lists the catalogue line by line")
    @SuppressWarnings("unchecked")
    void theEventCatalogueListsTheSameActors() throws IOException {
        Map<String, Object> composed;
        try (InputStream in = Files.newInputStream(ASYNCAPI)) {
            composed = new Yaml().load(in);
        }
        List<String> published = new ArrayList<>();
        for (Map<String, Object> actor : (List<Map<String, Object>>) composed.get("x-system-actors")) {
            published.add(actor.get("name") + " | " + actor.get("actorId") + " | " + actor.get("module") + " | " + actor.get("description"));
        }
        List<String> declared = new ArrayList<>();
        for (String row : Files.readAllLines(CATALOGUE)) {
            if (!row.isBlank() && !row.startsWith("#")) {
                declared.add(String.join(" | ", Stream.of(row.split("\\|")).map(String::strip).toList()));
            }
        }
        assertThat(published).as("run python3 docs/modernization/events/build_asyncapi.py").isEqualTo(declared);
    }

    private static int namesWritten(Path root, List<Pattern> patterns, Set<String> declared, List<String> missing) throws IOException {
        int seen = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("SystemActorCatalogueTest.java")
                    && !f.endsWith("SystemActorsTest.java") && !f.endsWith("SystemActorCheckTest.java")).toList()) {
                String source = Files.readString(file);
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(source);
                    while (matcher.find()) {
                        seen++;
                        if (!declared.contains(matcher.group(1))) {
                            missing.add(matcher.group(1) + " in " + file.getFileName());
                        }
                    }
                }
            }
        }
        return seen;
    }
}
