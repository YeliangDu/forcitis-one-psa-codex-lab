package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
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
 * Ground of the #877 tests (ADR-0068 item 2.4): the zero-administrator path. The scenario is the one the interface refuses (ADR-0068 item 2.3,
 * line 90): the only available super administrator is separated, so the separation is HELD, and releasing it would leave 0. The command runs
 * as operations run it: no signed-in caller (the security context is cleared before every run), operator and witness are two employees named on
 * the command line, never a session. Holds are made by the real path of {@link SeparationHoldSupport}.
 */
abstract class SeparationHoldOperationSupport extends SeparationHoldSupport {

    @Autowired
    private ApplicationContext beans;

    /** What the command printed and answered. */
    record Result(int exitCode, String out, String err) {
        String text() {
            return "exit " + exitCode + ", out: " + out + ", err: " + err;
        }
    }

    // ---- running ----

    private Object runner() {
        List<String> seen = new ArrayList<>();
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name);
            if (type == null) {
                continue;
            }
            Class<?> user = ClassUtils.getUserClass(type);
            if (user.getSimpleName().equals(RUNNER_TYPE)) {
                seen.add(user.getName());
                if (user.getPackageName().equals(RUNNER_PACKAGE)) {
                    return beans.getBean(name);
                }
            }
        }
        throw new AssertionError("ADR-0068 item 2.4: no bean of type " + RUNNER_PACKAGE + "." + RUNNER_TYPE + " (same-named types elsewhere: " + seen + ")");
    }

    /** Runs the command line as an operations runner would: nobody signed in, the exit code and both streams captured. */
    protected Result run(String... arguments) {
        SecurityContextHolder.clearContext();
        Object runner = runner();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            Method run = ClassUtils.getUserClass(runner).getDeclaredMethod(RUN_METHOD, ApplicationArguments.class, PrintStream.class, PrintStream.class);
            run.setAccessible(true);
            Object code = run.invoke(runner, new DefaultApplicationArguments(arguments), new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8));
            return new Result((Integer) code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } catch (NoSuchMethodException missing) {
            throw new AssertionError(RUNNER_TYPE + " has no " + RUN_METHOD + "(ApplicationArguments, PrintStream, PrintStream) answering the exit code", missing);
        } catch (InvocationTargetException failed) {
            throw new AssertionError("the command must answer an exit code (1 refused, 2 wrong usage), not throw: " + failed.getCause(), failed.getCause());
        } catch (IllegalAccessException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** A command line; a null value leaves the option out altogether. */
    protected static String[] line(UUID hold, String reason, Object operator, Object witness) {
        List<String> line = new ArrayList<>(List.of("--" + OPERATION_OPTION + "=" + COMMAND_NAME));
        add(line, HOLD_OPTION, hold);
        add(line, REASON_OPTION, reason);
        add(line, OPERATOR_OPTION, operator);
        add(line, WITNESS_OPTION, witness);
        return line.toArray(String[]::new);
    }

    private static void add(List<String> line, String option, Object value) {
        if (value != null) {
            line.add("--" + option + "=" + value);
        }
    }

    /** Two employees who are not the leaver: the operator and the witness named on the command line (existing employees, no account needed). */
    protected record Pair(UUID operator, UUID witness) {
    }

    protected Pair twoPeople() throws Exception {
        return new Pair(employed(REGULAR), employed(REGULAR));
    }

    protected Result release(Held held, Pair pair, String reason) {
        return run(line(held.hold(), reason, pair.operator(), pair.witness()));
    }

    // ---- the scenario ----

    /** The only available super administrator, separated and so held: releasing leaves 0 (the case the interface refuses). */
    protected Held heldWithNoneRemaining() throws Exception {
        return heldSeparation(administrator());
    }

    /** Everything the command could touch, to prove that a refusal changed nothing (ADR-0068 item 2.4: refuse before any change). */
    record Snapshot(Map<String, Object> hold, Map<String, Object> command, List<Map<String, Object>> employeeVersions, List<Map<String, Object>> assignmentVersions,
            int holdAudits, int separated, int heldEvents, int releasedEvents, List<String> commandPath, List<String> holdPath, long holdRows) {
    }

    protected Snapshot snapshot(Held held) throws Exception {
        return new Snapshot(hold(held.hold()), command(held.command()), allVersions(EMPLOYEE_VERSIONS, held.employee()),
                allVersions(ASSIGNMENT_VERSIONS, held.employee()), auditCount(AUDIT_HOLD_RELEASED), eventsOf(EVENT_SEPARATED, held.command()),
                eventsOf(EVENT_HELD, held.command()), eventsOf(EVENT_HOLD_RELEASED, held.command()), statusPath(AGGREGATE_PERSONNEL_COMMAND, held.command()),
                statusPath(AGGREGATE_SEPARATION_HOLD, held.hold()), holdRowsOf(held.employee()));
    }

    private long holdRowsOf(UUID employee) throws SQLException {
        return (long) rows("select count(*) as n from " + SEPARATION_HOLDS + " where employee_id = ?", stored(employee)).get(0).get("n");
    }

    /** The refusal: the given exit code (or any non-zero one), a message, and no change in hold, command, versions, events or audit. */
    protected void assertRefusedUnchanged(Result result, Held held, Snapshot before, Integer exitCode) throws Exception {
        if (exitCode == null) {
            assertThat(result.exitCode()).as(result.text()).isNotZero();
        } else {
            assertThat(result.exitCode()).as(result.text()).isEqualTo(exitCode);
        }
        assertThat(result.err()).as("the refusal says why on the error stream").isNotBlank();
        assertThat(snapshot(held)).as("nothing was written: hold, command, versions, events, audit").isEqualTo(before);
        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
        assertThat(employmentOn(held.employee(), TODAY)).as("the administrator is still employed").isEqualTo(EMPLOYED);
        assertThat(auditedActions(SINGLE_OPERATOR_AUDIT_PREFIX)).as("no relaxation was applied or marked").isEmpty();
    }

    // ---- the effect of a release (the same facts as the interface release, ADR-0068 item 2.3 line 89) ----

    /** The command runs at its original effective date, is not re-judged and does not go to HELD again. */
    protected void assertExecutedAtTheOriginalDate(Held held, int employeeVersionsBefore) throws Exception {
        Map<String, Object> hold = hold(held.hold());
        assertThat(hold.get("status")).isEqualTo(RELEASED);
        assertThat(hold.get("release_channel_kind")).as("the channel is the operations command").isEqualTo(CHANNEL_OPERATIONS);
        assertThat(hold.get("released_by")).as("operator and witness are only in the audit, not in released_by (ADR-0068 item 2.1 line 79)").isNull();
        assertThat(hold.get("release_reason")).isEqualTo(REASON_TEXT);
        assertThat(hold.get("released_at")).isNotNull();
        Map<String, Object> command = command(held.command());
        assertThat(command.get("status")).isEqualTo(EFFECTIVE);
        assertThat(command.get("applied_at")).isNotNull();
        assertThat(command.get("effective_date")).as("the effective date is still the one that was entered").isEqualTo(TODAY);
        List<Map<String, Object>> current = currentVersions(EMPLOYEE_VERSIONS, held.employee());
        assertContiguous(current);
        Map<String, Object> separated = inForceOn(current, TODAY);
        assertThat(separated.get("employment_status")).isEqualTo(SEPARATED);
        assertThat(separated.get("effective_from_date")).as("written back at the original date, not at the day of the release").isEqualTo(TODAY);
        assertThat(inForceOn(current, TODAY.minusDays(1)).get("employment_status")).isEqualTo(EMPLOYED);
        assertThat(allVersions(EMPLOYEE_VERSIONS, held.employee())).as("history is superseded, never deleted").hasSizeGreaterThanOrEqualTo(employeeVersionsBefore);
        List<Map<String, Object>> assignments = currentVersions(ASSIGNMENT_VERSIONS, held.employee());
        assertThat(assignments).isNotEmpty();
        assertThat(assignments.getLast().get("effective_to_date")).as("W2 section 3.4: the open assignment ends at the separation day").isEqualTo(TODAY);
        assertThat(commandCount(held.employee())).as("the command is not entered again").isEqualTo(1);
        assertThat(holdRowsOf(held.employee())).as("and does not go to HELD again (no re-judgement)").isEqualTo(1);
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, held.command())).endsWith(HELD, EFFECTIVE).containsOnlyOnce(HELD);
        assertThat(statusPath(AGGREGATE_SEPARATION_HOLD, held.hold())).endsWith(HELD, RELEASED);
        assertThat(eventsOf(EVENT_SEPARATED, held.command())).isEqualTo(1);
        assertThat(eventsOf(EVENT_HELD, held.command())).as("no second SeparationHeld").isEqualTo(1);
        List<String> released = eventJsonOf(EVENT_HOLD_RELEASED, held.command());
        assertThat(released).hasSize(1);
        assertThat(released.getFirst()).contains(held.hold().toString()).containsPattern("\"effectiveDate\"\\s*:\\s*\"" + TODAY + "\"")
                .doesNotContainPattern("\"releasedBy\"\\s*:\\s*\"[^\"]+\"");
    }

    /** The audit entries of the release whose subject is the employee. */
    protected List<Map<String, Object>> releaseAuditOf(UUID employee) throws Exception {
        return auditQuery(null, null, AUDIT_HOLD_RELEASED).stream().filter(entry -> employee.toString().equals(String.valueOf(entry.get("subjectId")))).toList();
    }

    protected static LocalDate farFuture() {
        return TODAY.plusYears(10);
    }
}
