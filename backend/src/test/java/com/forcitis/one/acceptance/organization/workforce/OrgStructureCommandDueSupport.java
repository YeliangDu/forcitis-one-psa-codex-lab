package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.KIND_CHANGE;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

/**
 * Ground of the #1146 tests (the due job of org-structure commands; ADR-0068 item 1.8 and item 15.2, #910 ruling point 3, note
 * org-structure-commands.md section 5 rules 4 and 6), on the world of {@link OrgCountersignSupport}. The job has no agreed name on the card; the
 * tests look for a bean whose class is named {@link #DUE_JOB_TYPE} under organization.workforce with a public no-argument
 * {@link #DUE_JOB_METHOD}() - the counterpart of PersonnelCommandDueJob (PersonnelCommandNames) - and fail, naming the jobs they did see, if none
 * is there. Rename here if lead settles another name.
 */
abstract class OrgStructureCommandDueSupport extends OrgCountersignSupport {

    static final String DUE_JOB_TYPE = "OrgStructureCommandDueJob";
    static final String DUE_JOB_METHOD = "run";
    static final String DEADLINE_JOB_TYPE = "AttributionCountersignDeadlineJob";
    static final String DEADLINE_JOB_METHOD = "runOnce";
    static final String PACKAGE = "com.forcitis.one.organization.workforce";

    /** The effective date of the commands of a test: after TODAY, so that they are entered as PENDING. */
    static final LocalDate DAY = TODAY.plusDays(10);

    protected Logger rootLogger;
    protected ListAppender<ILoggingEvent> logged;

    @BeforeEach
    void captureTheLog() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logged = new ListAppender<>();
        logged.start();
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheLogAndTheClock() {
        rootLogger.detachAppender(logged);
        setToday(TODAY);
    }

    // ---- worlds ----

    /** A unit with two people who inherit E1 from the chain, and an ASSIGN_LEGAL_ENTITY to E2 on the day, countersigned by both heads: PENDING. */
    protected Countersigned countersignedAssignment(LocalDate day) throws Exception {
        UUID unit = seedUnit(topE1);
        UUID first = inheritingIn(unit);
        UUID second = inheritingIn(unit);
        UUID command = assignLegalEntity(unit, day, e2);
        approve(headE1, command, KIND_CHANGE, e1);
        approve(headE2, command, KIND_CHANGE, e2);
        return new Countersigned(unit, first, second, command);
    }

    record Countersigned(UUID unit, UUID first, UUID second, UUID command) {
    }

    // ---- the jobs ----

    /** Runs the due job once at the day with nobody signed in (a system run). A missing bean or method fails the test, it is never skipped. */
    protected void runDueJobOn(LocalDate day) {
        setToday(day);
        runDueJob();
    }

    protected void runDueJob() {
        invoke(DUE_JOB_TYPE, DUE_JOB_METHOD);
    }

    protected void runDeadlineJob() {
        invoke(DEADLINE_JOB_TYPE, DEADLINE_JOB_METHOD);
    }

    private void invoke(String typeName, String methodName) {
        SecurityContextHolder.clearContext();
        List<String> similar = new ArrayList<>();
        for (String name : springContext.getBeanDefinitionNames()) {
            Class<?> type = springContext.getType(name);
            if (type == null) {
                continue;
            }
            Class<?> user = ClassUtils.getUserClass(type);
            if (!user.getSimpleName().equals(typeName) || !user.getName().startsWith(PACKAGE)) {
                if (user.getName().startsWith(PACKAGE) && user.getSimpleName().matches(".*(Job|Due).*")) {
                    similar.add(user.getName());
                }
                continue;
            }
            Method run;
            try {
                run = user.getMethod(methodName);
            } catch (NoSuchMethodException missing) {
                throw new AssertionError(user.getName() + " has no public no-argument method " + methodName + "()", missing);
            }
            try {
                run.invoke(springContext.getBean(name));
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(failure.getCause());
            } catch (IllegalAccessException broken) {
                throw new AssertionError("cannot call " + run, broken);
            }
            return;
        }
        throw new AssertionError("no bean of a type named " + typeName + " under " + PACKAGE + " (#1146 due job of org-structure commands); jobs seen: " + similar);
    }

    /** Both bodies start together on two threads; the first failure of either is rethrown after both ended. */
    protected void overlap(Runnable first, Runnable second) throws Exception {
        CyclicBarrier together = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (Runnable body : List.of(first, second)) {
                runs.add(pool.submit(() -> {
                    together.await(30, TimeUnit.SECONDS);
                    body.run();
                    return null;
                }));
            }
            Exception failure = null;
            for (Future<?> run : runs) {
                try {
                    run.get(120, TimeUnit.SECONDS);
                } catch (ExecutionException ended) {
                    failure = failure != null ? failure : ended;
                }
            }
            if (failure != null) {
                throw failure;
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- reads ----

    /** ERROR log lines that start with ALERT (the house convention), since the capture began. */
    protected List<String> alerts() {
        return logged.list.stream().filter(line -> line.getLevel() == Level.ERROR && line.getFormattedMessage().startsWith("ALERT"))
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    protected Object appliedAt(UUID orgCommand) throws SQLException {
        return orgCommand(orgCommand).get("applied_at");
    }

    // ---- a fault that shows at commit ----

    /** A constraint trigger that fails, when the transaction commits, the update that makes this one org command EFFECTIVE. */
    protected void failCommitOfEffective(UUID orgCommand) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); Statement statement = workforce.createStatement()) {
            statement.execute("""
                    create function f1_1146_fail_commit() returns trigger language plpgsql as $$
                    begin
                        if new.org_structure_command_id = '%s'::uuid and new.status = 'EFFECTIVE' then
                            raise exception 'f1-1146 forced failure at commit';
                        end if;
                        return new;
                    end $$""".formatted(orgCommand));
            statement.execute("create constraint trigger f1_1146_fail_commit after update on org_structure_commands deferrable initially deferred"
                    + " for each row execute function f1_1146_fail_commit()");
        }
    }

    protected void removeFailure() throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); Statement statement = workforce.createStatement()) {
            statement.execute("drop trigger if exists f1_1146_fail_commit on org_structure_commands");
            statement.execute("drop function if exists f1_1146_fail_commit()");
        }
    }
}
