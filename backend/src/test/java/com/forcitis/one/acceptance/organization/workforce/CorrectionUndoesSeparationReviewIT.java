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
    static final LocalDate BACK_ON = LocalDate.of(2026, 11, 1);
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
}
