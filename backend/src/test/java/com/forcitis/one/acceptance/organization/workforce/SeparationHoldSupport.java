package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;

/**
 * Ground of the #873 tests (ADR-0068 sections 2 and 3). The count of available super administrators is global, so a test that depends on
 * it must start from a pool it built itself: every account made here is disabled again after the test (an available administrator has an
 * enabled account, ADR-0028 item 4), and the next test sees only its own people. Holds are made by the real path (a SEPARATE command of
 * an administrator entered by the signed-in 人事专员), never written into the tables.
 */
abstract class SeparationHoldSupport extends PersonnelCommandSupport {

    /** The reason a release carries when the test does not care. */
    protected static final String REASON_TEXT = "synthetic: confirmed with the department";

    private final List<UUID> made = new ArrayList<>();

    @AfterEach
    void retireThePool() throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            for (UUID employee : made) {
                update(identity, "update user_accounts set status = 'DISABLED' where employee_id = ?", stored(employee));
            }
        }
        made.clear();
    }

    // ---- people ----

    /** An employed super administrator (ordinary session; the administrator mode is entered with {@link #inAdministratorMode}). */
    protected Staff administrator(String... moreRoles) throws Exception {
        List<String> roles = new ArrayList<>(List.of(SUPER_ADMIN));
        roles.addAll(List.of(moreRoles));
        return account(roles.toArray(String[]::new));
    }

    /** An employed security auditor: holds the permission to release in an ordinary session (ADR-0028 item 3). */
    protected Staff auditor(String... moreRoles) throws Exception {
        List<String> roles = new ArrayList<>(List.of(SECURITY_AUDITOR));
        roles.addAll(List.of(moreRoles));
        return account(roles.toArray(String[]::new));
    }

    protected Staff account(String... roles) throws Exception {
        UUID id = employed(REGULAR);
        made.add(id);
        for (String role : roles) {
            grantByOperations(id, role);
        }
        return new Staff(id, signIn(id));
    }

    protected Staff inAdministratorMode(Staff who) throws Exception {
        return new Staff(who.employeeId(), administratorMode(who.session()));
    }

    /** The status of the person's own profile in their login session over HTTP: 200 while the session lives, 401 or 403 when it is gone. */
    protected int profileStatus(Staff who) throws Exception {
        return mvc.perform(get("/api/v1/platform/sessions:current").with(session(who.session()))).andReturn().getResponse().getStatus();
    }

    // ---- entering and releasing ----

    /** The last working day before today, so that the separation takes effect today and runs inside the entry transaction (ADR-0068 item 1.8). */
    /**
     * The rows once there are some. The identity listeners of personnel events run after the command commits and on their own thread
     * (@ApplicationModuleListener: async, after commit), so a read right after the command can precede them. Bounded wait; on a timeout the
     * empty list comes back and the caller's own assertion fails with its message.
     */
    protected static List<Map<String, Object>> whenOpened(Callable<List<Map<String, Object>>> rows) {
        try {
            return Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(rows, found -> !found.isEmpty());
        } catch (ConditionTimeoutException timeout) {
            return List.of();
        }
    }

    protected static final LocalDate LAST_DAY = TODAY.minusDays(1);

    /** Enters a SEPARATE of the administrator as the 人事专员 and answers its id. */
    protected UUID separateAsHr(Staff leaver) throws Exception {
        actIn(hr);
        return separate(leaver.employeeId(), LAST_DAY);
    }

    /** A separation the hold path must hold: HELD, with a HELD hold, the person still employed. */
    protected Held heldSeparation(Staff leaver) throws Exception {
        UUID command = separateAsHr(leaver);
        assertThat(command(command).get("status")).as("the separation of the administrator is held").isEqualTo(HELD);
        return new Held(leaver.employeeId(), command, holdIdOf(command));
    }

    protected void releaseAs(Staff releaser, UUID hold, String reason) throws Exception {
        actIn(releaser);
        commands().release(hold, reason);
    }

    protected UUID holdIdOf(UUID command) throws SQLException {
        List<Map<String, Object>> found = rows("select separation_hold_id from " + SEPARATION_HOLDS + " where personnel_command_id = ?", command);
        assertThat(found).as("a row of " + SEPARATION_HOLDS + " for the command " + command).hasSize(1);
        return (UUID) found.get(0).get("separation_hold_id");
    }

    protected Map<String, Object> hold(UUID hold) throws SQLException {
        List<Map<String, Object>> found = rows("select * from " + SEPARATION_HOLDS + " where separation_hold_id = ?", hold);
        assertThat(found).as("a row of " + SEPARATION_HOLDS + " for " + hold).hasSize(1);
        return found.get(0);
    }

    protected String holdStatus(UUID hold) throws SQLException {
        return (String) hold(hold).get("status");
    }

    protected String commandStatus(UUID command) throws SQLException {
        return (String) command(command).get("status");
    }

    /** A held separation of {@code leaver}, a releaser who is not in the held set, and the administrator who stays. */
    protected record Scenario(Held held, Staff leaver, Staff releaser, Staff stays) {
    }

    /**
     * S2 is separated and so held (every separation of a super administrator is held, ADR-0055 decision 3 item 3); S3 stays and
     * {@code extra} more administrators join afterwards. Released, S2 leaves 1 + extra available administrators. The releaser is a security
     * auditor, who is no administrator and so does not change the count.
     */
    protected Scenario heldWithRemaining(int extra) throws Exception {
        Staff s2 = administrator();
        Staff s3 = administrator();
        Staff releaser = auditor();
        Held held = heldSeparation(s2);
        for (int i = 0; i < extra; i++) {
            administrator();
        }
        return new Scenario(held, s2, releaser, s3);
    }

    /** The session-mode field of an audit detail with the given value ("session mode: X", "sessionMode":"X"); not the word inside another field name. */
    static java.util.regex.Pattern sessionModePattern(String mode) {
        return java.util.regex.Pattern.compile("(?i)session[ _-]?mode\\W{1,6}" + mode + "\\b");
    }

    // ---- reading ----

    /** The employment status of the current facts on the day, or null when no current row covers it. */
    protected String employmentOn(UUID employee, LocalDate day) throws SQLException {
        Map<String, Object> row = inForceOn(currentVersions(EMPLOYEE_VERSIONS, employee), day);
        return row == null ? null : (String) row.get("employment_status");
    }

    protected boolean onUnpaidLeaveOn(UUID employee, LocalDate day) throws SQLException {
        Map<String, Object> row = inForceOn(currentVersions(EMPLOYEE_VERSIONS, employee), day);
        assertThat(row).as("a current employee version covers " + day).isNotNull();
        return (boolean) row.get("is_on_unpaid_leave");
    }

    /** The to_status values of the aggregate's state_transitions, in order, after checking that the rows chain. */
    protected List<String> statusPath(String aggregateType, UUID aggregateId) throws SQLException {
        List<String> path = new ArrayList<>();
        for (Map<String, Object> step : transitions(aggregateType, aggregateId)) {
            assertThat(step.get("from_status")).as("a step starts where the last one ended").isEqualTo(path.isEmpty() ? null : path.getLast());
            path.add((String) step.get("to_status"));
        }
        return path;
    }

    /** The serialized envelopes of the published events of the type that mention the command, distinct by event id. */
    protected List<String> eventJsonOf(String eventType, UUID command) throws SQLException {
        List<String> found = new ArrayList<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                for (Map<String, Object> row : query(registry, "select serialized_event from " + table + " where event_type like ? and serialized_event like ?",
                        "%." + eventType, "%" + command + "%")) {
                    String json = (String) row.get("serialized_event");
                    if (!found.contains(json)) {
                        found.add(json);
                    }
                }
            }
        }
        return found;
    }

    /** The audit entries of the actor whose action is the release; read through the audit HTTP query. */
    protected List<Map<String, Object>> releaseAuditBy(Staff releaser) {
        return auditEntriesBy(releaser.userId(), AUDIT_HOLD_RELEASED);
    }
}
