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
}
