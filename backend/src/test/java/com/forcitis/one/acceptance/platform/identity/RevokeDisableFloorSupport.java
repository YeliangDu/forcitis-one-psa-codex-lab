package com.forcitis.one.acceptance.platform.identity;

import com.forcitis.one.acceptance.EmploymentRelationshipSeeds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommands;
import com.forcitis.one.organization.workforce.api.ReleaseSeparationHold;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.platform.identity.api.SessionRoles;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Ground of the #732 part B tests (revoke / disable floor, alert and lock order). The count of available super administrators is global, so
 * every test starts from a pool it makes itself and every account made here is disabled again afterwards (an available administrator has an
 * enabled account, ADR-0028 item 4). Two administrators, the "desk", ask for and confirm the changes; they are put into a HELD separation as
 * soon as they have their administrator-mode sessions, so that they do not count as available (ADR-0068 item 2.2) and the pool is exactly the
 * people a test makes. Holds are made by the real path (a SEPARATE command entered by the signed-in personnel specialist) and released by the
 * real release; grants are made with the operations command and read from account_role_grants.
 */
abstract class RevokeDisableFloorSupport extends AccountsAcceptanceSupport {

    protected static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    protected static final String REQUESTS = "/api/v1/platform/authorization-requests";
    protected static final String SUPER_ADMIN = "super_admin";
    protected static final String AUDITOR = "security_auditor";
    protected static final String ORDINARY_ROLE = "business_admin";
    /** Opens the account of a person who is given no role, so that the first login does not (the login opening an account is alerted, #1174). */
    protected static final String DEFAULT_ROLE = "employee";
    protected static final String REASON = "synthetic: confirmed with the department";
    /** The last working day before today: the separation takes effect today and runs inside the entry transaction. */
    protected static final LocalDate LAST_DAY = TODAY.minusDays(1);
    /** A separation that takes effect after today: once released it is PENDING (gone from the count, still employed). */
    protected static final LocalDate DUE = TODAY.plusDays(10);

    protected static final String ROLE_REVOCATION_NOTICE = "ROLE_REVOCATION_NOTICE";
    protected static final String ACCOUNT_DISABLE_NOTICE = "ACCOUNT_DISABLE_NOTICE";
    protected static final String ACCOUNT_ROLE_GRANT = "ACCOUNT_ROLE_GRANT";
    protected static final String USER_ACCOUNT = "USER_ACCOUNT";
    protected static final Set<String> FLOOR_PURPOSES = Set.of(ROLE_REVOCATION_NOTICE, ACCOUNT_DISABLE_NOTICE);

    /** Business refusals of the floor: no error code of its own exists yet, so the status is the contract (doubtful point 1). */
    protected static final Set<Integer> REFUSAL_STATUSES = Set.of(409, 422);
    /** Codes of statuses 409/422 that say something other than "the floor": a refusal with one of them does not count as a floor refusal. */
    protected static final Set<String> NOT_THE_FLOOR = Set.of("PLATFORM_IDENTITY_CONCURRENT_UPDATE", "PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING",
            "PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_ROLE_NOT_FOUND",
            "PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND");

    @Autowired
    private PersonnelCommands personnelCommands;

    @Autowired
    private com.forcitis.one.organization.workforce.api.SeparationHolds separationHolds;

    @Autowired
    private OperationsCommands operationsBean;

    protected UUID unit;
    protected UUID legalEntity;
    private SignedIn organisationAdmin;
    protected Person hr;
    private SecurityContext acting;
    private final List<UUID> made = new ArrayList<>();

    /** A person with an account: the employee behind it and the login session (the administrator-mode one for the desk). */
    protected record Person(UUID employeeId, SignedIn in) {
        UUID userId() {
            return in.userId();
        }

        String wecomId() {
            return wecomUserIdOf(employeeId);
        }
    }

    /** The two administrators that ask and confirm; both are in a HELD separation, so neither counts as available. */
    protected record Desk(Person requester, Person confirmer) {
    }

    /** What an HTTP act answered: the last answer, the request id of the two-person step if there was one, and the state just before the applying call. */
    protected record Outcome(int status, String body, UUID requestId, Snapshot beforeApply) {
        boolean done() {
            return status == 204;
        }

        String errorCode() {
            try {
                return JsonPath.read(body, "$.errorCode");
            } catch (RuntimeException none) {
                return null;
            }
        }
    }

    /** Everything a refused act must leave as it was. */
    protected record Snapshot(List<UUID> openGrants, long grantRows, long auditRows, List<String> accounts) {
    }

