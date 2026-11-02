package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("#939 EmployeeReinstated opens the default-role grant once (ADR-0034 section 3 item 5)")
class HandleEmployeeReinstatedIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private EmployeeReinstated reinstated(EmployeeId employee) {
        return new EmployeeReinstated(TestEnvelopes.of("organization.workforce.EmployeeReinstated", "Employee", employee.value()),
                new PersonnelCommandId(UUID.randomUUID()), employee, LocalDate.now());
    }

    private boolean handled(EmployeeReinstated event) {
        return jdbc.sql("""
                select count(*) from (select listener_id, completion_date, serialized_event from platform_integration.event_publication
                                      union all
                                      select listener_id, completion_date, serialized_event from platform_integration.event_publication_archive) p
                 where p.listener_id like '%HandleEmployeeReinstated%' and p.completion_date is not null and p.serialized_event like :event
                """).param("event", "%" + event.envelope().eventId() + "%").query(Long.class).single() == 1;
    }

    @Test
    @DisplayName("a redelivery of the same event after an administrator took the role away opens nothing")
    void aRedeliveryDoesNotOverrideTheRemoval() {
        knownEmployee(7101, EmploymentStatus.EMPLOYED);
        createAccount(7101);
        EmployeeId employee = TestAccounts.employee(7101);
        EmployeeReinstated event = reinstated(employee);

        tx.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(Duration.ofSeconds(15)).until(() -> handled(event));
        assertThat(count("account_role_grants where effective_to_at is null")).as("the default role is opened").isEqualTo(1);
        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null").update();
        long grants = count("account_role_grants");

        tx.executeWithoutResult(status -> events.publishEvent(event));
        EmployeeReinstated another = reinstated(employee);
        tx.executeWithoutResult(status -> events.publishEvent(another));

        await().atMost(Duration.ofSeconds(15)).until(() -> handled(another));
        assertThat(count("account_role_grants")).isEqualTo(grants);
        assertThat(count("account_role_grants where effective_to_at is null")).isZero();
    }
}
