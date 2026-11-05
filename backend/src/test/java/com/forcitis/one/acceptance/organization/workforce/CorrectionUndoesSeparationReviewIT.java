package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeCorrected;
import com.forcitis.one.organization.workforce.events.EmployeeTransferred;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomCard;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #730 A-U1..A-U3 (one-design ruling on #730, comments 6599023412 and 6599341585; user decision 2026-11-03; ADR-0082 decision 2):
 * a correction that undoes a wrong separation (not employed before, employed today) reaches platform.identity as EmployeeCorrected with
 * triggerFieldKinds [ORG_UNIT], previous department and position null, new ones set. The listener raises one SENSITIVE_ROLE_REVIEW item for
 * the person, covering every sensitive role the person holds when it handles the event, and never a SCOPE_REVIEW item. A correction that ends
 * employment raises nothing and is not retried. The event is published as organization.workforce publishes it; the rows are read from
 * platform_identity.permission_adjustments by the columns of the F-1 card section 7.1.
 */
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/testdata")
class CorrectionUndoesSeparationReviewIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(CorrectionUndoesSeparationReviewIT.class, TestPostgres.MIGRATED_TESTDATA).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final Duration SETTLE = Duration.ofSeconds(20);
    static final LocalDate BACK_ON = TODAY.minusDays(1);
    static final String SECURITY_AUDITOR = "security_auditor";

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    /** An employed person whose account (opened by the first grant, or by signing in when no role is given) holds the given roles besides the employee role; answers the employee id. */
    private UUID employedHolding(String... roleCodes) throws Exception {
        UUID employee = employed(REGULAR);
        if (roleCodes.length == 0) {
            signIn(employee);
        }
        for (String role : roleCodes) {
            grantByOperations(employee, role);
        }
        return employee;
    }

    private UUID accountOf(UUID employee) throws SQLException {
        List<UUID> accounts = accountsOfEmployee(employee);
        assertThat(accounts).as("one account for the employee").hasSize(1);
        return accounts.getFirst();
    }

    /** The correction that undoes a wrong separation: ORG_UNIT only, nothing before, department and position today after (ADR-0082 decision 2). */
    private EmployeeCorrected undoneSeparation(UUID employee, UUID command, UUID toUnit, UUID toPosition) {
        return corrected(employee, command, List.of(TriggerFieldKind.ORG_UNIT), null, toUnit, null, toPosition);
    }

    /** A correction after which the person is not employed: the department and position today are gone, both fields differ. */
    private EmployeeCorrected endedEmployment(UUID employee, UUID command, UUID fromUnit, UUID fromPosition) {
        return corrected(employee, command, List.of(TriggerFieldKind.ORG_UNIT, TriggerFieldKind.POSITION), fromUnit, null, fromPosition, null);
    }

    private EmployeeCorrected corrected(UUID employee, UUID command, List<TriggerFieldKind> kinds, UUID previousUnit, UUID newUnit, UUID previousPosition,
                                        UUID newPosition) {
        EventEnvelope envelope = new EventEnvelope(Uuids.timeOrdered(NOW), "organization.workforce.EmployeeCorrected", 1, NOW, "Employee", employee, 1,
                Uuids.timeOrdered(NOW), null, ActorType.SYSTEM, com.forcitis.one.kernel.SystemActors.idOf("test.envelope"), new LegalEntityId(legalEntity),
                BACK_ON, null, List.of());
        return new EmployeeCorrected(envelope, new PersonnelCommandId(command), employeeIdOf(employee), BACK_ON, null, kinds,
                previousUnit == null ? null : new OrgUnitId(previousUnit), newUnit == null ? null : new OrgUnitId(newUnit),
                previousPosition == null ? null : new PositionId(previousPosition), newPosition == null ? null : new PositionId(newPosition));
    }

    private void publish(Object event) {
        transaction.executeWithoutResult(status -> publisher.publishEvent(event));
    }

    /**
     * Waits until platform.identity's listener has finished the event: an identity publication of it exists and none of its publications is
     * still open. A listener that throws and is retried, or never runs, leaves this waiting and the case red; the failure names the open ones.
     */
    private void identityHandled(EmployeeCorrected event) throws SQLException {
        identityHandled(event.envelope().eventId().toString());
    }

    private void identityHandled(String id) throws SQLException {
        try {
            Awaitility.await().atMost(SETTLE).pollInterval(Duration.ofMillis(100)).until(() -> {
                List<Map<String, Object>> publications = publicationsOf(id);
                long identity = publications.stream().filter(row -> String.valueOf(row.get("listener_id")).contains(".platform.identity.")).count();
                return identity > 0 && publications.stream().noneMatch(row -> row.get("completion_date") == null);
            });
        } catch (ConditionTimeoutException timeout) {
            throw new AssertionError("identity did not finish event " + id + " within " + SETTLE + "; publications of the event: " + publicationsOf(id), timeout);
        }
    }

    private List<Map<String, Object>> publicationsOf(String eventId) throws SQLException {
        List<Map<String, Object>> found = new java.util.ArrayList<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                found.addAll(query(registry, "select listener_id, status, completion_attempts, completion_date from " + table + " where serialized_event like ?",
                        "%" + eventId + "%"));
            }
        }
        return found;
    }

    private List<Map<String, Object>> itemsAbout(UUID user) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            return query(identity, """
                    select kind, origin_kind, status, trigger_field_kind, trigger_personnel_command_id, before_org_unit_id, after_org_unit_id,
                           before_position_id, after_position_id, role_id, recipient_employee_id
                      from permission_adjustments where user_id = ? order by created_at, permission_adjustment_id""", user);
        }
    }

    @Test
    @DisplayName("#730 A-U1 撤销误离职的更正：持有两个敏感角色的人得到恰好一条敏感角色复核（人级，不按角色分条），触发字段为部门，触发前部门、岗位为空，触发后为更正后的部门与岗位，触发命令为该更正命令；同一事件重投递两次、同一命令的另一事件都不再生成")
    void anUndoneSeparationGivesOnePersonItemForAHolderOfSensitiveRoles() throws Exception {
        UUID employee = employedHolding(SECURITY_AUDITOR, "business_admin");
        UUID user = accountOf(employee);
        assertThat(itemsAbout(user)).as("premise: nothing before the correction").isEmpty();
        UUID command = Uuids.timeOrdered(NOW);
        UUID toUnit = newUnit();
        UUID toPosition = newPosition();

        EmployeeCorrected event = undoneSeparation(employee, command, toUnit, toPosition);
        publish(event);
        identityHandled(event);

        List<Map<String, Object>> items = itemsAbout(user);
        assertThat(items).as("one item for the person, whatever number of sensitive roles").hasSize(1);
        Map<String, Object> item = items.getFirst();
        assertThat(item).containsEntry("kind", "SENSITIVE_ROLE_REVIEW").containsEntry("origin_kind", "ASSIGNMENT_CHANGE").containsEntry("status", "OPEN")
                .containsEntry("trigger_field_kind", "ORG_UNIT").containsEntry("trigger_personnel_command_id", command)
                .containsEntry("after_org_unit_id", toUnit).containsEntry("after_position_id", toPosition);
        assertThat(item.get("before_org_unit_id")).as("the person was not employed before").isNull();
        assertThat(item.get("before_position_id")).isNull();
        assertThat(item.get("role_id")).as("a person item names no role").isNull();
        assertThat(item.get("recipient_employee_id")).isNull();

        EmployeeCorrected again = undoneSeparation(employee, command, toUnit, toPosition);
        publish(event);
        publish(event);
        publish(again);
        identityHandled(event);
        identityHandled(again);
        assertThat(itemsAbout(user)).as("a redelivery or another event of the same command adds none").hasSize(1);
    }

    @Test
    @DisplayName("#730 A-U2 撤销误离职的更正：此刻不持有敏感角色（只有 employee）的人不生成条目；任何情况下都没有范围复核条目")
    void aPersonWithoutASensitiveRoleGetsNothingAndNobodyGetsAScopeReview() throws Exception {
        UUID plain = employedHolding();
        UUID plainUser = accountOf(plain);
        UUID holder = employedHolding(SECURITY_AUDITOR);
        UUID holderUser = accountOf(holder);

        EmployeeCorrected plainEvent = undoneSeparation(plain, Uuids.timeOrdered(NOW), newUnit(), newPosition());
        EmployeeCorrected holderEvent = undoneSeparation(holder, Uuids.timeOrdered(NOW), newUnit(), newPosition());
        publish(plainEvent);
        publish(holderEvent);
        identityHandled(plainEvent);
        identityHandled(holderEvent);

        assertThat(itemsAbout(plainUser)).as("no sensitive role, no item").isEmpty();
        assertThat(itemsAbout(holderUser)).as("the control person did get the sensitive-role item, so the listener ran").hasSize(1);
        for (UUID user : List.of(plainUser, holderUser)) {
            assertThat(itemsAbout(user)).extracting(item -> item.get("kind")).doesNotContain("SCOPE_REVIEW");
        }
    }

    @Test
    @DisplayName("#730 A-U3 更正后此人不在职（触发字段为部门与岗位、触发后部门与岗位为空）：持有敏感角色也不生成条目；监听器处理完成而不是失败重试，重投递仍不生成")
    void aCorrectionThatEndsEmploymentGivesNothingAndIsNotRetried() throws Exception {
        UUID employee = employedHolding(SECURITY_AUDITOR);
        UUID user = accountOf(employee);
        EmployeeCorrected event = endedEmployment(employee, Uuids.timeOrdered(NOW), newUnit(), newPosition());

        publish(event);
        identityHandled(event);
        publish(event);
        identityHandled(event);

        assertThat(itemsAbout(user)).isEmpty();
        List<Map<String, Object>> publications = publicationsOf(event.envelope().eventId().toString());
        assertThat(publications).as("every publication of the event completed once, none failed or was resubmitted")
                .allSatisfy(row -> assertThat(row).containsEntry("status", "COMPLETED").containsEntry("completion_attempts", 1));
    }

    @Test
    @DisplayName("#730 一次转岗事件缺触发前部门（空）：监听器不生成条目，记一条以 \"ALERT P2 platform.identity \" 开头的 ERROR 日志（恰好一条、含事件 id、不含此人姓名与工号），处理完成而不重试；持有敏感角色也一样（one-design 裁定，#730 评论 6600900803）")
    void aTransferWithoutItsBeforeDepartmentIsAlertedNotRetriedAndGivesNoItem() throws Exception {
        UUID employee = employedHolding(SECURITY_AUDITOR);
        UUID user = accountOf(employee);
        UUID command = Uuids.timeOrdered(NOW);
        EventEnvelope envelope = new EventEnvelope(Uuids.timeOrdered(NOW), "organization.workforce.EmployeeTransferred", 1, NOW, "Employee", employee, 1,
                Uuids.timeOrdered(NOW), null, ActorType.SYSTEM, com.forcitis.one.kernel.SystemActors.idOf("test.envelope"), new LegalEntityId(legalEntity),
                BACK_ON, null, List.of());
        EmployeeTransferred event = new EmployeeTransferred(envelope, new PersonnelCommandId(command), employeeIdOf(employee), BACK_ON,
                List.of(TriggerFieldKind.ORG_UNIT), null, new OrgUnitId(newUnit()), null, null);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        try {
            publish(event);
            identityHandled(envelope.eventId().toString());
        } finally {
            root.detachAppender(logged);
        }

        List<ILoggingEvent> alerts = logged.list.stream()
                .filter(line -> line.getLevel() == Level.ERROR && line.getFormattedMessage().startsWith("ALERT P2 platform.identity ")).toList();
        assertThat(alerts).as("exactly one alert line for the event").hasSize(1);
        String line = alerts.getFirst().getFormattedMessage();
        assertThat(line).as("the line names the event").contains(envelope.eventId().toString());
        Map<String, Object> person;
        try (Connection workforce = schemaConnection("organization_workforce")) {
            person = query(workforce, "select employee_code, display_name from employees where id = ?", stored(employee)).getFirst();
        }
        assertThat(line).as("no employee code and no name in the line").doesNotContain(String.valueOf(person.get("employee_code")))
                .doesNotContain(String.valueOf(person.get("display_name")));
        assertThat(itemsAbout(user)).as("no item for the refused event").isEmpty();
        assertThat(publicationsOf(envelope.eventId().toString())).as("delivery consumed, not failed or resubmitted")
                .isNotEmpty().allSatisfy(row -> assertThat(row).containsEntry("status", "COMPLETED").containsEntry("completion_attempts", 1));
    }

    // ---- real EmployeeTransferred (personnel command) and the escalation due date (one-design ruling A-6, #730 comment 6600968506) ----

    private static final java.time.ZoneId BUSINESS_ZONE = java.time.ZoneId.of("Asia/Shanghai");
    /**
     * The second weekday after TODAY is the entity's own holiday; the entity calendar is saved for that day's own year, so a year change needs no change here.
     * The due dates (five weekdays on) must fall in a year whose default calendar is maintained, which production requires too (an unmaintained year refuses).
     */
    private static final LocalDate HOLIDAY_OF_X = weekdaysAfter(TODAY, 2, java.util.Set.of());

    /** The day that is the n-th weekday after the start, skipping Saturdays, Sundays and the given holidays (the default calendar has weekends off only). */
    private static LocalDate weekdaysAfter(LocalDate start, int n, java.util.Set<LocalDate> holidays) {
        LocalDate day = start;
        for (int counted = 0; counted < n; ) {
            day = day.plusDays(1);
            if (day.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && day.getDayOfWeek() != java.time.DayOfWeek.SUNDAY && !holidays.contains(day)) {
                counted++;
            }
        }
        return day;
    }

    /** Instant of the local midnight that starts the given day in the business zone. */
    private static java.time.Instant startOf(LocalDate day) {
        return day.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    /** The legal entity gets its own 2026 calendar with one weekday holiday, which the default calendar (weekends off only) does not have. */
    private void givenOwnCalendarWithAHoliday(UUID entity, LocalDate holiday) throws SQLException {
        UUID year = Uuids.timeOrdered(NOW);
        try (Connection attendance = schemaConnection("organization_attendance")) {
            update(attendance, "insert into work_calendar_years (id, legal_entity_id, year_number) values (?, ?, ?)", year, stored(entity), holiday.getYear());
            update(attendance, "insert into work_calendar_days (id, work_calendar_year_id, calendar_date, day_kind) values (?, ?, ?, 'HOLIDAY')",
                    Uuids.timeOrdered(NOW), year, holiday);
        }
    }

    /** The fifth workday after TODAY ends the escalation: by the default calendar, or with HOLIDAY_OF_X off by the entity's own calendar. */
    private void assertDueAtEndOfDay(Object due, LocalDate fifthWorkday) {
        java.time.Instant at = instantOf(due);
        assertThat(at).as("the due instant is the end of " + fifthWorkday + " in the business zone").isAfterOrEqualTo(startOf(fifthWorkday.plusDays(1)).minusSeconds(1))
                .isBeforeOrEqualTo(startOf(fifthWorkday.plusDays(1)));
    }

    private Map<String, Object> dueRowAbout(UUID user) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            return query(identity, "select escalation_due_at from permission_adjustments where user_id = ?", user).getFirst();
        }
    }

    private void awaitItemsAbout(UUID user, int count) {
        Awaitility.await().atMost(SETTLE).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> assertThat(itemsAbout(user)).hasSize(count));
    }

    private EmployeeTransferred transferredIn(UUID employee, UUID command, UUID fromUnit, UUID toUnit, @org.jspecify.annotations.Nullable UUID entity) {
        EventEnvelope envelope = new EventEnvelope(Uuids.timeOrdered(NOW), "organization.workforce.EmployeeTransferred", 1, NOW, "Employee", employee, 1,
                Uuids.timeOrdered(NOW), null, ActorType.SYSTEM, com.forcitis.one.kernel.SystemActors.idOf("test.envelope"),
                entity == null ? null : new LegalEntityId(entity), BACK_ON, null, List.of());
        return new EmployeeTransferred(envelope, new PersonnelCommandId(command), employeeIdOf(employee), BACK_ON, List.of(TriggerFieldKind.ORG_UNIT),
                new OrgUnitId(fromUnit), new OrgUnitId(toUnit), null, null);
    }

    @Test
    @DisplayName("#730 真实转岗 + A-6(a)：人事专员对持有敏感角色的人提交转岗命令，生成恰好一条敏感角色复核（触发字段部门、触发前为原部门、触发后为目标部门、触发命令为该命令）；法人有自有日历且某个工作日为假日时，escalation_due_at 是该法人日历的第 5 个工作日结束（11-10），不是默认日历的（11-09）")
    void aRealTransferOfAHolderGivesOneItemDueByTheEntitysOwnCalendar() throws Exception {
        givenOwnCalendarWithAHoliday(legalEntity, HOLIDAY_OF_X);
        UUID employee = employedHolding(SECURITY_AUDITOR);
        UUID user = accountOf(employee);
        UUID toUnit = newUnit();

        UUID command = transfer(employee, TODAY, toUnit);

        awaitItemsAbout(user, 1);
        Map<String, Object> item = itemsAbout(user).getFirst();
        assertThat(item).containsEntry("kind", "SENSITIVE_ROLE_REVIEW").containsEntry("origin_kind", "ASSIGNMENT_CHANGE").containsEntry("status", "OPEN")
                .containsEntry("trigger_field_kind", "ORG_UNIT").containsEntry("trigger_personnel_command_id", command)
                .containsEntry("before_org_unit_id", unit).containsEntry("after_org_unit_id", toUnit);
        assertThat(item.get("recipient_employee_id")).as("a person item has no recipient").isNull();
        assertDueAtEndOfDay(dueRowAbout(user).get("escalation_due_at"), weekdaysAfter(TODAY, 5, java.util.Set.of(HOLIDAY_OF_X)));
    }

    @Test
    @DisplayName("#730 真实转岗：只有 employee 角色的人转岗不生成条目（对照：同一时刻持有敏感角色的人转岗生成一条，证明监听器在跑）")
    void aRealTransferOfAPersonWithoutASensitiveRoleGivesNothing() throws Exception {
        UUID plain = employedHolding();
        UUID plainUser = accountOf(plain);
        UUID holder = employedHolding(SECURITY_AUDITOR);
        UUID holderUser = accountOf(holder);

        transfer(plain, TODAY, newUnit());
        transfer(holder, TODAY, newUnit());

        awaitItemsAbout(holderUser, 1);
        assertThat(itemsAbout(plainUser)).isEmpty();
    }

    @Test
    @DisplayName("#730 A-6(a) 对照：同样的转岗事件，信封法人是没有自有日历的另一个法人，escalation_due_at 按默认日历（周末休息、无假日）是第 5 个工作日结束（11-09）；日历不跨法人套用")
    void anEntityWithoutItsOwnCalendarUsesTheDefaultOne() throws Exception {
        givenOwnCalendarWithAHoliday(legalEntity, HOLIDAY_OF_X);
        UUID otherEntity = createLegalEntity(administrator, newLegalEntityCode("PD"), "Synthetic entity without calendar");
        UUID employee = employedHolding(SECURITY_AUDITOR);
        UUID user = accountOf(employee);
        EmployeeTransferred event = transferredIn(employee, Uuids.timeOrdered(NOW), unit, newUnit(), otherEntity);

        publish(event);
        identityHandled(event.envelope().eventId().toString());

        assertThat(itemsAbout(user)).hasSize(1);
        assertDueAtEndOfDay(dueRowAbout(user).get("escalation_due_at"), weekdaysAfter(TODAY, 5, java.util.Set.of()));
    }

    @Test
    @DisplayName("#730 A-6(c)：信封不带法人时条目仍然生成，escalation_due_at 按默认日历（11-09），并恰好一条以 \"ALERT P3 platform.identity \" 开头的日志，含事件 id 与条目 id、不含工号与姓名（#730 评论 6605327331）；事件处理完成不重试")
    void anUnresolvableEntityStillGivesTheItemWithTheDefaultCalendarAndOneP3Alert() throws Exception {
        givenOwnCalendarWithAHoliday(legalEntity, HOLIDAY_OF_X);
        UUID employee = employedHolding(SECURITY_AUDITOR);
        UUID user = accountOf(employee);
        EmployeeTransferred event = transferredIn(employee, Uuids.timeOrdered(NOW), unit, newUnit(), null);
        String eventId = event.envelope().eventId().toString();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        try {
            publish(event);
            identityHandled(eventId);
        } finally {
            root.detachAppender(logged);
        }

        assertThat(itemsAbout(user)).as("a lost review is worse than an approximate date").hasSize(1);
        assertDueAtEndOfDay(dueRowAbout(user).get("escalation_due_at"), weekdaysAfter(TODAY, 5, java.util.Set.of()));
        List<ILoggingEvent> alerts = logged.list.stream().filter(line -> line.getFormattedMessage().startsWith("ALERT P3 platform.identity ")).toList();
        assertThat(alerts).as("exactly one P3 alert line").hasSize(1);
        String line = alerts.getFirst().getFormattedMessage();
        assertThat(line).contains(eventId).contains(String.valueOf(dueItemId(user)));
        Map<String, Object> person;
        try (Connection workforce = schemaConnection("organization_workforce")) {
            person = query(workforce, "select employee_code, display_name from employees where id = ?", stored(employee)).getFirst();
        }
        assertThat(line).doesNotContain(String.valueOf(person.get("employee_code"))).doesNotContain(String.valueOf(person.get("display_name")));
        assertThat(publicationsOf(eventId)).isNotEmpty().allSatisfy(row -> assertThat(row).containsEntry("status", "COMPLETED").containsEntry("completion_attempts", 1));
    }

    @Test
    @DisplayName("#730 A-6(d)：permission_adjustments 没有法人列（升级期限在生成时存好，之后不需要法人）")
    void theTableHasNoLegalEntityColumn() throws Exception {
        try (Connection identity = schemaConnection("platform_identity")) {
            assertThat(query(identity, "select column_name from information_schema.columns where table_schema = 'platform_identity' and table_name = 'permission_adjustments' and column_name like '%legal_entity%'"))
                    .isEmpty();
        }
    }

    // ---- SCOPE_REVIEW from a transfer (one-design ruling, #730 comment 6602688198): one item per (person, command, ORG_UNIT) for a holder of a scoped grant ----

    private static final String ORG_UNIT_READ = "organization.workforce:org-unit:read";
    private static final String OPENED = "platform.identity.PermissionAdjustmentOpened";

    /** An ordinary role (no sensitive permission) created through the role api by a super administrator; answers its code. */
    private String roleWithScope(String code, String accessScope, UUID... customUnits) throws Exception {
        SignedIn superAdministrator = administratorMode(signIn(superAdministratorEmployee()));
        String units = customUnits.length == 0 ? "" : ",\"customOrgUnits\":["
                + String.join(",", java.util.Arrays.stream(customUnits).map(u -> "{\"orgUnitId\":\"" + u + "\",\"includeDescendants\":false}").toList()) + "]";
        String body = "{\"code\":\"" + code + "\",\"name\":\"Synthetic role " + code + "\",\"enabled\":true,\"accessScope\":\"" + accessScope + "\"" + units
                + ",\"permissionCodes\":[\"" + ORG_UNIT_READ + "\"]}";
        int status = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/platform/roles")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).with(session(superAdministrator))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        assertThat(status).as("fixture: creating role " + code).isEqualTo(201);
        return code;
    }

    private UUID superAdministratorEmployee() throws Exception {
        UUID id = newEmployeeId();
        employee(id, unit, null);
        grantByOperations(id, "super_admin");
        return id;
    }

    private String customRole(String code, UUID... units) throws Exception {
        return roleWithScope(code + Uuids.timeOrdered(NOW).toString().substring(24), "CUSTOM_ORG_UNITS", units);
    }

    private void headOf(UUID orgUnit, UUID head) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update org_units set head_employee_id = ? where id = ?", stored(head), stored(orgUnit));
        }
    }

    /** The assigned cards sent about the item through the WeCom gateway (a mock in acceptance tests, so nothing reaches the delivery table); recipients are the WeCom user ids joined by commas. */
    private List<Map<String, Object>> assignedCardsAbout(Object item) {
        ArgumentCaptor<WecomCard> captured = ArgumentCaptor.forClass(WecomCard.class);
        Mockito.verify(wecomGateway, Mockito.atLeast(0)).send(captured.capture());
        List<Map<String, Object>> cards = new java.util.ArrayList<>();
        for (WecomCard card : captured.getAllValues()) {
            if ("PERMISSION_ADJUSTMENT_ASSIGNED".equals(card.subject().purpose().name()) && card.subject().id().toString().equals(String.valueOf(item))) {
                cards.add(Map.of("recipients", String.join(",", card.wecomUserIds().stream().map(WecomUserId::value).toList())));
            }
        }
        return cards;
    }

    private String storedWecomUserId(UUID employee) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            return (String) query(workforce, "select wecom_user_id from employees where id = ?", stored(employee)).getFirst().get("wecom_user_id");
        }
    }

    /** The envelope event id of the EmployeeTransferred a command published (written in the publishing transaction, so nothing is awaited). */
    private String transferEventIdOf(UUID command) throws SQLException {
        java.util.regex.Pattern eventId = java.util.regex.Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"");
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                for (Map<String, Object> row : query(registry, "select serialized_event from " + table + " where event_type like ? and serialized_event like ?",
                        "%.EmployeeTransferred", "%" + command + "%")) {
                    java.util.regex.Matcher found = eventId.matcher((String) row.get("serialized_event"));
                    if (found.find()) {
                        return found.group(1);
                    }
                }
            }
        }
        throw new AssertionError("no EmployeeTransferred published for command " + command);
    }

    private int openedAudits() throws Exception {
        return auditCount(OPENED);
    }

    private static List<Object> kindsOf(List<Map<String, Object>> items) {
        return items.stream().map(item -> item.get("kind")).toList();
    }

    @Test
    @DisplayName("#730 SCOPE_REVIEW 1：持有自定义部门角色的人真实转岗（部门变化），生成恰好一条范围复核（来源为任职变动、触发命令为该命令、触发字段部门、触发前为原部门、触发后为新部门、接收人为新部门负责人），并有恰好一张 PERMISSION_ADJUSTMENT_ASSIGNED 外发卡和一条 PermissionAdjustmentOpened 审计")
    void aRealTransferOfACustomOrgUnitHolderGivesOneScopeReviewForTheNewHead() throws Exception {
        UUID head = employed(REGULAR);
        UUID toUnit = newUnit();
        headOf(toUnit, head);
        UUID employee = employedHolding();
        grantByOperations(employee, customRole("scoped-one", unit));
        UUID user = accountOf(employee);
        int auditsBefore = openedAudits();

        UUID command = transfer(employee, TODAY, toUnit);

        awaitItemsAbout(user, 1);
        Map<String, Object> item = itemsAbout(user).getFirst();
        assertThat(item).containsEntry("kind", "SCOPE_REVIEW").containsEntry("origin_kind", "ASSIGNMENT_CHANGE").containsEntry("status", "OPEN")
                .containsEntry("trigger_field_kind", "ORG_UNIT").containsEntry("trigger_personnel_command_id", command)
                .containsEntry("before_org_unit_id", unit).containsEntry("after_org_unit_id", toUnit).containsEntry("recipient_employee_id", head);
        assertThat(item.get("role_id")).as("the item is about the person, not a role").isNull();
        Object itemId = dueItemId(user);
        Awaitility.await().atMost(SETTLE).untilAsserted(() -> {
            List<Map<String, Object>> cards = assignedCardsAbout(itemId);
            assertThat(cards).as("one assigned card to the new head, in the same transaction").hasSize(1);
            assertThat(cards.getFirst().get("recipients")).as("the card goes to exactly the new head's WeCom user id").isEqualTo(storedWecomUserId(head));
            assertThat(openedAudits() - auditsBefore).isEqualTo(1);
        });
    }

    private Object dueItemId(UUID user, String kind) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            return query(identity, "select permission_adjustment_id from permission_adjustments where user_id = ? and kind = ?", user, kind).getFirst().get("permission_adjustment_id");
        }
    }

    private Object dueItemId(UUID user) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            return query(identity, "select permission_adjustment_id from permission_adjustments where user_id = ?", user).getFirst().get("permission_adjustment_id");
        }
    }

    @Test
    @DisplayName("#730 SCOPE_REVIEW 3：持有两个自定义部门角色的人转岗，仍然只有一条范围复核（人级、一次命令一条，不按角色分条）")
    void twoScopedGrantsStillGiveOneScopeReview() throws Exception {
        UUID head = employed(REGULAR);
        UUID toUnit = newUnit();
        headOf(toUnit, head);
        UUID employee = employedHolding();
        grantByOperations(employee, customRole("scoped-a", unit));
        grantByOperations(employee, customRole("scoped-b", toUnit));
        UUID user = accountOf(employee);
        UUID control = employedHolding(SECURITY_AUDITOR);
        UUID controlUser = accountOf(control);

        transfer(employee, TODAY, toUnit);
        transfer(control, TODAY, newUnit());

        awaitItemsAbout(controlUser, 1);
        Awaitility.await().atMost(SETTLE).untilAsserted(() -> assertThat(itemsAbout(user)).hasSize(1));
        assertThat(kindsOf(itemsAbout(user))).containsExactly("SCOPE_REVIEW");
        assertThat(itemsAbout(user).getFirst().get("role_id")).isNull();
    }

    @Test
    @DisplayName("#730 SCOPE_REVIEW 4：只有全公司或本部门类数据范围角色的人转岗不生成范围复核（对照：同一人还持有敏感角色，得到恰好一条敏感角色复核，证明监听器在跑）")
    void aHolderOfOnlyNonScopedGrantsGetsNoScopeReview() throws Exception {
        UUID employee = employedHolding(SECURITY_AUDITOR);
        grantByOperations(employee, roleWithScope("whole-" + Uuids.timeOrdered(NOW).toString().substring(24), "ALL"));
        grantByOperations(employee, roleWithScope("own-unit-" + Uuids.timeOrdered(NOW).toString().substring(24), "OWN_ORG_UNIT"));
        UUID user = accountOf(employee);

        UUID command = transfer(employee, TODAY, newUnit());

        awaitItemsAbout(user, 1);
        identityHandled(transferEventIdOf(command));
        assertThat(kindsOf(itemsAbout(user))).containsExactly("SENSITIVE_ROLE_REVIEW");
    }

    @Test
    @DisplayName("#730 SCOPE_REVIEW 5：只改岗位（触发字段只有岗位）的转岗事件，持有自定义部门角色的人不生成范围复核；同时持有敏感角色时仍得到恰好一条敏感角色复核")
    void aPositionOnlyTransferGivesNoScopeReview() throws Exception {
        UUID employee = employedHolding(SECURITY_AUDITOR);
        grantByOperations(employee, customRole("scoped-pos", unit));
        UUID user = accountOf(employee);
        EventEnvelope envelope = new EventEnvelope(Uuids.timeOrdered(NOW), "organization.workforce.EmployeeTransferred", 1, NOW, "Employee", employee, 1,
                Uuids.timeOrdered(NOW), null, ActorType.SYSTEM, com.forcitis.one.kernel.SystemActors.idOf("test.envelope"), new LegalEntityId(legalEntity),
                BACK_ON, null, List.of());
        EmployeeTransferred event = new EmployeeTransferred(envelope, new PersonnelCommandId(Uuids.timeOrdered(NOW)), employeeIdOf(employee), BACK_ON,
                List.of(TriggerFieldKind.POSITION), new OrgUnitId(unit), new OrgUnitId(unit), new PositionId(newPosition()), new PositionId(newPosition()));

        publish(event);
        identityHandled(envelope.eventId().toString());

        assertThat(kindsOf(itemsAbout(user))).as("only the sensitive role is reviewed").containsExactly("SENSITIVE_ROLE_REVIEW");
    }

    @Test
    @DisplayName("#730 SCOPE_REVIEW 6：同一转岗事件重投递两次、同一命令的另一事件，仍然只有一条范围复核和一张外发卡")
    void aReplayGivesOneScopeReviewAndOneCard() throws Exception {
        UUID head = employed(REGULAR);
        UUID toUnit = newUnit();
        headOf(toUnit, head);
        UUID employee = employedHolding();
        grantByOperations(employee, customRole("scoped-replay", unit));
        UUID user = accountOf(employee);
        UUID command = Uuids.timeOrdered(NOW);
        EmployeeTransferred event = transferredIn(employee, command, unit, toUnit, legalEntity);
        EmployeeTransferred another = transferredIn(employee, command, unit, toUnit, legalEntity);

        publish(event);
        identityHandled(event.envelope().eventId().toString());
        publish(event);
        publish(another);
        identityHandled(another.envelope().eventId().toString());
        identityHandled(event.envelope().eventId().toString());

        assertThat(itemsAbout(user)).hasSize(1);
        assertThat(assignedCardsAbout(dueItemId(user))).hasSize(1);
    }

    @Test
    @DisplayName("#730 SCOPE_REVIEW 7：新部门没有负责人时仍生成范围复核，接收人为空，没有外发卡，并恰好一条以 \"ALERT P3 platform.identity \" 开头、含事件 id 与条目 id、不含工号与姓名的日志（#730 评论 6605327331）")
    void aNewDepartmentWithoutAHeadGivesAnItemWithoutRecipientCardOrNothingButOneP3Alert() throws Exception {
        UUID employee = employedHolding();
        grantByOperations(employee, customRole("scoped-nohead", unit));
        UUID user = accountOf(employee);
        EmployeeTransferred event = transferredIn(employee, Uuids.timeOrdered(NOW), unit, newUnit(), legalEntity);
        String eventId = event.envelope().eventId().toString();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        try {
            publish(event);
            identityHandled(eventId);
        } finally {
            root.detachAppender(logged);
        }

        List<Map<String, Object>> items = itemsAbout(user);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst()).containsEntry("kind", "SCOPE_REVIEW");
        assertThat(items.getFirst().get("recipient_employee_id")).isNull();
        assertThat(assignedCardsAbout(dueItemId(user))).as("no recipient, no delivery").isEmpty();
        List<ILoggingEvent> alerts = logged.list.stream().filter(line -> line.getFormattedMessage().startsWith("ALERT P3 platform.identity ")).toList();
        assertThat(alerts).as("exactly one P3 alert line").hasSize(1);
        Map<String, Object> person;
        try (Connection workforce = schemaConnection("organization_workforce")) {
            person = query(workforce, "select employee_code, display_name from employees where id = ?", stored(employee)).getFirst();
        }
        assertThat(alerts.getFirst().getFormattedMessage()).contains(eventId).contains(String.valueOf(dueItemId(user))).doesNotContain(String.valueOf(person.get("employee_code")))
                .doesNotContain(String.valueOf(person.get("display_name")));
    }

    @Test
    @DisplayName("#730 SCOPE_REVIEW 7b：新部门的负责人就是被转岗的人本人时，与没有负责人相同：范围复核接收人为空，没有外发卡，并恰好一条以 \"ALERT P3 platform.identity \" 开头、含事件 id 与条目 id、不含工号与姓名的日志（one-design 裁定，#730 评论 6604945364 与 6605327331）")
    void aMovedPersonWhoHeadsTheNewDepartmentIsTreatedLikeNoHead() throws Exception {
        UUID employee = employedHolding();
        grantByOperations(employee, customRole("scoped-self-head", unit));
        UUID user = accountOf(employee);
        UUID toUnit = headedUnit(employee);
        EmployeeTransferred event = transferredIn(employee, Uuids.timeOrdered(NOW), unit, toUnit, legalEntity);
        String eventId = event.envelope().eventId().toString();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        try {
            publish(event);
            identityHandled(eventId);
        } finally {
            root.detachAppender(logged);
        }

        List<Map<String, Object>> items = itemsAbout(user);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst()).containsEntry("kind", "SCOPE_REVIEW");
        assertThat(items.getFirst().get("recipient_employee_id")).as("the mover is not their own reviewer").isNull();
        assertThat(assignedCardsAbout(dueItemId(user))).as("no recipient, no delivery").isEmpty();
        List<ILoggingEvent> alerts = logged.list.stream().filter(line -> line.getFormattedMessage().startsWith("ALERT P3 platform.identity ")).toList();
        assertThat(alerts).as("exactly one P3 alert line").hasSize(1);
        Map<String, Object> person;
        try (Connection workforce = schemaConnection("organization_workforce")) {
            person = query(workforce, "select employee_code, display_name from employees where id = ?", stored(employee)).getFirst();
        }
        assertThat(alerts.getFirst().getFormattedMessage()).contains(eventId).contains(String.valueOf(dueItemId(user))).doesNotContain(String.valueOf(person.get("employee_code")))
                .doesNotContain(String.valueOf(person.get("display_name")));
    }

    @Test
    @DisplayName("#730 SCOPE_REVIEW 8：既持有敏感角色又持有自定义部门角色的人转岗，同一命令得到两条（敏感角色复核与范围复核各一，各自去重）；只有范围复核有接收人")
    void aSensitiveAndScopedHolderGetsOneItemOfEachKind() throws Exception {
        UUID head = employed(REGULAR);
        UUID toUnit = newUnit();
        headOf(toUnit, head);
        UUID employee = employedHolding(SECURITY_AUDITOR);
        grantByOperations(employee, customRole("scoped-both", unit));
        UUID user = accountOf(employee);

        UUID command = transfer(employee, TODAY, toUnit);

        awaitItemsAbout(user, 2);
        List<Map<String, Object>> items = itemsAbout(user);
        assertThat(kindsOf(items)).containsExactlyInAnyOrder("SENSITIVE_ROLE_REVIEW", "SCOPE_REVIEW");
        assertThat(items).allSatisfy(item -> assertThat(item).containsEntry("trigger_personnel_command_id", command).containsEntry("trigger_field_kind", "ORG_UNIT"));
        assertThat(items.stream().filter(item -> "SCOPE_REVIEW".equals(item.get("kind"))).findFirst().orElseThrow()).containsEntry("recipient_employee_id", head);
        assertThat(items.stream().filter(item -> "SENSITIVE_ROLE_REVIEW".equals(item.get("kind"))).findFirst().orElseThrow().get("recipient_employee_id")).isNull();
    }

    @Test
    @DisplayName("#730 SCOPE_REVIEW 9：EmployeeCorrected 触发字段含部门且更正前部门有值时按同一规则生成范围复核（ADR-0081 决定 3）；更正前部门为空（撤销误离职）不生成（ADR-0082 决定 3）。依赖 #1089 发布新载荷")
    void aCorrectionOfTheDepartmentFollowsTheSameRule() throws Exception {
        UUID head = employed(REGULAR);
        UUID toUnit = newUnit();
        headOf(toUnit, head);
        UUID moved = employedHolding();
        grantByOperations(moved, customRole("scoped-corr", unit));
        UUID movedUser = accountOf(moved);
        UUID undone = employedHolding();
        grantByOperations(undone, customRole("scoped-undo", unit));
        UUID undoneUser = accountOf(undone);

        EmployeeCorrected movedEvent = corrected(moved, Uuids.timeOrdered(NOW), List.of(TriggerFieldKind.ORG_UNIT), unit, toUnit, null, null);
        EmployeeCorrected undoneEvent = undoneSeparation(undone, Uuids.timeOrdered(NOW), toUnit, newPosition());
        publish(movedEvent);
        publish(undoneEvent);
        identityHandled(movedEvent);
        identityHandled(undoneEvent);

        List<Map<String, Object>> items = itemsAbout(movedUser);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst()).containsEntry("kind", "SCOPE_REVIEW").containsEntry("before_org_unit_id", unit).containsEntry("after_org_unit_id", toUnit)
                .containsEntry("recipient_employee_id", head);
        assertThat(itemsAbout(undoneUser)).as("nothing before the correction, nothing to review").isEmpty();
    }

    // ---- EmployeeCorrected with both departments behaves like a transfer (one-design ruling, #730 comment 6604000297) ----

    private static final List<TriggerFieldKind> UNIT = List.of(TriggerFieldKind.ORG_UNIT);
    private static final List<TriggerFieldKind> POSITION = List.of(TriggerFieldKind.POSITION);
    private static final List<TriggerFieldKind> BOTH = List.of(TriggerFieldKind.ORG_UNIT, TriggerFieldKind.POSITION);

    /** The kind and field of every item about the user, sorted: the shape of what the listener raised. */
    private List<String> shapeOf(UUID user) throws SQLException {
        return itemsAbout(user).stream().map(item -> item.get("kind") + "/" + item.get("trigger_field_kind")).sorted().toList();
    }

    private UUID headedUnit(UUID head) throws Exception {
        UUID toUnit = newUnit();
        headOf(toUnit, head);
        return toUnit;
    }

    @Test
    @DisplayName("#730 更正如转岗 1：更正把持有敏感角色的人从 X 部门改到 Y 部门（触发字段部门），生成恰好一条敏感角色复核（触发字段部门、触发命令为该更正命令、触发前后部门、无接收人），没有范围复核")
    void aDepartmentCorrectionOfASensitiveHolderGivesOneSensitiveItem() throws Exception {
        UUID employee = employedHolding(SECURITY_AUDITOR);
        UUID user = accountOf(employee);
        UUID toUnit = newUnit();
        UUID command = Uuids.timeOrdered(NOW);
        EmployeeCorrected event = corrected(employee, command, UNIT, unit, toUnit, null, null);

        publish(event);
        identityHandled(event);

        List<Map<String, Object>> items = itemsAbout(user);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst()).containsEntry("kind", "SENSITIVE_ROLE_REVIEW").containsEntry("origin_kind", "ASSIGNMENT_CHANGE").containsEntry("status", "OPEN")
                .containsEntry("trigger_field_kind", "ORG_UNIT").containsEntry("trigger_personnel_command_id", command).containsEntry("before_org_unit_id", unit)
                .containsEntry("after_org_unit_id", toUnit).containsEntry("recipient_employee_id", null);
    }

    @Test
    @DisplayName("#730 更正如转岗 2：同一个人还持有自定义部门数据范围角色，除敏感角色复核外多一条范围复核，接收人为 Y 部门负责人，并有恰好一张发给该负责人的外发卡")
    void aDepartmentCorrectionOfASensitiveAndScopedHolderAlsoGivesAScopeReviewForTheNewHead() throws Exception {
        UUID head = employed(REGULAR);
        UUID toUnit = headedUnit(head);
        UUID employee = employedHolding(SECURITY_AUDITOR);
        grantByOperations(employee, customRole("scoped-c2", unit));
        UUID user = accountOf(employee);
        EmployeeCorrected event = corrected(employee, Uuids.timeOrdered(NOW), UNIT, unit, toUnit, null, null);

        publish(event);
        identityHandled(event);

        assertThat(shapeOf(user)).containsExactly("SCOPE_REVIEW/ORG_UNIT", "SENSITIVE_ROLE_REVIEW/ORG_UNIT");
        Map<String, Object> scope = itemsAbout(user).stream().filter(item -> "SCOPE_REVIEW".equals(item.get("kind"))).findFirst().orElseThrow();
        assertThat(scope).containsEntry("recipient_employee_id", head).containsEntry("before_org_unit_id", unit).containsEntry("after_org_unit_id", toUnit);
        Awaitility.await().atMost(SETTLE).untilAsserted(() -> {
            List<Map<String, Object>> cards = assignedCardsAbout(dueItemId(user, "SCOPE_REVIEW"));
            assertThat(cards).hasSize(1);
            assertThat(cards.getFirst().get("recipients")).isEqualTo(storedWecomUserId(head));
        });
    }

    @Test
    @DisplayName("#730 更正如转岗 3：只改岗位（触发字段岗位）的更正，持有敏感角色（也持有自定义部门角色）的人得到恰好一条敏感角色复核（触发字段岗位、触发前后岗位），没有范围复核")
    void aPositionCorrectionGivesOnePositionItemAndNoScopeReview() throws Exception {
        UUID employee = employedHolding(SECURITY_AUDITOR);
        grantByOperations(employee, customRole("scoped-c3", unit));
        UUID user = accountOf(employee);
        UUID fromPosition = newPosition();
        UUID toPosition = newPosition();
        EmployeeCorrected event = corrected(employee, Uuids.timeOrdered(NOW), POSITION, unit, unit, fromPosition, toPosition);

        publish(event);
        identityHandled(event);

        List<Map<String, Object>> items = itemsAbout(user);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst()).containsEntry("kind", "SENSITIVE_ROLE_REVIEW").containsEntry("trigger_field_kind", "POSITION")
                .containsEntry("before_position_id", fromPosition).containsEntry("after_position_id", toPosition);
    }

    @Test
    @DisplayName("#730 更正如转岗 4：同时改部门与岗位（触发字段部门与岗位）的更正，敏感角色复核一个字段一条（共两条），持有自定义部门角色时再加一条范围复核（部门字段），共三条")
    void aCorrectionOfBothFieldsGivesOneSensitiveItemPerFieldAndTheScopeReview() throws Exception {
        UUID head = employed(REGULAR);
        UUID toUnit = headedUnit(head);
        UUID employee = employedHolding(SECURITY_AUDITOR);
        grantByOperations(employee, customRole("scoped-c4", unit));
        UUID user = accountOf(employee);
        EmployeeCorrected event = corrected(employee, Uuids.timeOrdered(NOW), BOTH, unit, toUnit, newPosition(), newPosition());

        publish(event);
        identityHandled(event);

        assertThat(shapeOf(user)).containsExactly("SCOPE_REVIEW/ORG_UNIT", "SENSITIVE_ROLE_REVIEW/ORG_UNIT", "SENSITIVE_ROLE_REVIEW/POSITION");
    }

    @Test
    @DisplayName("#730 更正如转岗 5：既不持有敏感角色也不持有自定义部门角色的人（只有 employee 与全公司范围角色）被更正不生成任何条目（对照：同一时刻持有敏感角色者的更正生成，证明监听器在跑）")
    void aCorrectionOfAPersonWithNeitherGrantGivesNothing() throws Exception {
        UUID plain = employedHolding();
        grantByOperations(plain, roleWithScope("whole-c5-" + Uuids.timeOrdered(NOW).toString().substring(24), "ALL"));
        UUID plainUser = accountOf(plain);
        UUID holder = employedHolding(SECURITY_AUDITOR);
        UUID holderUser = accountOf(holder);
        EmployeeCorrected plainEvent = corrected(plain, Uuids.timeOrdered(NOW), BOTH, unit, newUnit(), newPosition(), newPosition());
        EmployeeCorrected holderEvent = corrected(holder, Uuids.timeOrdered(NOW), UNIT, unit, newUnit(), null, null);

        publish(plainEvent);
        publish(holderEvent);
        identityHandled(plainEvent);
        identityHandled(holderEvent);

        assertThat(itemsAbout(plainUser)).isEmpty();
        assertThat(itemsAbout(holderUser)).hasSize(1);
    }

    @Test
    @DisplayName("#730 更正如转岗 6：同一更正事件重投递，以及同一更正命令的另一个事件，都不新增条目也不新增外发卡（三类条目各自去重）")
    void aReplayOfACorrectionAddsNothing() throws Exception {
        UUID head = employed(REGULAR);
        UUID toUnit = headedUnit(head);
        UUID employee = employedHolding(SECURITY_AUDITOR);
        grantByOperations(employee, customRole("scoped-c6", unit));
        UUID user = accountOf(employee);
        UUID command = Uuids.timeOrdered(NOW);
        UUID fromPosition = newPosition();
        UUID toPosition = newPosition();
        EmployeeCorrected event = corrected(employee, command, BOTH, unit, toUnit, fromPosition, toPosition);
        EmployeeCorrected another = corrected(employee, command, BOTH, unit, toUnit, fromPosition, toPosition);

        publish(event);
        identityHandled(event);
        List<String> first = shapeOf(user);
        publish(event);
        publish(another);
        identityHandled(another);
        identityHandled(event);

        assertThat(first).containsExactly("SCOPE_REVIEW/ORG_UNIT", "SENSITIVE_ROLE_REVIEW/ORG_UNIT", "SENSITIVE_ROLE_REVIEW/POSITION");
        assertThat(shapeOf(user)).isEqualTo(first);
        assertThat(assignedCardsAbout(dueItemId(user, "SCOPE_REVIEW"))).hasSize(1);
    }

    @Test
    @DisplayName("#730 更正如转岗 7：触发字段为空（更正既没改部门也没改岗位）不生成任何条目，持有敏感角色和自定义部门角色也一样；事件处理完成不重试（对照：同一人的部门更正生成）")
    void aCorrectionWithoutTriggerFieldsGivesNothing() throws Exception {
        UUID employee = employedHolding(SECURITY_AUDITOR);
        grantByOperations(employee, customRole("scoped-c7", unit));
        UUID user = accountOf(employee);
        EmployeeCorrected quiet = corrected(employee, Uuids.timeOrdered(NOW), List.of(), null, null, null, null);

        publish(quiet);
        identityHandled(quiet);

        assertThat(itemsAbout(user)).isEmpty();
        assertThat(publicationsOf(quiet.envelope().eventId().toString())).allSatisfy(row -> assertThat(row).containsEntry("status", "COMPLETED").containsEntry("completion_attempts", 1));
        EmployeeCorrected moved = corrected(employee, Uuids.timeOrdered(NOW), UNIT, unit, newUnit(), null, null);
        publish(moved);
        identityHandled(moved);
        assertThat(itemsAbout(user)).as("the listener runs").isNotEmpty();
    }

    @Test
    @DisplayName("#730 更正如转岗 8：更正使在职者不再在职（更正前有部门、更正后没有）不生成任何条目（持有敏感角色和自定义部门角色也一样），不重试，也不记任何以 \"ALERT\" 开头的日志")
    void aCorrectionThatSeparatesGivesNothingAndRaisesNoAlert() throws Exception {
        UUID employee = employedHolding(SECURITY_AUDITOR);
        grantByOperations(employee, customRole("scoped-c8", unit));
        UUID user = accountOf(employee);
        EmployeeCorrected event = endedEmployment(employee, Uuids.timeOrdered(NOW), unit, newPosition());
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        try {
            publish(event);
            identityHandled(event);
            publish(event);
            identityHandled(event);
        } finally {
            root.detachAppender(logged);
        }

        assertThat(itemsAbout(user)).isEmpty();
        assertThat(publicationsOf(event.envelope().eventId().toString())).allSatisfy(row -> assertThat(row).containsEntry("status", "COMPLETED").containsEntry("completion_attempts", 1));
        assertThat(logged.list.stream().map(ILoggingEvent::getFormattedMessage).filter(message -> message.startsWith("ALERT")).toList()).as("no alert").isEmpty();
    }
}
