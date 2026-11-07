package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.ActingDutyNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.LAST_WORKING_DAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1579: the two employee-scoped read contracts in the card and workforce-acting-duty §4, §5, §8.11.
 * DOUBTS: no SCHEDULED-row, covering-list stand-in scope, unit/post names or sort-tie assertions are specified.
 * No existing workforce headcount HTTP read was found; the unchanged reads cover roster, detail and assignments.
 * Duties are entered in process through the personnel command use case (the HTTP request record has no acting fields on main); the reads are HTTP.
 * IT execution (including the expected pre-implementation red) belongs to the Docker-enabled dev session.
 */
class ActingDutyReadHttpIT extends ActingDutyReadHttpSupport {
    static final TestDatabase postgres = TestPostgres.database(ActingDutyReadHttpIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#1579 active duty exposes the agreed fields on both reads, with no forbidden key")
    void activeDuty() throws Exception {
        start("active", "covered", true, TODAY, TODAY.plusDays(8));
        Map<String, Object> row = only("active");
        assertThat(row).containsEntry("employeeId", employeeIdOf("active"))
                .containsEntry("coveredOrgUnitId", unitIds.get("ADR-B")).containsEntry("coveredPositionId", positionId)
                .containsEntry("coveredEmployeeId", employeeIdOf("covered")).containsEntry("coveredEmployeeName", "Synthetic covered")
                .containsEntry("firstDay", TODAY.toString()).containsEntry("lastDay", TODAY.plusDays(7).toString())
                .containsEntry("state", "ACTIVE").containsEntry("endCause", null).containsEntry("reason", REASON_TEXT);
        assertThat(duties("all", COVERING, "covered")).contains(row);
    }

    @Test
    @DisplayName("#1579 unit head duty without a position or named covered person exposes nulls")
    void headDutyHasNoPositionOrPerson() throws Exception {
        start("head", null, false, TODAY, TODAY.plusDays(4));
        assertThat(only("head")).containsEntry("employeeId", employeeIdOf("head"))
                .containsEntry("coveredOrgUnitId", unitIds.get("ADR-B")).containsEntry("coveredPositionId", null)
                .containsEntry("coveredEmployeeId", null).containsEntry("coveredEmployeeName", null)
                .containsEntry("firstDay", TODAY.toString()).containsEntry("lastDay", TODAY.plusDays(3).toString())
                .containsEntry("state", "ACTIVE").containsEntry("endCause", null).containsEntry("reason", REASON_TEXT);
    }

    @Test
    @DisplayName("#1579 one-day duty has the same first and last day")
    void oneDayTerm() throws Exception {
        start("oneDay", null, true, TODAY, TODAY.plusDays(1));
        assertThat(only("oneDay")).containsEntry("firstDay", TODAY.toString()).containsEntry("lastDay", TODAY.toString())
                .containsEntry("state", "ACTIVE").containsEntry("endCause", null);
    }

    @Test
    @DisplayName("#1579 at ends_before a duty is ENDED and the due job supplies EXPIRED")
    void expiredDuty() throws Exception {
        start("expired", null, true, TODAY, TODAY.plusDays(2));
        String id = only("expired").get("actingDutyId").toString();
        on(TODAY.plusDays(2));
        assertThat(only("expired")).containsEntry("state", "ENDED").containsEntry("lastDay", TODAY.plusDays(1).toString());
        dueJob();
        assertThat(only("expired")).containsEntry("actingDutyId", id).containsEntry("state", "ENDED")
                .containsEntry("endCause", "EXPIRED").containsEntry("lastDay", TODAY.plusDays(1).toString());
    }

    @Test
    @DisplayName("#1579 ACTING_END shows ENDED_EARLY and ended_on minus one")
    void endedEarly() throws Exception {
        start("early", null, true, TODAY, TODAY.plusDays(10));
        String id = only("early").get("actingDutyId").toString();
        on(TODAY.plusDays(2));
        change("early", ACTING_END, id, TODAY.plusDays(2), null);
        assertThat(only("early")).containsEntry("actingDutyId", id).containsEntry("firstDay", TODAY.toString())
                .containsEntry("lastDay", TODAY.plusDays(1).toString()).containsEntry("state", "ENDED").containsEntry("endCause", "ENDED_EARLY");
    }

    @Test
    @DisplayName("#1579 ACTING_EXTEND replaces the displayed last day and remains active past the old end")
    void extendedDuty() throws Exception {
        start("extended", null, true, TODAY, TODAY.plusDays(3));
        String id = only("extended").get("actingDutyId").toString();
        on(TODAY.plusDays(1));
        change("extended", ACTING_EXTEND, id, TODAY.plusDays(1), TODAY.plusDays(8));
        assertThat(only("extended")).containsEntry("actingDutyId", id).containsEntry("lastDay", TODAY.plusDays(7).toString())
                .containsEntry("state", "ACTIVE").containsEntry("endCause", null);
        on(TODAY.plusDays(3));
        dueJob();
        assertThat(only("extended")).containsEntry("lastDay", TODAY.plusDays(7).toString()).containsEntry("state", "ACTIVE").containsEntry("endCause", null);
    }

    @Test
    @DisplayName("#1579 separation after the first day shows ended_on minus one and SEPARATION")
    void separationEndsDuty() throws Exception {
        start("separated", null, true, TODAY, TODAY.plusDays(10));
        String id = only("separated").get("actingDutyId").toString();
        enter("separated", "SEPARATE", Map.of(LAST_WORKING_DAY, TODAY.plusDays(1)));
        on(TODAY.plusDays(2));
        dueJob();
        assertThat(only("separated")).containsEntry("actingDutyId", id).containsEntry("firstDay", TODAY.toString())
                .containsEntry("lastDay", TODAY.plusDays(1).toString()).containsEntry("state", "ENDED").containsEntry("endCause", "SEPARATION");
    }

    @Test
    @DisplayName("#1579 zero-length separation has null lastDay, ENDED and SEPARATION")
    void separationOnStartDayHasNoLastDay() throws Exception {
        start("zero", null, true, TODAY, TODAY.plusDays(10));
        String id = only("zero").get("actingDutyId").toString();
        enter("zero", "SEPARATE", Map.of(LAST_WORKING_DAY, TODAY.minusDays(1)));
        assertThat(only("zero")).containsEntry("actingDutyId", id).containsEntry("firstDay", TODAY.toString())
                .containsEntry("lastDay", null).containsEntry("state", "ENDED").containsEntry("endCause", "SEPARATION");
    }

    @Test
    @DisplayName("#1579 three duties of one person are listed newest first by starts_on")
    void dutiesAreNewestFirst() throws Exception {
        // Entered out of starts_on order, so that neither creation time nor the id order can pass for starts_on order.
        on(TODAY.plusDays(2));
        start("ordered", null, true, TODAY.plusDays(2), TODAY.plusDays(3));
        start("ordered", null, true, TODAY, TODAY.plusDays(1));
        start("ordered", null, true, TODAY.plusDays(1), TODAY.plusDays(2));
        List<Map<String, Object>> rows = duties("all", DUTIES, "ordered");
        assertThat(field(rows, "firstDay")).containsExactly(TODAY.plusDays(2).toString(), TODAY.plusDays(1).toString(), TODAY.toString());
        assertThat(field(rows, "employeeId")).containsOnly(employeeIdOf("ordered"));
    }

    @Test
    @DisplayName("#1579 covering list contains only the named person's duties, with stand-ins newest first; nobody covered gets empty items")
    void dutiesCoveringAPerson() throws Exception {
        on(TODAY.plusDays(2));
        start("cover1", "orderedCovered", true, TODAY.plusDays(2), TODAY.plusDays(10));
        start("cover2", "orderedCovered", true, TODAY, TODAY.plusDays(10));
        start("cover3", "orderedCovered", true, TODAY.plusDays(1), TODAY.plusDays(10));
        List<Map<String, Object>> rows = duties("all", COVERING, "orderedCovered");
        assertThat(field(rows, "employeeId")).containsExactly(employeeIdOf("cover1"), employeeIdOf("cover3"), employeeIdOf("cover2"));
        assertThat(field(rows, "firstDay")).containsExactly(TODAY.plusDays(2).toString(), TODAY.plusDays(1).toString(), TODAY.toString());
        assertThat(field(rows, "coveredEmployeeId")).containsOnly(employeeIdOf("orderedCovered"));
        assertThat(duties("all", COVERING, "nobodyCovered")).isEmpty();
    }

    @Test
    @DisplayName("#1579 a covered person outside the reader's scope has a null name (spec limits only the name) while the duty and the id remain visible")
    void hiddenCoveredPersonDoesNotHideDuty() throws Exception {
        start("named", "covered", true, TODAY, TODAY.plusDays(5));
        Map<String, Object> visible = only("named");
        List<Map<String, Object>> scoped = duties("onlyA", DUTIES, "named");
        assertThat(visible).containsEntry("coveredEmployeeId", employeeIdOf("covered")).containsEntry("coveredEmployeeName", "Synthetic covered");
        Map<String, Object> expected = new java.util.LinkedHashMap<>(visible);
        expected.put("coveredEmployeeName", null);
        assertThat(scoped).containsExactly(expected);
    }

    @Test
    @DisplayName("#1579 employee:read is required on both endpoints, including create-only and administrator-only accounts")
    void permissionsCannotBeEscalated() throws Exception {
        for (String endpoint : List.of(DUTIES, COVERING)) {
            for (String reader : List.of("plain", "createOnly", "admin1"))
                require(read(reader, endpoint + employeeIdOf("none")), 403, reader + " lacks employee:read");
        }
    }

    @Test
    @DisplayName("#1579 out-of-scope subject is indistinguishable from unknown and writes an employee denied audit on both endpoints")
    void scopeDenialAndAudit() throws Exception {
        start("scoped", "scopeCovered", true, TODAY, TODAY.plusDays(10));
        for (String endpoint : List.of(DUTIES, COVERING)) {
            String subject = endpoint.equals(DUTIES) ? "scoped" : "scopeCovered";
            String reader = endpoint.equals(DUTIES) ? "onlyB" : "onlyA";
            assertThat(duties("all", endpoint, subject)).hasSize(1);
            int before = auditEntries(DENIED).size();
            String absent = UUID.randomUUID().toString();
            MockHttpServletResponse unknown = read(reader, endpoint + absent);
            MockHttpServletResponse denied = read(reader, endpoint + employeeIdOf(subject));
            require(unknown, 404, "unknown employee");
            require(denied, 404, "out-of-scope employee");
            assertThat(refusalBody(denied, employeeIdOf(subject))).isEqualTo(refusalBody(unknown, absent));
            eventually(() -> assertThat(auditEntries(DENIED)).hasSize(before + 1));
            assertThat(auditEntries(DENIED)).anySatisfy(entry -> assertThat(entry.get("subjectId").toString()).isEqualTo(employeeIdOf(subject)));
        }
    }

    @Test
    @DisplayName("#1579 permitted unknown employee is 404 and a known person without duties gets empty items on both endpoints")
    void unknownAndEmptySubjects() throws Exception {
        for (String endpoint : List.of(DUTIES, COVERING)) {
            require(read("all", endpoint + UUID.randomUUID()), 404, "unknown employee in ALL scope");
            assertThat(duties("all", endpoint, "none")).isEmpty();
        }
    }

    @Test
    @DisplayName("#1579 empty scope and own-unit scope without an assignment fail closed on both endpoints")
    void emptyScopes() throws Exception {
        require(read("nohome", CURRENT_SESSION), 200, "priming principal before removing assignment");
        try (Connection db = schemaConnection("organization_workforce")) {
            sql(db, "delete from assignments where employee_id = ?", stored(UUID.fromString(employeeIdOf("nohome"))));
        }
        for (String reader : List.of("empty", "nohome")) for (String endpoint : List.of(DUTIES, COVERING)) {
            require(read("all", endpoint + employeeIdOf("none")), 200, "control: the endpoint exists and answers a full-scope reader");
            require(read(reader, endpoint + employeeIdOf("none")), 404, "empty data scope");
        }
    }

    @Test
    @DisplayName("#1579 unresolvable scope fails closed on both endpoints")
    void unresolvableScope() throws Exception {
        require(read("broken", CURRENT_SESSION), 200, "priming principal before directory failure");
        String employee = employeeIdOf("broken");
        doThrow(new IllegalStateException("synthetic: directory unavailable")).when(organisation)
                .findEmployee(argThat(id -> id != null && employee.equals(id.value().toString())));
        for (String endpoint : List.of(DUTIES, COVERING)) {
            require(read("all", endpoint + employeeIdOf("none")), 200, "control: the endpoint exists and answers a full-scope reader");
            require(read("broken", endpoint + employeeIdOf("none")), 404, "unresolvable subject scope");
            require(read("broken", endpoint + UUID.randomUUID()), 404, "unresolvable unknown subject scope");
        }
    }

    @Test
    @DisplayName("#1579 several employee:read roles give the union of their scopes on both endpoints")
    void roleScopesAreUnited() throws Exception {
        start("unionActor", "unionCovered", true, TODAY, TODAY.plusDays(10));
        assertThat(duties("union", DUTIES, "unionActor")).hasSize(1);
        assertThat(duties("union", COVERING, "unionCovered")).hasSize(1);
        for (String endpoint : List.of(DUTIES, COVERING)) for (String subject : List.of("unionActor", "unionCovered")) {
            assertThat(duties("union", endpoint, subject)).isEqualTo(duties("all", endpoint, subject));
            require(read(subject.equals("unionActor") ? "onlyB" : "onlyA", endpoint + employeeIdOf(subject)), 404, "single role excludes subject");
        }
    }

    @Test
    @DisplayName("#1579 entering and starting duties changes no roster page, employee detail or assignment, and adds no stand-in to the covered unit")
    void rosterAndAssignmentsDoNotChange() throws Exception {
        Map<String, Object> before = rosterSnapshot();
        start("roster", "covered", true, TODAY, TODAY.plusDays(10));
        start("roster", null, false, TODAY.plusDays(1), TODAY.plusDays(10));
        assertThat(rosterSnapshot()).isEqualTo(before);
        assertThat(duties("all", DUTIES, "roster")).hasSize(1);
        on(TODAY.plusDays(1));
        Map<String, Object> beforeJob = rosterSnapshot();
        dueJob();
        assertThat(rosterSnapshot()).isEqualTo(beforeJob);
        assertThat(duties("all", DUTIES, "roster")).hasSize(2);
    }
}