    /** A floor card as handed to the WeCom gateway. */
    protected record Card(String subjectKind, UUID subjectId, String purpose, Optional<UUID> trigger, List<String> recipients, String text) {
    }

    // ---- the world ----

    @BeforeEach
    void buildTheWorld() throws Exception {
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID admin = newEmployeeId();
        employee(admin, unit, null);
        grantByOperations(admin, "org_data_admin");
        organisationAdmin = signIn(admin);
        legalEntity = createLegalEntity(organisationAdmin, newLegalEntityCode("RD"), "Synthetic revoke-disable entity");
        UUID hrEmployee = newEmployeeId();
        employee(hrEmployee, unit, null);
        grantByOperations(hrEmployee, "personnel_specialist");
        hr = new Person(hrEmployee, signIn(hrEmployee));
        actIn(hr);
    }

    /** Disables every account the test made: the next test sees only its own people. */
    @AfterEach
    void retireThePool() throws SQLException {
        retire(made.toArray(UUID[]::new));
        made.clear();
    }

    protected void retire(UUID... employees) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            for (UUID employee : employees) {
                update(identity, "update user_accounts set status = 'DISABLED' where employee_id = ?", stored(employee));
            }
        }
    }

    protected void retire(Person... people) throws SQLException {
        retire(java.util.Arrays.stream(people).map(Person::employeeId).toArray(UUID[]::new));
    }

    // ---- people ----

    /** An employed person with migrated initial versions, attributed to the world's legal entity; no account yet. */
    protected UUID employed() throws Exception {
        UUID position = newPosition();
        UUID id = newEmployeeId();
        employee(id, unit, null);
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        LocalDate since = LocalDate.of(2026, 1, 1);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employment_relationship_versions (employment_relationship_id, employment_relationship_version_id, employee_id, effective_from_date, revision_number, employment_status,
                      employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                      change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, ?, 0, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?)""", EmploymentRelationshipSeeds.relationshipOf(workforce, stored(id)), Uuids.timeOrdered(NOW), stored(id), since, since, at, at);
            // #711: on the employee's PRIMARY root once employee_assignments exists (AssignmentRoots).
            UUID root = AssignmentRoots.primaryOf(workforce, stored(id));
            update(workforce, AssignmentRoots.withRoot("""
                    insert into assignment_versions (employment_relationship_id, assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id,
                      change_channel_kind, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'MIGRATION', ?, ?)""", root), AssignmentRoots.arguments(root, EmploymentRelationshipSeeds.relationshipOf(workforce, stored(id)), Uuids.timeOrdered(NOW), stored(id), since, stored(unit), position, at, at));
        }
        attributeEmployee(organisationAdmin, id, legalEntity);
        return id;
    }

    private UUID newPosition() throws Exception {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "insert into positions (id, code, name, status) values (?, ?, 'Synthetic position', 'ACTIVE')", id, "RD" + id.toString().substring(24));
        }
        return id;
    }

    /** An employed person with an account and the given roles (granted before the login, so the session carries them); with no role the default role opens the account. */
    protected Person account(String... roles) throws Exception {
        UUID id = employed();
        made.add(id);
        for (String role : roles) {
            grantByOperations(id, role);
        }
        if (roles.length == 0) {
            grantByOperations(id, DEFAULT_ROLE);
        }
        return new Person(id, signIn(id));
    }

    protected Person admin(String... moreRoles) throws Exception {
        List<String> roles = new ArrayList<>(List.of(SUPER_ADMIN));
        roles.addAll(List.of(moreRoles));
        return account(roles.toArray(String[]::new));
    }

    protected Person auditor(String... moreRoles) throws Exception {
        List<String> roles = new ArrayList<>(List.of(AUDITOR));
        roles.addAll(List.of(moreRoles));
        return account(roles.toArray(String[]::new));
    }

    protected Person inAdministratorMode(Person who) throws Exception {
        return new Person(who.employeeId(), administratorMode(who.in()));
    }

    /** Two super administrators in the administrator mode who are then held, so that they do not count. */
    protected Desk desk() throws Exception {
        Person requester = inAdministratorMode(admin());
        Person confirmer = inAdministratorMode(admin());
        hold(requester);
        hold(confirmer);
        return new Desk(requester, confirmer);
    }

    // ---- holds (real path) ----

    /** Enters the separation of the administrator as the personnel specialist; the command is HELD and so is its hold. Takes effect today when released. */
    protected UUID hold(Person leaver) throws Exception {
        return enterSeparation(leaver, LAST_DAY);
    }

    /** The separation takes effect on DUE (after today): held now, and PENDING once released. */
    protected UUID holdForTheFuture(Person leaver) throws Exception {
        return enterSeparation(leaver, DUE.minusDays(1));
    }

    private UUID enterSeparation(Person leaver, LocalDate lastWorkingDay) throws Exception {
        actIn(hr);
        UUID command = personnelCommands.enter(new EnterPersonnelCommand(employeeIdOf(leaver.employeeId()), PersonnelCommandKind.SEPARATE, null,
                lastWorkingDay, null, null, null, null, null, null, null, null, null)).value();
        assertThat(commandStatus(command)).as("the separation of an administrator is held").isEqualTo("HELD");
        return holdOf(command);
    }

    /** Enters an unpaid-leave start dated after today (entered, not started) for the person; the command is PENDING. Answers the command id. */
    protected UUID enterFutureUnpaidLeave(Person person) throws Exception {
        actIn(hr);
        UUID command = personnelCommands.enter(new EnterPersonnelCommand(employeeIdOf(person.employeeId()), PersonnelCommandKind.UNPAID_LEAVE_START,
                DUE, null, null, null, null, null, null, null, null, null, null)).value();
        assertThat(commandStatus(command)).as("an unpaid-leave start in the future waits for its date").isEqualTo("PENDING");
        return command;
    }

    protected void withdrawCommand(UUID command) throws Exception {
        actIn(hr);
        personnelCommands.withdraw(new com.forcitis.one.organization.workforce.api.WithdrawPersonnelCommand(
                new com.forcitis.one.organization.workforce.api.PersonnelCommandId(command), REASON));
    }

    protected UUID holdOf(UUID command) throws SQLException {
        List<Map<String, Object>> found = workforce("select separation_hold_id from separation_holds where personnel_command_id = ?", command);
        assertThat(found).hasSize(1);
        return (UUID) found.get(0).get("separation_hold_id");
    }

    protected String commandStatus(UUID command) throws SQLException {
        return (String) workforce("select status from personnel_commands where personnel_command_id = ?", command).get(0).get("status");
    }

    protected String holdStatus(UUID hold) throws SQLException {
        return (String) workforce("select status from separation_holds where separation_hold_id = ?", hold).get(0).get("status");
    }

    /** Releases the hold in the releaser's ordinary session; business refusals propagate. */
    protected void release(Person releaser, UUID hold) throws Exception {
        actIn(releaser);
        separationHolds.release(new ReleaseSeparationHold(new SeparationHoldId(hold), REASON));
    }

    /** A future-dated separation of the administrator that is held and then released: PENDING, counted as gone, still employed. */
    protected void releasedPending(Person leaver, Person releaser) throws Exception {
        UUID hold = holdForTheFuture(leaver);
        release(releaser, hold);
        String status = (String) workforce("select c.status from personnel_commands c join separation_holds h on h.personnel_command_id = c.personnel_command_id"
                + " where h.separation_hold_id = ?", hold).get(0).get("status");
        assertThat(status).as("released with a date in the future: pending").isEqualTo("PENDING");
    }

    protected List<Map<String, Object>> workforce(String sql, Object... args) throws SQLException {
        try (Connection connection = schemaConnection("organization_workforce")) {
            return query(connection, sql, args);
        }
    }

    // ---- acting in process (the personnel command api has no HTTP surface) ----

    protected void actIn(Person who) throws Exception {
        MockHttpServletResponse current = mvc.perform(get("/api/v1/platform/sessions:current").with(session(who.in()))).andReturn().getResponse();
        assertThat(current.getStatus()).as("the session's own profile: " + current.getContentAsString()).isEqualTo(200);
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String code : JsonPath.<List<String>>read(current.getContentAsString(), "$.permissionCodes")) {
            authorities.add(new SimpleGrantedAuthority(code));
        }
        if ("ORDINARY".equals(JsonPath.<String>read(current.getContentAsString(), "$.mode"))) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
        }
        actInSession(who.in());
        Authentication login = SecurityContextHolder.getContext().getAuthentication();
        Method refresh = login.getClass().getDeclaredMethod("withAuthorities", Collection.class, boolean.class);
        refresh.setAccessible(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication((Authentication) refresh.invoke(login, authorities, true));
        acting = context;
        SecurityContextHolder.setContext(context);
    }

    /** For a worker thread: the caller set by {@link #actIn} is this thread's caller too. */
    protected void actAsTheCallerOnThisThread() {
        SecurityContextHolder.setContext(acting);
    }

    // ---- HTTP acts ----

    private MockHttpServletResponse getAccount(Person as, UUID user) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + user).with(session(as.in()))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return read;
    }

    protected List<String> rolesOf(Person as, Person account) throws Exception {
        return JsonPath.read(getAccount(as, account.userId()).getContentAsString(), "$.roleCodes");
    }

    /**
     * Takes the roles away from the target as the whole-set replacement the contract has. The requester asks; when the answer is 202 (a second
     * person must confirm) the confirmer confirms and the answer of that call is the outcome. The state is read just before the applying call.
     */
    protected Outcome revoke(Person requester, Person confirmer, Person target, String... away) throws Exception {
        MockHttpServletResponse account = getAccount(requester, target.userId());
        List<String> roles = new ArrayList<>(JsonPath.<List<String>>read(account.getContentAsString(), "$.roleCodes"));
        roles.removeAll(List.of(away));
        String body = "{\"roleCodes\":[" + String.join(",", roles.stream().map(code -> "\"" + code + "\"").toList()) + "]}";
        Snapshot beforeAsk = snapshot(target);
        MockHttpServletResponse asked = mvc.perform(post(ACCOUNTS + target.userId() + ":replace-roles").header("If-Match", account.getHeader("ETag"))
                .with(csrf()).with(session(requester.in())).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        if (asked.getStatus() != 202) {
            return new Outcome(asked.getStatus(), asked.getContentAsString(), null, beforeAsk);
        }
        UUID requestId = UUID.fromString(JsonPath.read(asked.getContentAsString(), "$.requestId"));
        Snapshot beforeApply = snapshot(target);
        MockHttpServletResponse confirmed = mvc.perform(post(REQUESTS + "/" + requestId + ":confirm").with(csrf()).with(session(confirmer.in())))
                .andReturn().getResponse();
        return new Outcome(confirmed.getStatus(), confirmed.getContentAsString(), requestId, beforeApply);
    }

    protected Outcome disable(Person by, Person target) throws Exception {
        return changeStatus(by, target, "disable");
    }

    protected Outcome enable(Person by, Person target) throws Exception {
        return changeStatus(by, target, "enable");
    }

    private Outcome changeStatus(Person by, Person target, String action) throws Exception {
        MockHttpServletResponse account = getAccount(by, target.userId());
        Snapshot before = snapshot(target);
        MockHttpServletResponse answered = mvc.perform(post(ACCOUNTS + target.userId() + ":" + action).header("If-Match", account.getHeader("ETag"))
                .with(csrf()).with(session(by.in()))).andReturn().getResponse();
        return new Outcome(answered.getStatus(), answered.getContentAsString(), null, before);
    }

    /** The two-person step of a revoke, split so that a test can hold a lock between asking and confirming. */
    protected UUID ask(Person requester, Person target, String... away) throws Exception {
        MockHttpServletResponse account = getAccount(requester, target.userId());
        List<String> roles = new ArrayList<>(JsonPath.<List<String>>read(account.getContentAsString(), "$.roleCodes"));
        roles.removeAll(List.of(away));
        String body = "{\"roleCodes\":[" + String.join(",", roles.stream().map(code -> "\"" + code + "\"").toList()) + "]}";
        MockHttpServletResponse asked = mvc.perform(post(ACCOUNTS + target.userId() + ":replace-roles").header("If-Match", account.getHeader("ETag"))
                .with(csrf()).with(session(requester.in())).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        assertThat(asked.getStatus()).as("asking for the revoke waits for a second person: " + asked.getContentAsString()).isEqualTo(202);
        return UUID.fromString(JsonPath.read(asked.getContentAsString(), "$.requestId"));
    }

    protected Outcome confirm(Person confirmer, UUID requestId) throws Exception {
        MockHttpServletResponse confirmed = mvc.perform(post(REQUESTS + "/" + requestId + ":confirm").with(csrf()).with(session(confirmer.in())))
                .andReturn().getResponse();
        return new Outcome(confirmed.getStatus(), confirmed.getContentAsString(), requestId, null);
    }

    /** The operations command revoke-role with operator and witness; the answer is the exit code. */
    protected int revokeByOperations(Person target, String roleCode) {
        ensureAttestors();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = operationsBean.run(new org.springframework.boot.DefaultApplicationArguments("--operation=revoke-role", "--employee-id=" + text(target.employeeId()),
                "--role-code=" + roleCode, "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        lastOperationsText = out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
        return exit;
    }

    protected String lastOperationsText = "";

    protected void assertRefusedByTheFloor(Outcome outcome) {
        assertThat(outcome.status()).as("a business refusal (409 or 422; no error code of its own exists yet): " + outcome.body()).isIn(REFUSAL_STATUSES);
        assertThat(outcome.errorCode()).as("the refusal names an error code: " + outcome.body()).isNotBlank().isNotIn(NOT_THE_FLOOR);
    }

    protected void assertDone(Outcome outcome) {
        assertThat(outcome.status()).as(outcome.body()).isEqualTo(204);
    }

    // ---- state ----

    protected Snapshot snapshot(Person... accounts) throws SQLException {
        List<UUID> open = new ArrayList<>();
        long rows;
        List<String> states = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity")) {
            for (Map<String, Object> grant : query(identity, "select account_role_grant_id from account_role_grants where effective_to_at is null order by account_role_grant_id")) {
                open.add((UUID) grant.get("account_role_grant_id"));
            }
            rows = (long) query(identity, "select count(*) as n from account_role_grants").get(0).get("n");
            for (Person account : accounts) {
                for (Map<String, Object> row : query(identity, "select status, version from user_accounts where user_id = ?", account.userId())) {
                    states.add(account.userId() + ":" + row.get("status") + ":" + row.get("version"));
                }
            }
        }
        return new Snapshot(open, rows, settledAuditRows(), states);
    }

    /** Audit entries from domain events are written after the commit: read until the count stops moving. */
    protected long settledAuditRows() throws SQLException {
        long previous = -1;
        for (int i = 0; i < 40; i++) {
            long now = auditRows();
            if (now == previous) {
                return now;
            }
            previous = now;
            sleep(150);
        }
        return previous;
    }

    /**
     * Lead ruling 2026-11-02: PrivilegedRequestAudit records every administrator-mode write attempt before its outcome is known, refused ones
     * included, so its entries are not a change made by the act and stay out of the "nothing changed" count.
     */
    static final String PRIVILEGED_REQUEST_ACTION = "platform.identity.privileged.request";

    private long auditRows() throws SQLException {
        try (Connection audit = schemaConnection("platform_audit")) {
            return (long) query(audit, "select count(*) as n from audit_entries where action <> ?", PRIVILEGED_REQUEST_ACTION).get(0).get("n");
        }
    }

    protected List<Map<String, Object>> grantsOf(Person person, String roleCode) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            return query(identity, """
                    select g.* from account_role_grants g join roles r on r.role_id = g.role_id
                     where g.user_id = ? and r.code = ? order by g.effective_from_at, g.created_at""", person.userId(), roleCode);
        }
    }

    protected boolean holds(Person person, String roleCode) throws SQLException {
        return grantsOf(person, roleCode).stream().anyMatch(grant -> grant.get("effective_to_at") == null);
    }

    /** The grant of the role that was closed last. */
    protected UUID closedGrant(Person person, String roleCode) throws SQLException {
        List<Map<String, Object>> closed = grantsOf(person, roleCode).stream().filter(grant -> grant.get("effective_to_at") != null).toList();
        assertThat(closed).as("a closed " + roleCode + " grant of " + person.userId()).isNotEmpty();
        return (UUID) closed.get(closed.size() - 1).get("account_role_grant_id");
    }

    protected String accountStatus(Person person) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            return (String) query(identity, "select status from user_accounts where user_id = ?", person.userId()).get(0).get("status");
        }
    }

    /** The ids of the audit entries about the account so far (subject USER_ACCOUNT). */
    protected Set<UUID> accountAuditIds(Person account) throws SQLException {
        Set<UUID> ids = new TreeSet<>();
        try (Connection audit = schemaConnection("platform_audit")) {
            for (Map<String, Object> row : query(audit, "select id from audit_entries where subject_kind = 'USER_ACCOUNT' and subject_id = ?", account.userId())) {
                ids.add((UUID) row.get("id"));
            }
        }
        return ids;
    }

    /** The audit entry that one disable wrote about the account: the one new entry whose action is about disabling (or the only new one). */
    protected UUID disableAuditEntry(Person account, Set<UUID> before) throws SQLException {
        List<Map<String, Object>> fresh = new ArrayList<>();
        try (Connection audit = schemaConnection("platform_audit")) {
            for (Map<String, Object> row : query(audit, "select id, action from audit_entries where subject_kind = 'USER_ACCOUNT' and subject_id = ? order by created_at",
                    account.userId())) {
                if (!before.contains((UUID) row.get("id"))) {
                    fresh.add(row);
                }
            }
        }
        assertThat(fresh).as("the disable wrote an audit entry about the account").isNotEmpty();
        List<Map<String, Object>> about = fresh.stream().filter(row -> String.valueOf(row.get("action")).toLowerCase().matches(".*(disabl|status).*")).toList();
        if (about.size() == 1) {
            return (UUID) about.get(0).get("id");
        }
        assertThat(fresh).as("which of the new entries is the disable: " + fresh).hasSize(1);
        return (UUID) fresh.get(0).get("id");
    }

    // ---- cards and alerts ----

    protected void forgetCards() {
        Mockito.clearInvocations(wecomGateway);
    }

    protected List<Card> floorCards() {
        ArgumentCaptor<WecomCard> captured = ArgumentCaptor.forClass(WecomCard.class);
        Mockito.verify(wecomGateway, Mockito.atLeast(0)).send(captured.capture());
        List<Card> cards = new ArrayList<>();
        for (WecomCard card : captured.getAllValues()) {
            String purpose = card.subject().purpose().name();
            if (FLOOR_PURPOSES.contains(purpose)) {
                List<String> recipients = new ArrayList<>();
                card.wecomUserIds().forEach(id -> recipients.add(id.value()));
                recipients.sort(String::compareTo);
                cards.add(new Card(card.subject().kind().name(), card.subject().id(), purpose, card.subject().triggerEvent(), recipients,
                        card.title() + "\n" + card.description()));
            }
        }
        return cards;
    }

    /** Waits (up to 10 s) for at least n floor cards, then lets late ones arrive, and answers all of them. */
    protected List<Card> cardsAfterwards(int expected) {
        if (expected > 0) {
            try {
                Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).until(() -> floorCards().size() >= expected);
            } catch (org.awaitility.core.ConditionTimeoutException none) {
                // the assertion of the caller reports the count it got
            }
        }
        sleep(400);
        return floorCards();
    }

    protected static Set<String> wecomIds(Person... people) {
        Set<String> ids = new TreeSet<>();
        for (Person person : people) {
            ids.add(person.wecomId());
        }
        return ids;
    }

    /** A card names no person: none of the synthetic display names ("Synthetic person N") nor employee codes ("ACC" + number) is in the text. */
    protected static void assertNoPersonalData(Card card) {
        assertThat(card.text()).doesNotContainIgnoringCase("synthetic person").doesNotContainPattern("ACC\\d");
    }

    /** A pause that is the point of the test (a stagger, or the time late work would need to show up); not a wait for a condition. */
    protected static void sleep(long millis) {
        Awaitility.await().pollDelay(Duration.ofMillis(millis)).atMost(Duration.ofSeconds(30)).until(() -> true);
    }

    // ---- concurrency ----

    /**
     * Runs the acts at the same moment on threads of their own; with reversed set the last one is released first (the other start order).
     * Answers the results in the order the acts were given. An act that does not finish in 90 s is a hang or a deadlock.
     */
    protected <T> List<T> race(boolean reversed, List<Callable<T>> acts) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(acts.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < acts.size(); i++) {
                Callable<T> act = acts.get(i);
                long stagger = (reversed ? acts.size() - 1 - i : i) * 3L;
                futures.add(threads.submit(() -> {
                    actAsTheCallerOnThisThread();
                    go.await();
                    sleep(stagger);
                    return act.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    results.add(future.get(90, TimeUnit.SECONDS));
                } catch (TimeoutException hung) {
                    throw new AssertionError("an act did not finish in 90 s: a hang or a deadlock", hung);
                }
            }
            return results;
        } finally {
            threads.shutdownNow();
        }
    }

    /** Pool of the first n of a list of persons that still hold an open super_admin grant on an enabled account. */
    protected long availableAmong(Person... people) throws SQLException {
        long available = 0;
        for (Person person : people) {
            if ("ENABLED".equals(accountStatus(person)) && holds(person, SUPER_ADMIN)) {
                available++;
            }
        }
        return available;
    }

    // ---- jdbc ----

    protected static List<Map<String, Object>> query(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                var meta = result.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                        Object value = "timestamptz".equals(meta.getColumnTypeName(c)) ? result.getObject(c, OffsetDateTime.class) : result.getObject(c);
                        row.put(meta.getColumnLabel(c).toLowerCase(java.util.Locale.ROOT), value);
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    protected static void update(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }
}
