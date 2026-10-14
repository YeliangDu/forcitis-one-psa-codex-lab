package com.forcitis.one.fixture.doubles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Counts, per transaction, the kinds of aggregate the repositories saved. More than one kind is a violation unless the set is a registered
 * exception (architecture/multi-aggregate-exceptions.txt). The integration tests that import it read the violations after each test.
 */
@Aspect
@Component
public class AggregateWriteGuard {

    static final Path EXCEPTIONS = Path.of("architecture/multi-aggregate-exceptions.txt");
    private static final List<String> VIOLATIONS = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Set<String>> savedInTransaction = new ThreadLocal<>();
    private final ThreadLocal<Set<String>> namedByEvents = new ThreadLocal<>();

    public static List<String> violations() {
        return List.copyOf(VIOLATIONS);
    }

    public static void forget() {
        VIOLATIONS.clear();
    }

    @Around("execution(* com.forcitis.one..domain.*Repository.save(..)) || execution(* com.forcitis.one..domain.*Repository.insert(..))")
    Object saved(ProceedingJoinPoint call) throws Throwable {
        Object result = call.proceed();
        if (call.getArgs().length > 0 && TransactionSynchronizationManager.isSynchronizationActive()) {
            if (savedInTransaction.get() == null) {
                Set<String> kinds = new LinkedHashSet<>();
                savedInTransaction.set(kinds);
                Set<String> named = new LinkedHashSet<>();
                namedByEvents.set(named);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        savedInTransaction.remove();
                        namedByEvents.remove();
                        if (kinds.size() > 1 && !registered(kinds)) {
                            VIOLATIONS.add("a transaction saved " + new TreeSet<>(kinds) + " and that set is not in " + EXCEPTIONS);
                        } else if (kinds.size() > 1 && !named.containsAll(kinds)) {
                            Set<String> missing = new TreeSet<>(kinds);
                            missing.removeAll(named);
                            VIOLATIONS.add("a transaction saved " + new TreeSet<>(kinds) + " but its events do not name " + missing
                                    + " as the aggregate or in relatedAggregates (DDD-1)");
                        }
                    }
                });
            }
            savedInTransaction.get().add(call.getArgs()[0].getClass().getSimpleName());
        }
        return result;
    }

    /** The aggregates the events of the running transaction name: their own, and the ones they say were touched together. */
    @org.springframework.context.event.EventListener
    public void observe(com.forcitis.one.kernel.event.DomainEvent event) {
        Set<String> named = namedByEvents.get();
        if (named != null) {
            named.add(event.envelope().aggregateType());
            event.envelope().relatedAggregates().forEach(related -> named.add(related.aggregateType()));
        }
    }

    static boolean registered(Set<String> aggregates) {
        try {
            return entries(Files.readAllLines(EXCEPTIONS)).stream().anyMatch(set -> set.containsAll(aggregates));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + EXCEPTIONS, e);
        }
    }

    /** The aggregate sets of the registered lines. */
    public static List<Set<String>> entries(List<String> lines) {
        List<Set<String>> sets = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|");
            sets.add(new LinkedHashSet<>(Arrays.stream(parts[1].trim().split("\\+")).map(String::trim).toList()));
        }
        return sets;
    }
}
