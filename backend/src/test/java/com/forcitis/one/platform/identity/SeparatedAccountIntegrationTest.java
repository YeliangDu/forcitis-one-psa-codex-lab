package com.forcitis.one.platform.identity;

import com.forcitis.one.BusinessDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeCorrected;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/** #1326: separation is a state of the account, set for every account and refusing every grant that goes through the aggregate, whatever the directory says. */
@DisplayName("SeparatedAccount: #1326")
class SeparatedAccountIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private UUID publishSeparation(long employee) {
        EmployeeId id = TestAccounts.employee(employee);
        var event = new EmployeeSeparated(TestEnvelopes.of("organization.workforce.EmployeeSeparated", "Employee", id.value()), new PersonnelCommandId(UUID.randomUUID()), id,
                LocalDate.of(2026, 10, 15), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
        tx.executeWithoutResult(status -> events.publishEvent(event));
        return event.envelope().eventId();
    }

    private UUID publishReinstatement(long employee) {
        EmployeeId id = TestAccounts.employee(employee);
        var event = new EmployeeReinstated(TestEnvelopes.of("organization.workforce.EmployeeReinstated", "Employee", id.value()), new PersonnelCommandId(UUID.randomUUID()), id,
                BusinessDay.today(), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
        tx.executeWithoutResult(status -> events.publishEvent(event));
        return event.envelope().eventId();
    }

    /** A correction that undoes a wrong separation: no department before, one today; ORG_UNIT only (ADR-0082 decision 2). */
    private UUID publishCorrectionUndoingSeparation(long employee) {
        EmployeeId id = TestAccounts.employee(employee);
        var event = new EmployeeCorrected(TestEnvelopes.of("organization.workforce.EmployeeCorrected", "Employee", id.value()), new PersonnelCommandId(UUID.randomUUID()), id,
                LocalDate.of(2026, 10, 15), null, java.util.List.of(TriggerFieldKind.ORG_UNIT), null, com.forcitis.one.fixture.doubles.TestOrgIds.orgUnit(10), null,
                com.forcitis.one.fixture.doubles.TestOrgIds.position(1), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
        tx.executeWithoutResult(status -> events.publishEvent(event));
        return event.envelope().eventId();
    }

    /** A correction of the position only: the shape does not say the person was away, the directory does. */
    private UUID publishPositionCorrection(long employee) {
        EmployeeId id = TestAccounts.employee(employee);
        var orgUnit = com.forcitis.one.fixture.doubles.TestOrgIds.orgUnit(10);
        var event = new EmployeeCorrected(TestEnvelopes.of("organization.workforce.EmployeeCorrected", "Employee", id.value()), new PersonnelCommandId(UUID.randomUUID()), id,
                LocalDate.of(2026, 10, 15), null, java.util.List.of(TriggerFieldKind.POSITION), orgUnit, orgUnit, com.forcitis.one.fixture.doubles.TestOrgIds.position(1),
                com.forcitis.one.fixture.doubles.TestOrgIds.position(2), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
        tx.executeWithoutResult(status -> events.publishEvent(event));
        return event.envelope().eventId();
    }

    private void awaitHandled(String listener, UUID published) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.sql("""
                select count(*) from (select listener_id, completion_date, serialized_event from platform_integration.event_publication
                                      union all
                                      select listener_id, completion_date, serialized_event from platform_integration.event_publication_archive) p
                 where p.listener_id like :listener and p.completion_date is not null and p.serialized_event like :event
                """).param("listener", "%" + listener + "%").param("event", "%" + published + "%").query(Long.class).single()).isEqualTo(1));
    }

    private OffsetDateTime separatedAt(long user) {
        return jdbc.sql("select separated_at from platform_identity.user_accounts where employee_id = :u")
                .param("u", TestAccounts.employee(user).value()).query(OffsetDateTime.class).optional().orElse(null);
    }

    private long versionOf(long user) {
        return jdbc.sql("select version from platform_identity.user_accounts where employee_id = :u").param("u", TestAccounts.employee(user).value()).query(Long.class).single();
    }

    private boolean holds(long user, String roleCode) {
        return jdbc.sql("select count(*) from platform_identity.account_role_grants ar join platform_identity.roles r on r.role_id = ar.role_id where ar.effective_to_at is null and ar.user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = :u) and r.code = :c")
                .param("u", TestAccounts.employee(user).value()).param("c", roleCode).query(Long.class).single() == 1;
    }

