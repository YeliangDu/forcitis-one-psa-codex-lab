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
                new PersonnelCommandId(UUID.randomUUID()), employee, LocalDate.now(), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
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
        EmployeeReinstated same = new EmployeeReinstated(TestEnvelopes.of("organization.workforce.EmployeeReinstated", "Employee", employee.value()),
                event.personnelCommandId(), employee, event.effectiveDate(), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
        tx.executeWithoutResult(status -> events.publishEvent(same));

        await().atMost(Duration.ofSeconds(15)).until(() -> handled(same));
        assertThat(count("account_role_grants")).isEqualTo(grants);
        assertThat(count("account_role_grants where effective_to_at is null")).isZero();
        assertThat(count("handled_personnel_commands")).isEqualTo(1);
    }

    @Test
    @DisplayName("a second reinstatement of the same person (another command, after a separation closed the grants) opens the grant again, however the dates lie")
    void aLaterReinstatementIsDecidedOnItsOwn() {
        knownEmployee(7102, EmploymentStatus.EMPLOYED);
        createAccount(7102);
        EmployeeId employee = TestAccounts.employee(7102);
        EmployeeReinstated first = reinstated(employee);
        tx.executeWithoutResult(status -> events.publishEvent(first));
        await().atMost(Duration.ofSeconds(15)).until(() -> handled(first));
        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null").update();

        EmployeeReinstated second = reinstated(employee);
        tx.executeWithoutResult(status -> events.publishEvent(second));

        await().atMost(Duration.ofSeconds(15)).until(() -> handled(second));
        assertThat(count("account_role_grants where effective_to_at is null")).isEqualTo(1);
        assertThat(count("handled_personnel_commands")).isEqualTo(2);
    }

    private EmployeeReinstated again(EmployeeReinstated event) {
        return new EmployeeReinstated(TestEnvelopes.of("organization.workforce.EmployeeReinstated", "Employee", event.employeeId().value()),
                event.personnelCommandId(), event.employeeId(), event.effectiveDate(), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
    }

    @Test
    @DisplayName("a command first seen while the directory shows the person not employed is recorded: the same command again, once the person is employed, opens nothing")
    void aNotEmployedDecisionIsKept() {
        knownEmployee(7103, EmploymentStatus.SEPARATED);
        createAccount(7103);
        EmployeeId employee = TestAccounts.employee(7103);
        EmployeeReinstated event = reinstated(employee);
        tx.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(Duration.ofSeconds(15)).until(() -> handled(event));
        assertThat(count("account_role_grants where effective_to_at is null")).isZero();

        knownEmployee(7103, EmploymentStatus.EMPLOYED);
        EmployeeReinstated redelivered = again(event);
        tx.executeWithoutResult(status -> events.publishEvent(redelivered));

        await().atMost(Duration.ofSeconds(15)).until(() -> handled(redelivered));
        assertThat(count("account_role_grants where effective_to_at is null")).isZero();
        assertThat(count("handled_personnel_commands")).isEqualTo(1);
    }

    @Test
    @DisplayName("a command first seen while the account already holds a grant is recorded: after an administrator took the grants away, the same command again opens nothing")
    void anAlreadyGrantedDecisionIsKept() {
        knownEmployee(7104, EmploymentStatus.EMPLOYED);
        createAccount(7104, "employee");
        EmployeeId employee = TestAccounts.employee(7104);
        EmployeeReinstated event = reinstated(employee);
        tx.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(Duration.ofSeconds(15)).until(() -> handled(event));
        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null").update();

        EmployeeReinstated redelivered = again(event);
        tx.executeWithoutResult(status -> events.publishEvent(redelivered));

        await().atMost(Duration.ofSeconds(15)).until(() -> handled(redelivered));
        assertThat(count("account_role_grants where effective_to_at is null")).isZero();
        assertThat(count("handled_personnel_commands")).isEqualTo(1);
    }
}
