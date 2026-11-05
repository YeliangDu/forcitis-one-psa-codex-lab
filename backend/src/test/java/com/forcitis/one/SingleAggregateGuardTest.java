package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.aggregates.domain.NoopSampleRepository;
import com.forcitis.one.fixture.aggregates.domain.SampleRepository;
import com.forcitis.one.fixture.aggregates.domain.Samples;
import com.forcitis.one.fixture.doubles.AggregateWriteGuard;
import com.forcitis.one.fixture.doubles.TestEventEnvelopes;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import java.time.LocalDate;
import java.util.Arrays;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DDD-1: a transaction changes one aggregate; the few that cannot are listed with their reason, and the list names no use case that is gone. */
class SingleAggregateGuardTest {

    static final Path EXCEPTIONS = Path.of("architecture/multi-aggregate-exceptions.txt");

    @Test
    @DisplayName("DDD-1 every registered multi-aggregate transaction names an existing use case, at least two aggregates and a reason")
    void theRegisterIsCurrent() throws Exception {
        List<String> lines = Files.readAllLines(EXCEPTIONS).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        assertThat(lines).as(GuardMessage.of("CS-2.3", "the multi-aggregate register has no entries",
                "restore the entries in backend/architecture/multi-aggregate-exceptions.txt")).isNotEmpty();
        String fix = "edit backend/architecture/multi-aggregate-exceptions.txt so the line reads <use case class> | <Aggregate>+<Aggregate> | <reason>, "
                + "and delete the line when the use case no longer exists";
        for (String line : lines) {
            String[] parts = line.split("\\|");
            assertThat(parts).as(GuardMessage.of("CS-2.3", "register line '" + line + "' does not have three '|' separated fields", fix)).hasSize(3);
            try {
                assertThat(Class.forName(parts[0].trim(), false, getClass().getClassLoader()).getName()).isEqualTo(parts[0].trim());
            } catch (ClassNotFoundException gone) {
                throw new AssertionError(GuardMessage.of("CS-2.3", "register line '" + line + "' names a use case class that no longer exists", fix), gone);
            }
            assertThat(parts[1].trim().split("\\+")).as(GuardMessage.of("CS-2.3", "register line '" + line + "' names fewer than two aggregates", fix))
                    .hasSizeGreaterThan(1);
            assertThat(parts[2].trim()).as(GuardMessage.of("CS-2.3", "register line '" + line + "' has no reason", fix)).isNotBlank();
        }
    }

    @Test
    @DisplayName("DDD-1 injected: a set of aggregates outside the register is not allowed, one inside is")
    void anUnregisteredSetIsCaught() throws IOException {
        List<Set<String>> registered = AggregateWriteGuard.entries(Files.readAllLines(EXCEPTIONS));
        assertThat(registered).anyMatch(set -> set.containsAll(Set.of("Timesheet", "NumberingRule")));
        assertThat(registered).noneMatch(set -> set.containsAll(Set.of("ApprovalRequest", "Timesheet")));
    }