    private MvcResult replaceRoles(String session, long target, String... roleCodes) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/platform/user-accounts/" + accountOrNone(target) + ":replace-roles").header("If-Match", accountVersion(target)).with(xsrf())
                .cookie(new Cookie("SESSION", session)).contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"" + String.join("\",\"", roleCodes) + "\"]}")).andReturn();
    }

    @Test
    @DisplayName("#1326 (a) the separation marks an account without roles; a redelivery keeps the first moment and moves no version")
    void aSeparationMarksAnAccountWithoutRoles() {
        createAccount(1002);
        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null").update();
        knownEmployee(1002, EmploymentStatus.SEPARATED);
        assertThat(separatedAt(1002)).isNull();

        awaitHandled("HandleEmployeeSeparated", publishSeparation(1002));
        OffsetDateTime first = separatedAt(1002);
        long version = versionOf(1002);
        assertThat(first).as("marked although no role was taken away").isNotNull();

        awaitHandled("HandleEmployeeSeparated", publishSeparation(1002));

        assertThat(separatedAt(1002)).isEqualTo(first);
        assertThat(versionOf(1002)).isEqualTo(version);
    }

    @Test
    @DisplayName("#1326 (b) a marked account is given no role even when the directory says employed: the refusal is the aggregate's, not the directory's")
    void aMarkedAccountRefusesAGrantWhateverTheDirectorySays() throws Exception {
        String admin = administratorSession(9);
        createAccount(1002);
        knownEmployee(1002, EmploymentStatus.SEPARATED);
        awaitHandled("HandleEmployeeSeparated", publishSeparation(1002));
        knownEmployee(1002, EmploymentStatus.EMPLOYED);

        MvcResult granted = replaceRoles(admin, 1002, "employee");

        assertThat(granted.getResponse().getStatus()).as(granted.getResponse().getContentAsString()).isEqualTo(403);
        assertThat(body(granted).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(holds(1002, "employee")).isFalse();
    }

    @Test
    @DisplayName("#1326 (c) the reinstatement lifts the mark and opens the default role again")
    void theReinstatementLiftsTheMark() {
        createAccount(1002);
        knownEmployee(1002, EmploymentStatus.SEPARATED);
        awaitHandled("HandleEmployeeSeparated", publishSeparation(1002));
        assertThat(separatedAt(1002)).isNotNull();
        assertThat(holds(1002, "employee")).isFalse();
        knownEmployee(1002, EmploymentStatus.EMPLOYED);

        awaitHandled("HandleEmployeeReinstated", publishReinstatement(1002));

        assertThat(separatedAt(1002)).isNull();
        assertThat(holds(1002, "employee")).isTrue();
    }

    @Test
    @DisplayName("#1326 (d) a separation event that is handled after the reinstatement, the directory showing the person employed, leaves no mark and the person can be given roles")
    void aLateSeparationEventDoesNotLockAnEmployedPerson() throws Exception {
        createAccount(1003, "employee");
        knownEmployee(1003, EmploymentStatus.EMPLOYED);

        awaitHandled("HandleEmployeeSeparated", publishSeparation(1003));

        assertThat(separatedAt(1003)).as("the directory says employed: nothing to remember").isNull();
        assertThat(holds(1003, "employee")).as("the roles are taken away as before").isFalse();
        MvcResult granted = replaceRoles(administratorSession(9), 1003, "employee");
        assertThat(granted.getResponse().getStatus()).as(granted.getResponse().getContentAsString()).isEqualTo(204);
    }

    @Test
    @DisplayName("#1326 (e) a correction that undoes the separation (no reinstatement event follows) lifts the mark once the directory shows the person employed")
    void aCorrectionUndoingTheSeparationLiftsTheMark() throws Exception {
        createAccount(1004);
        knownEmployee(1004, EmploymentStatus.SEPARATED);
        awaitHandled("HandleEmployeeSeparated", publishSeparation(1004));
        assertThat(separatedAt(1004)).isNotNull();
        knownEmployee(1004, EmploymentStatus.EMPLOYED);

        awaitHandled("HandleEmployeeReinstated", publishCorrectionUndoingSeparation(1004));

        assertThat(separatedAt(1004)).isNull();
        MvcResult granted = replaceRoles(administratorSession(9), 1004, "employee");
        assertThat(granted.getResponse().getStatus()).as(granted.getResponse().getContentAsString()).isEqualTo(204);
    }

    @Test
    @DisplayName("#1326 (f) any correction re-checks the mark: one of another field reaches a marked person the directory shows employed, and the mark clears")
    void aCorrectionOfAnotherFieldLiftsTheMarkOfAnEmployedPerson() {
        createAccount(1005);
        knownEmployee(1005, EmploymentStatus.SEPARATED);
        awaitHandled("HandleEmployeeSeparated", publishSeparation(1005));
        assertThat(separatedAt(1005)).isNotNull();
        knownEmployee(1005, EmploymentStatus.EMPLOYED);

        awaitHandled("HandleEmployeeReinstated", publishPositionCorrection(1005));

        assertThat(separatedAt(1005)).isNull();
    }
}
