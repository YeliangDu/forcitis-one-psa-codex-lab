package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeHired;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("#1015 EmployeeHired opens the account of the new employee (ADR-0068 item 2.5)")
class HandleEmployeeHiredIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private EmployeeHired hired(EmployeeId employee) {
        return new EmployeeHired(TestEnvelopes.of("organization.workforce.EmployeeHired", "Employee", employee.value()), new PersonnelCommandId(UUID.randomUUID()), employee,
                LocalDate.of(2026, 11, 2), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
    }

    private long accountsOf(EmployeeId employee) {
        return jdbc.sql("select count(*) from platform_identity.user_accounts where employee_id = :id").param("id", employee.value()).query(Long.class).single();
    }

    /** Whether the publication of this very event to the hire listener is completed: a delivery that failed stays open and is retried. */
    private boolean handled(EmployeeHired event) {
        return jdbc.sql("""
                select count(*) from (select listener_id, completion_date, serialized_event from platform_integration.event_publication
                                      union all
                                      select listener_id, completion_date, serialized_event from platform_integration.event_publication_archive) p
                 where p.listener_id like '%HandleEmployeeHired%' and p.completion_date is not null and p.serialized_event like :event
                """).param("event", "%" + event.envelope().eventId() + "%").query(Long.class).single() == 1;
    }

    @Test
    @DisplayName("a hire opens one account with the default role as its only open grant; the same event again and a second event for the person change nothing")
    void aHireOpensOneAccountOnce() {
        knownEmployee(7001, EmploymentStatus.EMPLOYED);
        EmployeeId employee = TestAccounts.employee(7001);
        EmployeeHired event = hired(employee);

        tx.executeWithoutResult(status -> events.publishEvent(event));

        await().atMost(Duration.ofSeconds(15)).until(() -> handled(event));
        assertThat(accountsOf(employee)).isEqualTo(1);
        assertThat(count("account_role_grants where effective_to_at is null")).isEqualTo(1);
        long grants = count("account_role_grants");
        EmployeeHired second = hired(employee);
        tx.executeWithoutResult(status -> events.publishEvent(second));
        tx.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(Duration.ofSeconds(15)).until(() -> handled(second));
        assertThat(accountsOf(employee)).isEqualTo(1);
        assertThat(count("account_role_grants")).isEqualTo(grants);
    }

    @Test
    @DisplayName("a person who is not employed on the directory's reading gets no account")
    void aPersonNotEmployedGetsNone() {
        knownEmployee(7002, EmploymentStatus.SEPARATED);
        EmployeeId employee = TestAccounts.employee(7002);

        EmployeeHired event = hired(employee);
        tx.executeWithoutResult(status -> events.publishEvent(event));

        await().atMost(Duration.ofSeconds(15)).until(() -> handled(event));
        assertThat(accountsOf(employee)).isZero();
    }
}
