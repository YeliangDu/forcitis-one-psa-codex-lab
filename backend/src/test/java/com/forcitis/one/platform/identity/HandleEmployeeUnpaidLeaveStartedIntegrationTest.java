package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.organization.workforce.events.EmployeeUnpaidLeaveStarted;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("#918 EmployeeUnpaidLeaveStarted raises one sensitive-role review for a holder of a sensitive role (ADR-0068 decision 3)")
class HandleEmployeeUnpaidLeaveStartedIntegrationTest extends IdentityIntegrationTest {

    private static final String REVIEWS = "permission_adjustments where kind = 'SENSITIVE_ROLE_REVIEW' and origin_kind = 'UNPAID_LEAVE_START'";

    @MockitoBean
    WorkCalendar calendar;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    /** The work calendar is not under test: the escalation deadline of a review is a fixed instant. */
    @BeforeEach
    void theDeadlineIsFixed() {
        when(calendar.endOfWorkdayAfter(any(), anyInt(), any())).thenReturn(Instant.now().plus(Duration.ofDays(7)));
    }

    @AfterEach
    void theReviewsGoBeforeTheAccountsDo() {
        jdbc.sql("delete from platform_identity.permission_adjustments").update();
    }

    private EmployeeUnpaidLeaveStarted started(EmployeeId employee, PersonnelCommandId command) {
        return new EmployeeUnpaidLeaveStarted(TestEnvelopes.of("organization.workforce.EmployeeUnpaidLeaveStarted", "Employee", employee.value()),
                command, employee, LocalDate.now());
    }

    private void deliver(EmployeeUnpaidLeaveStarted event) {
        tx.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.sql("""
                select count(*) from (select listener_id, completion_date, serialized_event from platform_integration.event_publication
                                      union all
                                      select listener_id, completion_date, serialized_event from platform_integration.event_publication_archive) p
                 where p.listener_id like '%HandleEmployeeUnpaidLeaveStarted%' and p.completion_date is not null and p.serialized_event like :event
                """).param("event", "%" + event.envelope().eventId() + "%").query(Long.class).single() == 1);
    }

    @Test
    @DisplayName("a holder of a sensitive role gets one review naming the command and no field; a redelivery of the same command adds none")
    void aHolderGetsOneReview() {
        knownEmployee(7201, EmploymentStatus.EMPLOYED);
        createAccount(7201, "super_admin", "employee");
        EmployeeId employee = TestAccounts.employee(7201);
        PersonnelCommandId command = new PersonnelCommandId(UUID.randomUUID());

        deliver(started(employee, command));

        assertThat(count(REVIEWS + " and status = 'OPEN' and trigger_personnel_command_id = '" + command.value() + "' and trigger_field_kind is null"
                + " and before_org_unit_id is null and after_org_unit_id is null and user_id = '" + TestAccounts.account(7201).value() + "'")).isEqualTo(1);

        deliver(started(employee, command));

        assertThat(count(REVIEWS)).as("the same command again").isEqualTo(1);

        deliver(started(employee, new PersonnelCommandId(UUID.randomUUID())));

        assertThat(count(REVIEWS)).as("a later leave is a new trigger").isEqualTo(2);
    }

    @Test
    @DisplayName("a person with only the default role, and a person without an account, get none")
    void nobodyElseGetsOne() {
        knownEmployee(7202, EmploymentStatus.EMPLOYED);
        createAccount(7202, "employee");
        knownEmployee(7203, EmploymentStatus.EMPLOYED);

        deliver(started(TestAccounts.employee(7202), new PersonnelCommandId(UUID.randomUUID())));
        deliver(started(TestAccounts.employee(7203), new PersonnelCommandId(UUID.randomUUID())));

        assertThat(count(REVIEWS)).isZero();
    }

    @Test
    @DisplayName("a holder of an ADR-0009 sensitive permission through an ordinary custom role gets one review; an ordinary custom role gets none")
    void aSensitivePermissionHolderGetsOneReview() {
        TestRoles.insertCustom(jdbc, "audit_exporter", "audit exporter", "ENABLED", "ALL", Permission.Codes.PLATFORM_AUDIT_ENTRY_EXPORT);
        TestRoles.insertCustom(jdbc, "calendar_reader", "calendar reader", "ENABLED", "ALL", Permission.Codes.ORGANIZATION_ATTENDANCE_WORK_CALENDAR_READ);
        knownEmployee(7204, EmploymentStatus.EMPLOYED);
        createAccount(7204, "audit_exporter");
        knownEmployee(7205, EmploymentStatus.EMPLOYED);
        createAccount(7205, "calendar_reader");

        deliver(started(TestAccounts.employee(7204), new PersonnelCommandId(UUID.randomUUID())));
        deliver(started(TestAccounts.employee(7205), new PersonnelCommandId(UUID.randomUUID())));

        assertThat(count(REVIEWS + " and user_id = '" + TestAccounts.account(7204).value() + "'")).isEqualTo(1);
        assertThat(count(REVIEWS)).isEqualTo(1);
    }

    @Test
    @DisplayName("the table refuses a leave-start review that names a role instead of a person")
    void aLeaveReviewAboutARoleIsRefused() {
        assertThatThrownBy(() -> jdbc.sql("""
                insert into platform_identity.permission_adjustments (permission_adjustment_id, kind, origin_kind, status, role_id, org_unit_id,
                    trigger_personnel_command_id, escalation_due_at, created_at, updated_at)
                select uuidv7(), 'SENSITIVE_ROLE_REVIEW', 'UNPAID_LEAVE_START', 'OPEN', role_id, uuidv7(), uuidv7(), now() + interval '1 day', now(), now()
                  from platform_identity.roles limit 1""").update())
                .hasMessageContaining("ck_permission_adjustments_subject");
    }

    @Test
    @DisplayName("a scope review cannot come from an unpaid-leave start (ck_permission_adjustments_kind_origin)")
    void aScopeReviewFromALeaveIsRefused() {
        createAccount(7206, "employee");
        assertThatThrownBy(() -> jdbc.sql("""
                insert into platform_identity.permission_adjustments (permission_adjustment_id, kind, origin_kind, status, user_id,
                    trigger_personnel_command_id, escalation_due_at, created_at, updated_at)
                values (uuidv7(), 'SCOPE_REVIEW', 'UNPAID_LEAVE_START', 'OPEN', :user, uuidv7(), now() + interval '1 day', now(), now())""")
                .param("user", TestAccounts.account(7206).value()).update())
                .hasMessageContaining("ck_permission_adjustments_kind_origin");
    }

    @Test
    @DisplayName("a leave review names its personnel command (ck_permission_adjustments_trigger_command)")
    void aLeaveReviewWithoutACommandIsRefused() {
        createAccount(7207, "employee");
        assertThatThrownBy(() -> jdbc.sql("""
                insert into platform_identity.permission_adjustments (permission_adjustment_id, kind, origin_kind, status, user_id,
                    escalation_due_at, created_at, updated_at)
                values (uuidv7(), 'SENSITIVE_ROLE_REVIEW', 'UNPAID_LEAVE_START', 'OPEN', :user, now() + interval '1 day', now(), now())""")
                .param("user", TestAccounts.account(7207).value()).update())
                .hasMessageContaining("ck_permission_adjustments_trigger_command");
    }
}
