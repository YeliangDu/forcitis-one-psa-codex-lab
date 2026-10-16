package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.Uuids;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0033 section 1 item 9 and guard 2: an aggregate root gets its id when it is built, by the application with the injected IdGenerator, never by the
 * repository that stores it; and nothing but kernel.Uuids makes a random UUID. The exceptions are listed with a reason and the card that ends them; a
 * line whose code no longer breaks the rule fails the test, so the exception cannot outlive its cause.
 */
class IdMakingGuardTest {
    static final Path REPOSITORY_EXCEPTIONS = Path.of("architecture/repository-id-exceptions.txt");
    static final Path RANDOM_UUID_EXCEPTIONS = Path.of("architecture/random-uuid-exceptions.txt");

    static Set<String> read(Path file) throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                int hash = line.indexOf('#');
                assertThat(hash).as("a reason and the ending card after # on: " + line).isPositive();
                assertThat(line.substring(hash + 1)).as("reason of " + line).isNotBlank();
                entries.add(line.substring(0, hash).strip());
            }
        }
        return entries;
    }

    /** Methods of repositories that return an aggregate root and ask the IdGenerator for an id on the way: the root's id is made at the repository. */
    static List<String> repositoryProblems(JavaClasses classes, Predicate<JavaClass> repository, Set<String> excepted) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JavaClass type : classes) {
            if (!repository.test(type)) {
                continue;
            }
            for (JavaMethod method : type.getMethods()) {
                boolean root = method.getRawReturnType().isAssignableTo(AggregateRoot.class) && !method.getRawReturnType().isEquivalentTo(AggregateRoot.class);
                boolean makesAnId = method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTargetOwner().isAssignableTo(IdGenerator.class) && call.getName().equals("next"));
                if (root && makesAnId) {
                    String name = type.getName() + "." + method.getName();
                    seen.add(name);
                    if (!excepted.contains(name)) {
                        problems.add(name + " returns an aggregate root and makes its id: the id is made when the aggregate is built (ADR-0033 section 1 item 9)");
                    }
                }
            }
        }
        excepted.stream().filter(e -> !seen.contains(e)).forEach(e -> problems.add(e + ": listed in " + REPOSITORY_EXCEPTIONS + " but it no longer breaks the rule; delete the line"));
        return problems;
    }

    /**
     * The id of an aggregate root is final: a repository cannot hand a root its id later, whether in a method that returns the root, a void save or a
     * private helper. A field is taken for the root's own id when it is called id, is called after the root, or is called after its own type (userId of
     * UserId); a typed reference to another aggregate (releasedByRunId) is not. A root whose id is named otherwise is not seen here and stays under the
     * repository rule above.
     */
    static List<String> mutableRootIdProblems(JavaClasses classes) {
        List<String> problems = new ArrayList<>();
        for (JavaClass type : classes) {
            if (!type.isAssignableTo(AggregateRoot.class) || type.isEquivalentTo(AggregateRoot.class)) {
                continue;
            }
            String named = decapitalise(type.getSimpleName()) + "Id";
            type.getFields().stream()
                    .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC) && !field.getModifiers().contains(JavaModifier.FINAL))
                    .filter(field -> field.getName().equals("id") || field.getName().equals(named)
                            || field.getRawType().getSimpleName().endsWith("Id") && field.getName().equals(decapitalise(field.getRawType().getSimpleName())))
                    .forEach(field -> problems.add(type.getName() + "." + field.getName() + " is an id that is not final: the id of a root is set when it is built, never later (ADR-0033 section 1 item 9)"));
        }
        return problems;
    }

    private static String decapitalise(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** Classes other than kernel.Uuids that call UUID.randomUUID. */
    static List<String> randomUuidProblems(JavaClasses classes, Set<String> excepted) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JavaClass type : classes) {
            if (type.isEquivalentTo(Uuids.class)) {
                continue;
            }
            boolean calls = type.getMethodCallsFromSelf().stream()
                    .anyMatch(call -> call.getTargetOwner().isEquivalentTo(UUID.class) && call.getName().equals("randomUUID"));
            if (calls) {
                seen.add(type.getName());
                if (!excepted.contains(type.getName())) {
                    problems.add(type.getName() + " calls UUID.randomUUID: ids come from IdGenerator, other random values from kernel.Uuids");
                }
            }
        }
        excepted.stream().filter(e -> !seen.contains(e)).forEach(e -> problems.add(e + ": listed in " + RANDOM_UUID_EXCEPTIONS + " but it no longer calls UUID.randomUUID; delete the line"));
        return problems;
    }

    @Test
    @DisplayName("no repository makes the id of the aggregate root it returns (ADR-0033 section 1 item 9)")
    void repositoriesDoNotMakeRootIds() throws IOException {
        assertThat(repositoryProblems(ArchitectureRulesTest.CLASSES, type -> type.getPackageName().contains(".infrastructure.persistence"), read(REPOSITORY_EXCEPTIONS))).isEmpty();
    }

    @Test
    @DisplayName("the id of every aggregate root is final, so no repository can make it afterwards")
    void rootIdsAreFinal() {
        assertThat(mutableRootIdProblems(ArchitectureRulesTest.CLASSES)).isEmpty();
    }

    @Test
    @DisplayName("nothing but kernel.Uuids calls UUID.randomUUID in production code")
    void onlyUuidsMakesRandomUuids() throws IOException {
        assertThat(randomUuidProblems(ArchitectureRulesTest.CLASSES, read(RANDOM_UUID_EXCEPTIONS))).isEmpty();
    }

    static final class SampleRoot extends AggregateRoot {
        SampleRoot() {
            super(0, 0);
        }
    }

    static final class MakesRootIdAtTheRepository {
        private final IdGenerator ids = null;

        SampleRoot insert() {
            ids.next();
            return new SampleRoot();
        }

        SampleRoot find() {
            return new SampleRoot();
        }

        void insertChild() {
            ids.next();
        }
    }

    static final class RootWithAnIdTheRepositoryFills extends AggregateRoot {
        private UUID id;

        RootWithAnIdTheRepositoryFills() {
            super(0, 0);
        }
    }

    static final class RootWithAFinalId extends AggregateRoot {
        private final UUID id = null;
        private String status;

        RootWithAFinalId() {
            super(0, 0);
        }
    }

    static final class VoidSaveFillsTheRootId {
        private final IdGenerator ids = null;

        void save(RootWithAnIdTheRepositoryFills root) {
            root.id = ids.next();
        }

        void save2(RootWithAnIdTheRepositoryFills root) {
            fill(root);
        }

        private void fill(RootWithAnIdTheRepositoryFills root) {
            root.id = ids.next();
        }
    }

    static final class CallsRandomUuid {
        UUID make() {
            return UUID.randomUUID();
        }
    }

    @Test
    @DisplayName("injected: a root whose id is not final is caught however the repository fills it (void save, private helper); a final id and other mutable fields are not")
    void theRootIdGuardCatchesInjectedSamples() {
        JavaClasses sample = new ClassFileImporter().importClasses(RootWithAnIdTheRepositoryFills.class, RootWithAFinalId.class, VoidSaveFillsTheRootId.class);

        assertThat(mutableRootIdProblems(sample)).containsExactly(RootWithAnIdTheRepositoryFills.class.getName()
                + ".id is an id that is not final: the id of a root is set when it is built, never later (ADR-0033 section 1 item 9)");
    }

    @Test
    @DisplayName("injected: a repository method that returns a root and makes an id, a class that calls randomUUID, and two stale exceptions are caught; child rows and finders are not")
    void theGuardsCatchInjectedSamples() {
        JavaClasses sample = new ClassFileImporter().importClasses(MakesRootIdAtTheRepository.class, CallsRandomUuid.class, SampleRoot.class);
        String repository = MakesRootIdAtTheRepository.class.getName();

        assertThat(repositoryProblems(sample, type -> type.getName().equals(repository), Set.of()))
                .containsExactly(repository + ".insert returns an aggregate root and makes its id: the id is made when the aggregate is built (ADR-0033 section 1 item 9)");
        assertThat(repositoryProblems(sample, type -> type.getName().equals(repository), Set.of(repository + ".insert", repository + ".gone")))
                .containsExactly(repository + ".gone: listed in " + REPOSITORY_EXCEPTIONS + " but it no longer breaks the rule; delete the line");

        assertThat(randomUuidProblems(sample, Set.of())).containsExactly(CallsRandomUuid.class.getName()
                + " calls UUID.randomUUID: ids come from IdGenerator, other random values from kernel.Uuids");
        assertThat(randomUuidProblems(sample, Set.of(CallsRandomUuid.class.getName(), "x.Gone"))).containsExactly("x.Gone: listed in " + RANDOM_UUID_EXCEPTIONS
                + " but it no longer calls UUID.randomUUID; delete the line");
    }
}
