package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.*;

import com.forcitis.one.kernel.Uuids;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;

/**
 * Ground of the #960 tests (one-design DD on #960, issue comment 6529863330): the one-time initial migration of people who have no employee
 * version yet, run as a workforce operations command (operator and a different witness, nobody signed in), reading only this system's own
 * snapshot (employees, assignments, assignment_versions). The command and audit names are 暂名 agreed here.
 */
abstract class InitialVersionMigrationSupport extends PersonnelCommandSupport {

    /** 暂名 (DD section 2): the operation name of the command. */
    static final String MIGRATE_OPERATION = "migrate-initial-versions";
    /** 暂名 (DD section 5): one audit entry per run, counts only. */
    static final String AUDIT_MIGRATED = "organization.workforce.initial-versions.migrated";

    @Autowired
    private ApplicationContext beans;

    record Result(int exitCode, String out, String err) {
        String text() {
            return "exit " + exitCode + ", out: " + out + ", err: " + err;
        }
    }

    /** Runs a command line as operations run it: nobody signed in, exit code and both streams captured. */
    protected Result run(String... arguments) {
        SecurityContextHolder.clearContext();
        Object runner = null;
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name);
            if (type != null && ClassUtils.getUserClass(type).getName().equals(RUNNER_PACKAGE + "." + RUNNER_TYPE)) {
                runner = beans.getBean(name);
            }
        }
        if (runner == null) {
            throw new AssertionError("no bean of type " + RUNNER_PACKAGE + "." + RUNNER_TYPE);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            Method run = ClassUtils.getUserClass(runner).getDeclaredMethod(RUN_METHOD, ApplicationArguments.class, PrintStream.class, PrintStream.class);
            run.setAccessible(true);
            Object code = run.invoke(runner, new DefaultApplicationArguments(arguments), new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8));
            return new Result((Integer) code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } catch (InvocationTargetException failed) {
            throw new AssertionError("the command must answer an exit code, not throw: " + failed.getCause(), failed.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(broken);
        }
    }

    /** The migration with two existing employees as operator and witness; null leaves the option out. */
    protected Result migrate(UUID operator, UUID witness) {
        List<String> line = new ArrayList<>(List.of("--" + OPERATION_OPTION + "=" + MIGRATE_OPERATION));
        if (operator != null) {
            line.add("--" + OPERATOR_OPTION + "=" + operator);
        }
        if (witness != null) {
            line.add("--" + WITNESS_OPTION + "=" + witness);
        }
        return run(line.toArray(String[]::new));
    }

    protected Result migrate() throws Exception {
        return migrate(employed(REGULAR), employed(REGULAR));
    }

    // ---- the snapshot as the sync left it ----

    /** A person only the snapshot knows (no version of any table), with the fee-earner flag and the reporting line given. */
    protected UUID snapshotPerson(boolean feeEarner, UUID reportsTo) throws Exception {
        UUID id = newEmployeeId();
        employee(id, unit, reportsTo);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set is_fee_earner = ? where id = ?", feeEarner, stored(id));
        }
        return id;
    }

    /** The snapshot says separated, observed by the sync at the instant (null: no separated_at). */
    protected void snapshotSeparated(UUID person, Instant separatedAt) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set employment_status = 'SEPARATED', separated_at = ? where id = ?",
                    separatedAt == null ? null : OffsetDateTime.ofInstant(separatedAt, ZoneOffset.UTC), stored(person));
        }
    }

    /** A sync-written (MIGRATION) assignment version of the person's unit over [from, to), as OrgHistory writes them. */
    protected void syncAssignmentVersion(UUID person, LocalDate from, LocalDate to) throws Exception {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into assignment_versions (assignment_version_id, employee_id, effective_from_date, effective_to_date, org_unit_id,
                      change_channel_kind, closed_at, created_at, updated_at) values (?, ?, ?, ?, ?, 'MIGRATION', ?, ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(person), from, to, stored(unit), to == null ? null : at, at, at);
        }
    }

    // ---- reading ----

    /** Every row of both version tables, all people, in a fixed order: a rerun must leave this list exactly as it was. */
    protected List<Map<String, Object>> everyVersionRow() throws Exception {
        List<Map<String, Object>> all = new ArrayList<>();
        all.addAll(rows("select * from " + EMPLOYEE_VERSIONS + " order by employee_version_id"));
        all.addAll(rows("select * from " + ASSIGNMENT_VERSIONS + " order by assignment_version_id"));
        return all;
    }

    protected long versionRowCount() throws Exception {
        return (long) rows("select (select count(*) from " + EMPLOYEE_VERSIONS + ") + (select count(*) from " + ASSIGNMENT_VERSIONS + ") as n").get(0).get("n");
    }

    /** Domain events in the publication registry (both tables): the migration publishes none (checklist 14.2 M7). */
    protected long publishedEvents() throws Exception {
        try (Connection registry = schemaConnection("platform_integration")) {
            return (long) query(registry, "select (select count(*) from event_publication) + (select count(*) from event_publication_archive) as n").get(0).get("n");
        }
    }
}
