package com.forcitis.one.acceptance.organization.workforce;

import com.forcitis.one.acceptance.AssignmentRoots;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeHired;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

/**
 * #1015 (lead ruling 2026-11-02 on #934): when a HIRE takes effect, platform.identity opens the employee's account on EmployeeHired, in place of
 * the account opening the old sync did on OrgSnapshotChanged. The opening follows the rules every account entry follows: one account per
 * employee and one AccountOpened however often or concurrently it is reached (ADR-0033 section 3 item 1, P2-M3); the base role granted once at
 * creation (RULE-344, RULE_REVIEWS:73: 「新建在职员工时显式授予一次基础角色并审计」), i.e. the configured default role employee as one open grant
 * of the grant store with channel DEFAULT_ROLE (ADR-0034 decision 2 item 3: 「建档或复职时的默认角色」) and its audit entry; an existing
 * account is left as it is; the WeCom user id of the employee is not written (#1015 「no WeCom id is overwritten」).
 *
 * <p>Events built here are built the way workforce publishes EmployeeHired (envelope over the employee, business date the effective date,
 * inside a transaction, delivered after the commit through the event publication registry). Whether identity handled it is read from the
 * registry: a publication of the event to a listener of platform.identity, completed.
 */
class HireOpensAccountIT extends EmployeeHireSupport {

    static final TestDatabase postgres = TestPostgres.database(HireOpensAccountIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String BASE_ROLE = "employee";
    static final String DEFAULT_ROLE_CHANNEL = "DEFAULT_ROLE";
    static final Duration DELIVERY = Duration.ofSeconds(20);
    static final int ROUNDS = 3;

    @Autowired
    ApplicationContext beans;

    @Autowired
    EventEnvelopes envelopes;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    OperationsCommands operations;

    private ListAppender<ILoggingEvent> errors;
    private Logger root;

    @BeforeEach
    void captureErrors() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        errors = new ListAppender<>();
        errors.start();
        root.addAppender(errors);
    }

    @BeforeEach
    void theEmployeeCodeTemplateIsConfirmed() throws Exception {
        Staff businessAdmin = staff(BUSINESS_ADMIN);
        confirmSeededNumberingRule(EMPLOYEE_DOCUMENT_TYPE, businessAdmin.session());
        actIn(hr);
    }

    @AfterEach
    void releaseErrors() {
        root.detachAppender(errors);
        errors.stop();
    }

    // ---- entering a hire ----

