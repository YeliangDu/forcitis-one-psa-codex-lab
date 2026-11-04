package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #1322 (ADR-0027 decision 1 item 6; ADR-0034 decision 2 item 5; user 2026-11-04 TLA+ model and 「作废」): a separated person leaves no confirmable
 * authorization request behind, as grantee or as requester, and no grant is made to someone who has already left.
 */
@DisplayName("SeparationGapsInAuthorization: #1322")
class SeparationGapsInAuthorizationIntegrationTest extends IdentityIntegrationTest {

    private static final String VOIDED = "platform.identity.authorization.voided";

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private UUID publishSeparation(long employee) {
        knownEmployee(employee, EmploymentStatus.SEPARATED);
        EmployeeId id = TestAccounts.employee(employee);
        var event = new EmployeeSeparated(TestEnvelopes.of("organization.workforce.EmployeeSeparated", "Employee", id.value()), new PersonnelCommandId(UUID.randomUUID()), id,
                LocalDate.of(2026, 10, 15));
        tx.executeWithoutResult(status -> events.publishEvent(event));
        return event.envelope().eventId();
    }

    private void awaitSeparationHandled(UUID published) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.sql("""
                select count(*) from (select listener_id, completion_date, serialized_event from platform_integration.event_publication
                                      union all
                                      select listener_id, completion_date, serialized_event from platform_integration.event_publication_archive) p
                 where p.listener_id like '%HandleEmployeeSeparated%' and p.completion_date is not null and p.serialized_event like :event
                """).param("event", "%" + published + "%").query(Long.class).single()).isEqualTo(1));
    }

    private MvcResult replaceRoles(String session, long target, String... roleCodes) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/platform/user-accounts/" + accountOrNone(target) + ":replace-roles").header("If-Match", accountVersion(target)).with(xsrf())
                .cookie(new Cookie("SESSION", session)).contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"" + String.join("\",\"", roleCodes) + "\"]}")).andReturn();
    }

    private MvcResult confirm(String session, String requestId) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/platform/authorization-requests/" + requestId + ":confirm").with(xsrf()).cookie(new Cookie("SESSION", session))).andReturn();
    }

    private String pendingRequest(String session, long target, String... roleCodes) throws Exception {
        MvcResult accepted = replaceRoles(session, target, roleCodes);
        assertThat(accepted.getResponse().getStatus()).as(accepted.getResponse().getContentAsString()).isEqualTo(202);
        return body(accepted).path("requestId").asString();
    }

    private String statusOf(String requestId) {
        return jdbc.sql("select request_status from platform_identity.authorization_requests where request_id = :id::uuid").param("id", requestId).query(String.class).single();
    }

    private long requestRows() {
        return jdbc.sql("select count(*) from platform_identity.authorization_requests").query(Long.class).single();
    }

    private boolean holds(long user, String roleCode) {
        return jdbc.sql("select count(*) from platform_identity.account_role_grants ar join platform_identity.roles r on r.role_id = ar.role_id where ar.effective_to_at is null and ar.user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = :u) and r.code = :c")
                .param("u", TestAccounts.employee(user).value()).param("c", roleCode).query(Long.class).single() == 1;
    }

    private long voidAudits(String requestId) {
        return recorded().stream().filter(entry -> entry.action().equals(VOIDED))
                .filter(entry -> entry.subject().equals(AuditSubject.of(AuditSubjectKind.AUTHORIZATION_REQUEST, UUID.fromString(requestId)))).count();
    }

    @Test
    @DisplayName("#1322 (a) the grantee holds no roles and separates while a request for them waits: the request ends VOIDED with an audit entry, confirming it afterwards grants nothing")
    void aGranteeWithoutRolesSeparates() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);
        createAccount(1002);
        knownEmployee(1002, EmploymentStatus.EMPLOYED);
        String requestId = pendingRequest(first, 1002, "security_auditor");

        awaitSeparationHandled(publishSeparation(1002));

        assertThat(statusOf(requestId)).isEqualTo("VOIDED");
        assertThat(voidAudits(requestId)).isEqualTo(1);
        assertThat(confirm(second, requestId).getResponse().getStatus()).as("a voided request is not confirmable").isEqualTo(409);
        assertThat(holds(1002, "security_auditor")).isFalse();
    }

    @Test
    @DisplayName("#1322 (b) a request for an account whose person has already separated is refused and none is opened")
    void noRequestForASeparatedAccount() throws Exception {
        String first = administratorSession(9);
        createAccount(1003);
        knownEmployee(1003, EmploymentStatus.SEPARATED);
        long before = requestRows();

        MvcResult refused = replaceRoles(first, 1003, "security_auditor");

        assertThat(refused.getResponse().getStatus()).as(refused.getResponse().getContentAsString()).isEqualTo(403);
        assertThat(body(refused).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(requestRows()).isEqualTo(before);
    }

    @Test
    @DisplayName("#1322 (c) the person separated and the event is published but not handled yet: confirming the request grants nothing")
    void confirmingInTheWindowBeforeTheListenerRuns() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);
        createAccount(1005, "employee");
        knownEmployee(1005, EmploymentStatus.EMPLOYED);
        String requestId = pendingRequest(first, 1005, "employee", "security_auditor");
        knownEmployee(1005, EmploymentStatus.SEPARATED);

        MvcResult confirmed = confirm(second, requestId);

        assertThat(confirmed.getResponse().getStatus()).as(confirmed.getResponse().getContentAsString()).isEqualTo(403);
        assertThat(body(confirmed).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(holds(1005, "security_auditor")).isFalse();
    }

    @Test
    @DisplayName("#1322 (d) the requester separates: every request they made that still waits ends VOIDED with an audit entry, and confirming it grants nothing")
    void theRequesterSeparates() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);
        loginWithWecom(1001);
        String requestId = pendingRequest(first, 1001, "employee", "security_auditor");

        awaitSeparationHandled(publishSeparation(9));

        assertThat(statusOf(requestId)).isEqualTo("VOIDED");
        assertThat(voidAudits(requestId)).isEqualTo(1);
        assertThat(confirm(second, requestId).getResponse().getStatus()).isEqualTo(409);
        assertThat(holds(1001, "security_auditor")).isFalse();
    }

    @Test
    @DisplayName("#1322 (e) the requester separated and the event is not handled yet: confirming their request grants nothing (TLA+ rule 4)")
    void confirmingARequestOfADepartedRequester() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);
        loginWithWecom(1001);
        String requestId = pendingRequest(first, 1001, "employee", "security_auditor");
        knownEmployee(9, EmploymentStatus.SEPARATED);

        MvcResult confirmed = confirm(second, requestId);

        assertThat(confirmed.getResponse().getStatus()).as(confirmed.getResponse().getContentAsString()).isEqualTo(403);
        assertThat(body(confirmed).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(holds(1001, "security_auditor")).isFalse();
    }

    @Test
    @DisplayName("#1322 (f) the confirmer separated and the event is not handled yet: their confirmation grants nothing (TLA+ rule 4)")
    void aDepartedConfirmerConfirmsNothing() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);
        loginWithWecom(1001);
        String requestId = pendingRequest(first, 1001, "employee", "security_auditor");
        knownEmployee(10, EmploymentStatus.SEPARATED);

        MvcResult confirmed = confirm(second, requestId);

        assertThat(confirmed.getResponse().getStatus()).as(confirmed.getResponse().getContentAsString()).isEqualTo(403);
        assertThat(body(confirmed).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(holds(1001, "security_auditor")).isFalse();
    }
}