    private static List<String> transactionSaving(java.util.function.Consumer<AggregateWriteGuard> events, Object... aggregates) {
        AggregateWriteGuard guard = new AggregateWriteGuard();
        org.springframework.aop.aspectj.annotation.AspectJProxyFactory factory = new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(new NoopSampleRepository());
        factory.addAspect(guard);
        SampleRepository repository = factory.getProxy();
        AggregateWriteGuard.forget();
        TransactionSynchronizationManager.initSynchronization();
        try {
            for (Object aggregate : aggregates) {
                repository.save(aggregate);
            }
            assertThat(TransactionSynchronizationManager.getSynchronizations())
                    .as("the aspect must have intercepted save and registered its synchronization; none means it did not match, and the violations would be empty by silence")
                    .hasSize(1);
            events.accept(guard);
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCompletion(
                    TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        List<String> found = AggregateWriteGuard.violations();
        AggregateWriteGuard.forget();
        return found;
    }

    private static DomainEvent eventNaming(String... related) {
        EventEnvelope envelope = new TestEventEnvelopes().wrap(new EventDraft("cost.timesheet.TimesheetSubmitted", 1, "Timesheet", TestIds.uuid(7), 1,
                new LegalEntityId(TestIds.uuid(1)), LocalDate.of(2026, 10, 14),
                Arrays.stream(related).map(type -> new AggregateReference(type, TestIds.uuid(2))).toList()));
        return () -> envelope;
    }

    @Test
    @DisplayName("DDD-1 driven through the real aspect: two aggregates in one transaction outside the register are a violation")
    void theAspectCatchesAnUnregisteredPair() {
        List<String> found = transactionSaving(guard -> {
        }, new Samples.Alpha(), new Samples.Beta());

        assertThat(found).singleElement().asString().contains("Alpha").contains("Beta").contains("not in");
    }

    @Test
    @DisplayName("DDD-1 a registered pair must also be named by the events of its transaction in relatedAggregates; naming it passes, silence is a violation")
    void aRegisteredPairMustBeNamedByTheEvents() {
        Object timesheet = new Samples.Timesheet();
        Object counter = new Samples.NumberingRule();

        assertThat(transactionSaving(guard -> {
        }, timesheet, counter)).singleElement().asString().contains("NumberingRule").contains("relatedAggregates");
        assertThat(transactionSaving(guard -> guard.observe(eventNaming()), timesheet, counter)).hasSize(1);
        assertThat(transactionSaving(guard -> guard.observe(eventNaming("NumberingRule")), timesheet, counter)).isEmpty();
    }

    // DDD-1 / G-T1, whole surface and static: a @Transactional use case, with the application helpers it calls, writes through at most one aggregate repository.
    // The aspect above sees only save / insert in two modules' ITs. A repository method counts as a write unless its name starts like a read.
    static final java.util.regex.Pattern READ = java.util.regex.Pattern.compile("(find|get|load|list|exists|count|search|read|is|has|query|select|fetch|lookup|resolve|peek|contains|stream|all|by|view|check|verify|validate|current|latest|open|pending)[A-Z0-9_].*|(find|get|load|list|count|exists|all|stream|current|latest)");
    static final Path GAPS = Path.of("architecture/single-aggregate-gaps.txt");

    /** use case class -> the aggregate repositories one of its transactions writes, for those that write two or more. */
    static java.util.Map<String, java.util.Set<String>> multiRepositoryWriters(com.tngtech.archunit.core.domain.JavaClasses classes) {
        java.util.Map<String, java.util.Set<String>> found = new java.util.TreeMap<>();
        for (com.tngtech.archunit.core.domain.JavaClass type : classes) {
            for (com.tngtech.archunit.core.domain.JavaMethod entry : TransactionalUseCases.entries(type)) {
                java.util.Set<String> written = new java.util.TreeSet<>();
                TransactionalUseCases.reachableAccesses(entry).stream().filter(a -> a.owner().getSimpleName().endsWith("Repository") && a.owner().getPackageName().endsWith(".domain")
                        && !READ.matcher(a.name()).matches()).forEach(a -> written.add(a.owner().getSimpleName()));
                if (written.size() > 1) {
                    found.merge(type.getName(), written, (x, y) -> { x.addAll(y); return x; });
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("DDD-1 a @Transactional use case writes through at most one aggregate repository, unless registered; the gaps may only shrink")
    void useCasesWriteOneAggregate() throws IOException {
        java.util.Map<String, java.util.Set<String>> found = multiRepositoryWriters(ArchitectureRulesTest.CLASSES);
        List<String> registered = Files.readAllLines(EXCEPTIONS).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).map(l -> l.split("\\|")[0].trim()).toList();
        List<String> gaps = Files.readAllLines(GAPS).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        assertThat(found.keySet().stream().filter(c -> !registered.contains(c) && !gaps.contains(c)).map(c -> c + " writes " + found.get(c)).toList()).as(GuardMessage.of("DDD-1",
                "a @Transactional use case writes through two or more repositories (aggregates)", "split it into one transaction per aggregate joined by a domain event, or register it in multi-aggregate-exceptions.txt with a reason; never add it to single-aggregate-gaps.txt")).isEmpty();
        assertThat(gaps.stream().filter(c -> !found.containsKey(c)).toList()).as(GuardMessage.of("DDD-1",
                "backend/architecture/single-aggregate-gaps.txt lists a use case that no longer writes two repositories, or no longer exists", "delete the line (the list may only shrink)")).isEmpty();
        com.tngtech.archunit.core.domain.JavaClasses fixture = new com.tngtech.archunit.core.importer.ClassFileImporter().withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.forcitis.one.fixture.twoaggregates");
        assertThat(multiRepositoryWriters(fixture).keySet()).as("injected: the use case writing two repositories is caught, also through a helper and a method reference; the one writing one is not").containsExactly("com.forcitis.one.fixture.twoaggregates.application.WritesTwo", "com.forcitis.one.fixture.twoaggregates.application.WritesViaHelper");
    }
}