    /** A HIRE of a new person through the personnel command entry, effective on the day, with its own WeCom user id. */
    private UUID hireOn(LocalDate effective, String wecomUserId) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(COMMAND_KIND, HIRE);
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(TARGET_ORG_UNIT_ID, unit);
        fields.put(TARGET_POSITION_ID, positionFor());
        fields.put(IS_TARGET_FEE_EARNER, true);
        fields.put(TARGET_EMPLOYMENT_PHASE_KIND, PROBATION);
        fields.put(DISPLAY_NAME, "Synthetic hire " + wecomUserId);
        fields.put(WECOM_USER_ID, wecomUserId);
        return commands().enter(fields);
    }

    private UUID positionFor() {
        try {
            return newPosition();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** EmployeeHired as workforce builds it, published inside a transaction as a system run (no signed-in caller); returns the event id. */
    private UUID publishEmployeeHired(EmployeeHired event) {
        SecurityContextHolder.clearContext();
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.personnel.command.due")) {
            return transactions.execute(status -> {
                publisher.publishEvent(event);
                return event.envelope().eventId();
            });
        }
    }

    /**
     * Built as the due-date job builds it: a system run under its actor, no signed-in caller; the envelope's legal entity is the one legal
     * entity of this world (workforce.yaml EmployeeHired: the employee's legal entity on the effective date).
     */
    private EmployeeHired employeeHired(UUID command, UUID employee, LocalDate effective) {
        SecurityContextHolder.clearContext();
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.personnel.command.due")) {
            return new EmployeeHired(envelopes.wrap(new EventDraft("organization.workforce.EmployeeHired", 1, "Employee", employee, 1,
                    new LegalEntityId(legalEntity), effective, List.of())), new PersonnelCommandId(command), employeeIdOf(employee), effective);
        }
    }

    private String grantRoleByOperations(UUID employee) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ensureAttestors();
        int exit = operations.run(new DefaultApplicationArguments("--operation=grant-role", "--employee-id=" + text(employee), "--role-code=business_admin",
                "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return (exit + " " + err.toString(StandardCharsets.UTF_8)).strip();
    }

    /** Runs the due-date job once as the system (PersonnelCommandNames: a bean of type DUE_JOB_TYPE with a public DUE_JOB_METHOD()). */
    private void runDueJob() throws Exception {
        SecurityContextHolder.clearContext();
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name);
            if (type != null && ClassUtils.getUserClass(type).getSimpleName().equals(DUE_JOB_TYPE)) {
                Method run = ClassUtils.getUserClass(type).getMethod(DUE_JOB_METHOD);
                try {
                    run.invoke(beans.getBean(name));
                } catch (InvocationTargetException failure) {
                    throw failure.getCause() instanceof Exception cause ? cause : new IllegalStateException(failure.getCause());
                }
                return;
            }
        }
        throw new AssertionError("ADR-0068 item 1.8: no bean of a type named " + DUE_JOB_TYPE);
    }

    // ---- reading identity ----

    /** "COMPLETED", "FAILED" or "OPEN" (also when there is none) for the publications to platform.identity listeners of EmployeeHired events matching the text. */
    private String hiredDeliveryToIdentity(String matching) throws Exception {
        List<String> states = new ArrayList<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement select = registry.prepareStatement("select completion_date is not null, status from " + table
                        + " where event_type like '%.EmployeeHired' and listener_id like 'com.forcitis.one.platform.identity.%' and serialized_event like ?")) {
                    select.setString(1, "%" + matching + "%");
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            states.add("FAILED".equals(rs.getString(2)) ? "FAILED" : rs.getBoolean(1) ? "COMPLETED" : "OPEN");
                        }
                    }
                }
            }
        }
        if (states.isEmpty() || states.contains("OPEN")) {
            return "OPEN";
        }
        return states.contains("FAILED") ? "FAILED" : "COMPLETED";
    }

    /** Waits until identity is done with the EmployeeHired events matching the text, then requires that it completed them. */
    private void identityHandledEmployeeHired(String matching) throws Exception {
        try {
            Awaitility.await().atMost(DELIVERY).pollInterval(Duration.ofMillis(100)).until(() -> hiredDeliveryToIdentity(matching), state -> !state.equals("OPEN"));
        } catch (ConditionTimeoutException stillOpen) {
            // reported by the assertion below, with the state it stayed in
        }
        assertThat(hiredDeliveryToIdentity(matching)).as("delivery of EmployeeHired (%s) to a platform.identity listener", matching).isEqualTo("COMPLETED");
    }

    private int accountOpenedEvents(UUID account) throws Exception {
        List<String> eventIds = new ArrayList<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement select = registry.prepareStatement("select serialized_event from " + table + " where event_type like ? and serialized_event like ?")) {
                    select.setString(1, "%.AccountOpened");
                    select.setString(2, "%\"aggregateId\":\"" + account + "\"%");
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            String event = rs.getString(1);
                            int at = event.indexOf("\"eventId\":\"") + "\"eventId\":\"".length();
                            String id = event.substring(at, event.indexOf('"', at));
                            if (!eventIds.contains(id)) {
                                eventIds.add(id);
                            }
                        }
                    }
                }
            }
        }
        return eventIds.size();
    }

    /** AccountOpened events in the registry whose payload names the employee. */
    private int accountOpenedEventsOfEmployee(UUID employee) throws Exception {
        int found = 0;
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement select = registry.prepareStatement("select count(*) from " + table + " where event_type like '%.AccountOpened' and serialized_event like ?")) {
                    select.setString(1, "%" + text(employee) + "%");
                    try (ResultSet rs = select.executeQuery()) {
                        rs.next();
                        found += rs.getInt(1);
                    }
                }
            }
        }
        return found;
    }

    /** The grants of the account in the grant store as "role|channel|open", ordered. */
    private List<String> grantsOf(UUID account) throws Exception {
        List<String> grants = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement("""
                select r.code, g.grant_channel_kind, g.effective_to_at is null as open
                  from account_role_grants g join roles r on r.role_id = g.role_id
                 where g.user_id = ? order by r.code, g.effective_from_at""")) {
            select.setObject(1, account);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    grants.add(rs.getString(1) + "|" + rs.getString(2) + "|" + (rs.getBoolean(3) ? "open" : "closed"));
                }
            }
        }
        return grants;
    }

    /**
     * RULE-344 「并审计」: the audit entry the open base-role grant names exists and is an identity action, read through the audit query by
     * the account or by the employee as subject.
     */
    private void theBaseGrantIsAudited(UUID account, UUID employee) throws Exception {
        UUID entry;
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement("""
                select g.grant_audit_entry_id from account_role_grants g join roles r on r.role_id = g.role_id
                 where g.user_id = ? and r.code = ? and g.effective_to_at is null""")) {
            select.setObject(1, account);
            select.setString(2, BASE_ROLE);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).as("an open base-role grant").isTrue();
                entry = rs.getObject(1, UUID.class);
            }
        }
        List<String> actions = new ArrayList<>();
        for (String[] subject : List.of(new String[] {"USER_ACCOUNT", account.toString()}, new String[] {"EMPLOYEE", text(employee)})) {
            MockHttpServletResponse found = mvc.perform(get(AUDIT_ENTRIES).param("subjectKind", subject[0]).param("subjectId", subject[1])
                    .param("limit", "200").with(AUDIT_READER)).andReturn().getResponse();
            assertThat(found.getStatus()).as(found.getContentAsString()).isEqualTo(200);
            for (Map<String, Object> item : JsonPath.<List<Map<String, Object>>>read(found.getContentAsString(), "$.items")) {
                if (entry.toString().equals(item.get("entryId"))) {
                    actions.add(String.valueOf(item.get("action")));
                }
            }
        }
        assertThat(actions).as("the audit entry %s of the base-role grant", entry).isNotEmpty().allMatch(action -> action.startsWith("platform.identity."));
    }

    /** The identity operations command revoke-role for the employee's account; returns exit code and error output. */
    private String revokeRoleByOperations(UUID employee, String roleCode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ensureAttestors();
        int exit = operations.run(new DefaultApplicationArguments("--operation=revoke-role", "--employee-id=" + text(employee), "--role-code=" + roleCode,
                "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return (exit + " " + err.toString(StandardCharsets.UTF_8)).strip();
    }

    /** The publications to platform.identity listeners whose event names the text and that are not completed yet. */
    private int openIdentityPublications(String matching) throws Exception {
        int open = 0;
        try (Connection registry = schemaConnection("platform_integration"); PreparedStatement select = registry.prepareStatement("""
                select count(*) from event_publication
                 where listener_id like 'com.forcitis.one.platform.identity.%' and serialized_event like ? and completion_date is null""")) {
            select.setString(1, "%" + matching + "%");
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                open = rs.getInt(1);
            }
        }
        return open;
    }

    /**
     * Sets the employee separated: the snapshot row and the employee versions (employed until yesterday, separated from yesterday on), with an
     * assignment version so that a reader of the versions finds the person on the day, as separated, not as missing.
     */
    private void separatedYesterday(UUID person) throws Exception {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        LocalDate yesterday = TODAY.minusDays(1);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set employment_status = 'SEPARATED', separated_at = ? where id = ?", at.minusDays(1), stored(person));
            update(workforce, """
                    insert into employee_versions (employee_version_id, employee_id, effective_from_date, effective_to_date, closed_at, revision_number,
                      employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                      change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, ?, ?, 0, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?)""",
                    com.forcitis.one.kernel.Uuids.timeOrdered(NOW), stored(person), SINCE, yesterday, at, SINCE, at, at);
            update(workforce, """
                    insert into employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                      is_on_unpaid_leave, is_fee_earner, change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, 0, 'SEPARATED', false, false, 'MIGRATION', ?, ?)""",
                    com.forcitis.one.kernel.Uuids.timeOrdered(NOW), stored(person), yesterday, at, at);
            UUID root = AssignmentRoots.primaryOf(workforce, stored(person));
            update(workforce, AssignmentRoots.withRoot("""
                    insert into assignment_versions (assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id,
                      change_channel_kind, created_at, updated_at) values (?, ?, ?, ?, ?, 'MIGRATION', ?, ?)""", root),
                    AssignmentRoots.arguments(root, com.forcitis.one.kernel.Uuids.timeOrdered(NOW), stored(person), SINCE, stored(unit), newPosition(), at, at));
        }
    }

    private String wecomColumn(UUID employee) throws Exception {
        return (String) root(employee).get("wecom_user_id");
    }

    /** The one account of the employee, with one AccountOpened. */
    private UUID theOneAccount(UUID employee) throws Exception {
        List<UUID> accounts = accountsOfEmployee(employee);
        assertThat(accounts).as("accounts of employee %s", employee).hasSize(1);
        UUID account = accounts.getFirst();
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> accountOpenedEvents(account) >= 1);
        assertThat(accountOpenedEvents(account)).as("AccountOpened events of %s", account).isEqualTo(1);
        return account;
    }

    private static boolean isConstraintFailure(ILoggingEvent event) {
        StringBuilder text = new StringBuilder(event.getFormattedMessage());
        for (IThrowableProxy thrown = event.getThrowableProxy(); thrown != null; thrown = thrown.getCause()) {
            text.append(' ').append(thrown.getClassName()).append(' ').append(thrown.getMessage());
        }
        String all = text.toString();
        return all.contains("duplicate key") || all.contains("uk_user_accounts") || all.contains("DuplicateKey") || all.contains("DataIntegrityViolation");
    }

    private static <T> Callable<T> waiting(CountDownLatch start, Callable<T> work) {
        return () -> {
            start.await();
            return work.call();
        };
    }

    // ---- cases ----

    @Test
    @DisplayName("#1015 入职生效即开账号：经人事命令入口录入、今天生效的入职 → 身份模块处理完 EmployeeHired，该员工恰有一个账号、一条 AccountOpened，基础角色 employee 恰一条开放授予、渠道 DEFAULT_ROLE、有审计（RULE-344，ADR-0034 决定 2 第 3 项）")
    void aHireTakingEffectOpensTheAccountWithTheBaseRole() throws Exception {
        String wecom = syntheticWecom("hire");
        UUID command = hireOn(TODAY, wecom);
        UUID employee = employeeOf(command);
        assertThat(command(command).get("status")).as("premise: the hire took effect").isEqualTo(EFFECTIVE);

        identityHandledEmployeeHired(text(employee));

        UUID account = theOneAccount(employee);
        assertThat(grantsOf(account)).as("grants of the new account").containsExactly(BASE_ROLE + "|" + DEFAULT_ROLE_CHANNEL + "|open");
        theBaseGrantIsAudited(account, employee);
    }

    @Test
    @DisplayName("#1015 幂等（RULE-344 只在建档时授一次）：开户后用运维命令收回基础角色，再把同一个 EmployeeHired 对象发布两次、为同一命令另发一条 → 仍只有一个账号、一条 AccountOpened，基础角色不被补回，授予与收回后相同")
    void aRepeatedEmployeeHiredOpensNothingMore() throws Exception {
        UUID command = hireOn(TODAY, syntheticWecom("replay"));
        UUID employee = employeeOf(command);
        identityHandledEmployeeHired(text(employee));
        UUID account = theOneAccount(employee);
        assertThat(revokeRoleByOperations(employee, BASE_ROLE)).as("exit code and error output of revoke-role").isEqualTo("0");
        List<String> grants = grantsOf(account);
        assertThat(grants).as("premise: the base role is no longer held").noneMatch(grant -> grant.equals(BASE_ROLE + "|" + DEFAULT_ROLE_CHANNEL + "|open"));

        // a redelivery is the same event (same envelope, same eventId) as a new object, the way the registry deserialises it again
        EmployeeHired again = employeeHired(command, employee, TODAY);
        UUID first = publishEmployeeHired(again);
        UUID second = publishEmployeeHired(new EmployeeHired(again.envelope(), again.personnelCommandId(), again.employeeId(), again.effectiveDate()));
        assertThat(second).as("premise: the redelivery carries the same eventId").isEqualTo(first);
        UUID another = publishEmployeeHired(employeeHired(command, employee, TODAY));
        for (UUID event : List.of(first, second, another)) {
            identityHandledEmployeeHired(event.toString());
        }

        assertThat(theOneAccount(employee)).isEqualTo(account);
        assertThat(grantsOf(account)).as("grants after the redeliveries: the removed base role stays removed").isEqualTo(grants);
    }

    @Test
    @DisplayName("#1015 并发（P2-M3、ADR-0033 第 3 项第 1 条）：同一新员工同时经 EmployeeHired、企业微信登录、运维 grant-role 三个入口建账号，多轮 → 只有一个账号、恰一条 AccountOpened、基础角色恰一条开放授予、三个入口都成功、没有唯一约束冲突、身份模块对 EmployeeHired 的投递完成")
    void threeEntriesAtOnceOpenOneAccount() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                UUID person = newEmployeeId();
                employee(person, unit, null);
                UUID command = UUID.randomUUID();
                EmployeeHired hired = employeeHired(command, person, TODAY);
                String code = wecomCodeFor(person);
                int mark = errors.list.size();
                CountDownLatch start = new CountDownLatch(1);
                Future<UUID> hire = pool.submit(waiting(start, () -> publishEmployeeHired(hired)));
                Future<SignedIn> login = pool.submit(waiting(start, () -> signInWith(code, person)));
                Future<String> granted = pool.submit(waiting(start, () -> grantRoleByOperations(person)));
                start.countDown();

                UUID account = login.get(60, TimeUnit.SECONDS).userId();
                assertThat(granted.get(60, TimeUnit.SECONDS)).as("exit code and error output of grant-role").isEqualTo("0");
                UUID hiredEvent = hire.get(60, TimeUnit.SECONDS);
                identityHandledEmployeeHired(hiredEvent.toString());

                assertThat(accountsOfEmployee(person)).as("accounts of employee %s", person).containsExactly(account);
                assertThat(theOneAccount(person)).isEqualTo(account);
                assertThat(grantsOf(account).stream().filter(grant -> grant.startsWith(BASE_ROLE + "|")).toList()).as("base role grants")
                        .containsExactly(BASE_ROLE + "|" + DEFAULT_ROLE_CHANNEL + "|open");
                assertThat(List.copyOf(errors.list).subList(mark, errors.list.size()).stream()
                        .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).filter(HireOpensAccountIT::isConstraintFailure)
                        .map(ILoggingEvent::getFormattedMessage).toList()).as("unique constraint failures logged").isEmpty();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("#1015 已有账号（入职事件前已登录过，基础角色已被运维命令收回）：EmployeeHired 不建第二个账号、不发第二条 AccountOpened，不补回基础角色（RULE-344），授予不变，投递完成")
    void anExistingAccountIsLeftAsItIs() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        UUID account = signIn(person).userId();
        UUID existing = theOneAccount(person);
        assertThat(revokeRoleByOperations(person, BASE_ROLE)).as("exit code and error output of revoke-role").isEqualTo("0");
        List<String> grants = grantsOf(account);
        assertThat(grants).as("premise: the base role is no longer held").noneMatch(grant -> grant.equals(BASE_ROLE + "|" + DEFAULT_ROLE_CHANNEL + "|open"));

        UUID event = publishEmployeeHired(employeeHired(UUID.randomUUID(), person, TODAY));
        identityHandledEmployeeHired(event.toString());

        assertThat(existing).isEqualTo(account);
        assertThat(theOneAccount(person)).isEqualTo(account);
        assertThat(grantsOf(account)).as("grants after the hire event: the removed base role stays removed").isEqualTo(grants);
    }

    @Test
    @DisplayName("#1015 已离职、尚无账号的员工收到 EmployeeHired（如离职后迟到的重投）：身份模块处理完后仍无账号、无 AccountOpened（I-11、RULE-344 只给在职员工）")
    void aSeparatedEmployeeGetsNoAccount() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        separatedYesterday(person);
        assertThat(accountsOfEmployee(person)).as("premise: no account").isEmpty();

        UUID event = publishEmployeeHired(employeeHired(UUID.randomUUID(), person, SINCE));
        identityHandledEmployeeHired(event.toString());

        assertThat(accountsOfEmployee(person)).as("accounts of the separated employee").isEmpty();
        assertThat(accountOpenedEventsOfEmployee(person)).as("AccountOpened events naming the separated employee").isZero();
    }

    @Test
    @DisplayName("#1015 未来日期的入职（对照）：录入后、生效日之前不开账号")
    void aFutureHireOpensNoAccountBeforeItsDate() throws Exception {
        UUID command = hireOn(TODAY.plusDays(5), syntheticWecom("future"));
        UUID employee = employeeOf(command);
        runDueJob();
        assertThat(command(command).get("status")).as("premise: the hire waits for its date").isEqualTo(PENDING);
        // the registry records a publication in the publishing transaction: with none open for this employee, no identity work is still to come
        Awaitility.await().atMost(DELIVERY).pollInterval(Duration.ofMillis(100)).until(() -> openIdentityPublications(text(employee)) == 0);

        assertThat(accountsOfEmployee(employee)).as("accounts before the effective date").isEmpty();
    }

    @Test
    @DisplayName("#1015 未来日期的入职：生效日到、生效日作业跑过后开账号，恰一个账号、一条 AccountOpened、基础角色一条 DEFAULT_ROLE 授予")
    void aFutureHireOpensTheAccountOnceItTakesEffect() throws Exception {
        LocalDate due = TODAY.plusDays(5);
        UUID command = hireOn(due, syntheticWecom("due"));
        UUID employee = employeeOf(command);
        Awaitility.await().atMost(DELIVERY).pollInterval(Duration.ofMillis(100)).until(() -> openIdentityPublications(text(employee)) == 0);
        assertThat(accountsOfEmployee(employee)).as("premise: no account before the date").isEmpty();

        setToday(due);
        runDueJob();
        assertThat(command(command).get("status")).as("premise: the due-date job made the hire take effect").isEqualTo(EFFECTIVE);
        identityHandledEmployeeHired(text(employee));

        UUID account = theOneAccount(employee);
        assertThat(grantsOf(account)).containsExactly(BASE_ROLE + "|" + DEFAULT_ROLE_CHANNEL + "|open");
        theBaseGrantIsAudited(account, employee);
    }

    @Test
    @DisplayName("#1015 开账号不写企业微信账号：入职生效、身份模块处理完 EmployeeHired 前后，该员工 employees.wecom_user_id 不变")
    void handlingTheHireWritesNoWecomId() throws Exception {
        String wecom = syntheticWecom("keep");
        UUID employee = employeeOf(hireOn(TODAY, wecom));
        String before = wecomColumn(employee);
        assertThat(before).as("premise: the hire stored the WeCom user id it was entered with").isEqualTo(wecom);

        identityHandledEmployeeHired(text(employee));

        // identity cannot write this schema itself (own-schema guard): this watches a call from the hire handling back into a workforce api
        assertThat(wecomColumn(employee)).as("employees.wecom_user_id after identity handled the hire").isEqualTo(before);
    }
}
