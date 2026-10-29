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
    static final Path ASYNCAPI = Path.of("../docs/architecture/events/asyncapi.yaml");
    static final JavaClasses CLASSES = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.forcitis.one");
    static final Pattern ENTERED = Pattern.compile("WorkContext\\.enter\\([^;{]*?\"([^\"]+)\"\\)", Pattern.DOTALL);
    static final Pattern ACTOR_CONSTANT = Pattern.compile("static final String ACTOR = \"([^\"]+)\"");
    /** Undeclared on purpose: tests that a name not in the catalogue is refused use these prefixes, and only tests may. */
    static final List<String> COUNTEREXAMPLE_PREFIXES = List.of("test.no-such-", "no.such.");
    static final Pattern ANY_ACTOR_CONSTANT = Pattern.compile("static final String ACTOR = ([^;]+);");
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
            assertThat(fields).as(GuardMessage.of("RULING-P2-C5", "a line of a system actor catalogue does not have four columns: " + row,
                    "write it as <name> | <uuid> | <module> | <description>")).hasSize(4);
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
        assertThat(problems).as(GuardMessage.of("RULING-P2-C5", "a line of architecture/system-actors.txt is duplicated, outside its module or has an id that is not the v5 UUID of its name",
                "set the id to SystemActors.idOf(<name>), prefix the name with its module and a dot, and delete duplicates")).isEmpty();
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
        assertThat(missing).as(GuardMessage.of("RULING-P2-C5", "a job or listener acts under a name that is not declared",
                "add a line <name> | <v5 uuid of the name> | <module> | <description> to " + CATALOGUE + ", and give each listener class a @SystemActorName of a declared name")).isEmpty();
    }

    @Test
    @DisplayName("RULING-P2-C5 every name written into WorkContext.enter, directly or through an ACTOR constant, is declared")
    void namesEnteredInCodeAreDeclared() throws IOException {
        List<String> missing = new ArrayList<>();
        assertThat(namesWritten(MAIN, List.of(ENTERED, ACTOR_CONSTANT, ACTOR_ANNOTATION), declaredNames(), missing)).isGreaterThan(3);
        assertThat(missing).as(GuardMessage.of("RULING-P2-C5", "a name written into WorkContext.enter is not declared",
                "add a line <name> | <v5 uuid of the name> | <module> | <description> to " + CATALOGUE)).isEmpty();
    }

    @Test
    @DisplayName("P2 second recheck L-4 an ACTOR constant is read from the class's own @SystemActorName, so the start-up check covers it")
    void actorConstantsComeFromTheAnnotation() throws IOException {
        List<String> literal = new ArrayList<>();
        int seen = 0;
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String type = file.getFileName().toString().replace(".java", "");
                Matcher matcher = ANY_ACTOR_CONSTANT.matcher(Files.readString(file));
                while (matcher.find()) {
                    seen++;
                    if (!matcher.group(1).equals(type + ".class.getAnnotation(SystemActorName.class).value()")) {
                        literal.add(type + ": ACTOR = " + matcher.group(1));
                    }
                }
            }
        }
        assertThat(seen).isGreaterThanOrEqualTo(3);
        assertThat(literal).as(GuardMessage.of("RULING-P2-C5 (L-4)", "an ACTOR constant is not read from the class own @SystemActorName",
                "write ACTOR = <Class>.class.getAnnotation(SystemActorName.class).value() so the start-up check covers it")).isEmpty();
    }

    /** Actor expressions that are not a name but pass one on: the listener or job the aspect wraps, and delivery inside or outside a job. */
    static final Map<String, Set<String>> PASSED_ON = Map.of(
            "UnitOfWorkAspect.java", Set.of("handler", "job"),
            "DeliverWecomCards.java", Set.of("WorkContext.systemActor().orElse(\"platform.integration.wecom.delivery\")"));

    /** The third argument of every WorkContext.enter call in the source, whitespace collapsed. */
    static List<String> actorArguments(String source) {
        List<String> found = new ArrayList<>();
        int from = 0;
        while ((from = source.indexOf("WorkContext.enter(", from)) >= 0) {
            int i = from + "WorkContext.enter(".length();
            int depth = 0;
            boolean quoted = false;
            List<String> arguments = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (; i < source.length(); i++) {
                char c = source.charAt(i);
                if (c == '"' && source.charAt(i - 1) != '\\') {
                    quoted = !quoted;
                } else if (!quoted && (c == '(' || c == '{')) {
                    depth++;
                } else if (!quoted && (c == ')' || c == '}')) {
                    if (depth == 0) {
                        break;
                    }
                    depth--;
                } else if (!quoted && c == ',' && depth == 0) {
                    arguments.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            }
            arguments.add(current.toString());
            found.add(arguments.size() == 3 ? arguments.get(2).strip().replaceAll("\\s+", " ") : "<" + arguments.size() + " arguments>");
            from = i;
        }
        return found;
    }

    static boolean namedPlainly(String argument, String file) {
        return argument.matches("\"[^\"]+\"") || argument.equals("ACTOR") || argument.equals("null")
                || PASSED_ON.getOrDefault(file, Set.of()).contains(argument);
    }

    @Test
    @DisplayName("P2 second recheck L-1 the actor given to WorkContext.enter is a literal, ACTOR, null or a listed pass-on, so the catalogue checks see every name")
    void actorsAreNamedPlainly() throws IOException {
        List<String> hidden = new ArrayList<>();
        Map<String, Integer> passedOn = new HashMap<>();
        int seen = 0;
        for (Path root : List.of(MAIN, TESTS)) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("SystemActorCatalogueTest.java")).toList()) {
                    String name = file.getFileName().toString();
                    for (String argument : actorArguments(Files.readString(file))) {
                        seen++;
                        if (!namedPlainly(argument, name)) {
                            hidden.add(name + ": " + argument);
                        } else if (PASSED_ON.getOrDefault(name, Set.of()).contains(argument)) {
                            passedOn.merge(name, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        assertThat(seen).isGreaterThan(10);
        assertThat(hidden).as(GuardMessage.of("RULING-P2-C5 (L-1)", "the actor given to WorkContext.enter is held in a variable or computed, so the catalogue check cannot see the name",
                "pass a string literal, the ACTOR constant or null; a real pass-on goes into PASSED_ON of SystemActorCatalogueTest with its file")).isEmpty();
        assertThat(passedOn).as(GuardMessage.of("RULING-P2-C5 (L-1)", "a listed pass-on of an actor is no longer used", "delete it from PASSED_ON of SystemActorCatalogueTest")).containsOnlyKeys(PASSED_ON.keySet());
    }

    @Test
    @DisplayName("P2 second recheck L-1 negative sample: an actor held in a variable or computed is reported")
    void anActorInAVariableIsReported() {
        String sample = """
                try (var a = WorkContext.enter(id, null, "test.job")) { }
                try (var b = WorkContext.enter(Uuids.timeOrdered(clock), WorkContext.causationId().orElse(null), ACTOR)) { }
                try (var c = WorkContext.enter(id, null, name)) { }
                try (var d = WorkContext.enter(id, null, "test." + kind)) { }
                try (var e = WorkContext.enter(id, null, handler)) { }
                """;
        List<String> arguments = actorArguments(sample);
        assertThat(arguments).containsExactly("\"test.job\"", "ACTOR", "name", "\"test.\" + kind", "handler");
        assertThat(arguments.stream().filter(argument -> !namedPlainly(argument, "Sample.java"))).containsExactly("name", "\"test.\" + kind", "handler");
        assertThat(namedPlainly("handler", "UnitOfWorkAspect.java")).isTrue();
    }

    @Test
    @DisplayName("P2 recheck B-1 every name the tests act under (WorkContext.enter, ACTOR, @SchedulerLock, @SystemActorName, SystemActors.idOf) is in the main or the test catalogue")
    void namesInTheTestsAreDeclared() throws IOException {
        Set<String> declared = declaredNames();
        catalogue(TEST_CATALOGUE).forEach(line -> declared.add(line.name()));
        List<String> missing = new ArrayList<>();
        assertThat(namesWritten(TESTS, List.of(ENTERED, ACTOR_CONSTANT, LOCK_NAME, ACTOR_ANNOTATION, ID_OF), declared, missing)).isGreaterThan(10);
        assertThat(missing).as(GuardMessage.of("RULING-P2-C5 (B-1)", "a test acts under a name that is in neither the main nor the test catalogue",
                "add the name to the test catalogue (a test. or acceptance. name) with the v5 UUID of the name, or use a declared name")).isEmpty();
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
            if (!testName) {
                problems.add(line.name() + " does not start with test. or acceptance.");
            }
        }
        assertThat(problems).as(GuardMessage.of("RULING-P2-C5 (L-2)", "a line of the test catalogue is duplicated, is also in the main catalogue, is not the v5 UUID of its name, or does not start with test. or acceptance.",
                "fix the line in the test catalogue; a production name never goes there")).isEmpty();
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
        assertThat(published).as(GuardMessage.of("RULING-P2-C5 (M-1)", "x-system-actors in asyncapi.yaml does not list backend/architecture/system-actors.txt line by line",
                "run python3 scripts/codegen/build_asyncapi.py and commit the result")).isEqualTo(declared);
    }

    private static int namesWritten(Path root, List<Pattern> patterns, Set<String> declared, List<String> missing) throws IOException {
        int seen = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(source);
                    while (matcher.find()) {
                        seen++;
                        boolean counterexample = root.equals(TESTS) && COUNTEREXAMPLE_PREFIXES.stream().anyMatch(matcher.group(1)::startsWith);
                        if (!declared.contains(matcher.group(1)) && !counterexample) {
                            missing.add(matcher.group(1) + " in " + file.getFileName());
                        }
                    }
                }
            }
        }
        return seen;
    }
}
