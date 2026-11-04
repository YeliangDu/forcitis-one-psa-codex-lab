package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** #1244, rulings 1-3 and 5d: EmployeeSeparated closes OPEN person items independently of account-role removal. */
class EmployeeSeparatedClosesAdjustmentsIT extends PermissionAdjustmentConsumerSupport {

    private static final String CLOSED = "platform.identity.PermissionAdjustmentClosedBySeparation";
    private static final String CLOSED_EVENT = "PermissionAdjustmentClosedBySeparation";

    static final TestDatabase postgres = TestPostgres.database(EmployeeSeparatedClosesAdjustmentsIT.class, TestPostgres.MIGRATED_TESTDATA)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void organisation() throws Exception {
        consumerWorld();
    }

    private Item personItem(String person, String kind) throws Exception {
        return base(kind, "ASSIGNMENT_CHANGE").with("user_id", UUID.fromString(userId(person)))
                .with("trigger_personnel_command_id", UUID.randomUUID()).with("trigger_field_kind", "ORG_UNIT")
                .with("before_org_unit_id", unitId("F1-A")).with("after_org_unit_id", unitId("F1-B"));
    }

    private void assertClosed(UUID item, EmployeeSeparated event) throws Exception {
        Map<String, Object> row = rowOf(item);
        assertThat(row).containsEntry("status", "CLOSED_BY_SEPARATION")
                .containsEntry("closure_personnel_command_id", event.personnelCommandId().value());
        assertThat(row.get("closed_at")).isNotNull();
    }

    @Test
    @DisplayName("#1244 F-1 invariants 26, 29: separation closes both OPEN person kinds; UPHELD, another person and source-2 rows stay untouched")
    void onlyOpenItemsOfTheSeparatedAccountAreClosed() throws Exception {
        UUID sensitive = insertItem(personItem("subject", SENSITIVE_ROLE_REVIEW));
        UUID scope = insertItem(personItem("subject", SCOPE_REVIEW));
        UUID upheld = insertItem(upheld(personItem("subject", SENSITIVE_ROLE_REVIEW)));
        UUID other = insertItem(personItem("other", SENSITIVE_ROLE_REVIEW));
        String role = customRole("f1244-separation-source2", onlyUnit(unitId("F1-A").toString()));
        UUID sourceTwo = sourceTwoItem(role, unitId("F1-A"), false);
        Map<UUID, Map<String, Object>> untouched = Map.of(upheld, rowOf(upheld), other, rowOf(other), sourceTwo, rowOf(sourceTwo));
        EmployeeSeparated event = separation("subject");

        publishAndAwait(event);

        assertClosed(sensitive, event);
        assertClosed(scope, event);
        for (UUID item : untouched.keySet()) {
            assertThat(rowOf(item)).as("untouched item " + item).isEqualTo(untouched.get(item));
        }
    }

    @Test
    @DisplayName("#1244 ruling 1 gap: an account with NO roles still has its OPEN item closed by EmployeeSeparated")
    void anAccountWithoutRolesStillHasItsOpenItemClosed() throws Exception {
        UUID account = UUID.fromString(userId("roleless"));
        String administrator = administrator("admin1");
        String etag = readAccount(administrator, "roleless").getHeader("ETag");
        MockHttpServletResponse removed = mvc.perform(post(USER_ACCOUNTS + "/" + account + ":replace-roles").with(csrf()).with(as(administrator))
                .header("If-Match", etag).contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[]}")).andReturn().getResponse();
        require(removed, 204, "removing every role before separation");
        assertThat(roleCodesOf(administrator, "roleless")).isEmpty();
        assertThat(select("platform_identity", "select account_role_grant_id from account_role_grants where user_id = ? and effective_to_at is null", account)).isEmpty();
        UUID item = insertItem(personItem("roleless", SENSITIVE_ROLE_REVIEW));
        EmployeeSeparated event = separation("roleless");

        publishAndAwait(event);

        assertClosed(item, event);
    }

    @Test
    @DisplayName("#1244 one-design ruling 6631627109 (2): each closed item emits one ClosedBySeparation event (ids and kind) and one audit entry named by the event type with the exact detail text and a system actor")
    void everyClosedItemHasOneSystemAuditAndOneIdsOnlyEvent() throws Exception {
        UUID account = UUID.fromString(userId("auditSubject"));
        List<UUID> items = List.of(insertItem(personItem("auditSubject", SENSITIVE_ROLE_REVIEW)), insertItem(personItem("auditSubject", SCOPE_REVIEW)));
        EmployeeSeparated event = separation("auditSubject");

        publishAndAwait(event);

        eventually(() -> {
            for (UUID item : items) {
                assertClosed(item, event);
                String detail = "item " + item + " " + rowOf(item).get("kind") + " closed by separation; user " + account
                        + "; command " + event.personnelCommandId().value();
                assertThat(auditsOf(CLOSED, item)).singleElement().satisfies(entry -> {
                    assertThat(entry).containsEntry("action", CLOSED).containsEntry("subjectKind", "PERMISSION_ADJUSTMENT")
                            .containsEntry("subjectId", item.toString()).containsEntry("detail", detail);
                    assertThat(entry.get("actorId")).as("system actions have no account actor").isNull();
                });
                assertThat(eventsOf(CLOSED_EVENT, item)).singleElement().satisfies(closed -> {
                    assertThat(closed).containsOnlyKeys("envelope", "permissionAdjustmentId", "userId", "kind", "closurePersonnelCommandId");
                    assertThat(closed.get("kind")).isEqualTo(rowOf(item).get("kind"));
                    assertThat(idValue(closed.get("permissionAdjustmentId"))).isEqualTo(item.toString());
                    assertThat(idValue(closed.get("userId"))).isEqualTo(account.toString());
                    assertThat(idValue(closed.get("closurePersonnelCommandId"))).isEqualTo(event.personnelCommandId().value().toString());
                    assertThat(((Map<?, ?>) closed.get("envelope")).get("actorType")).isEqualTo("SYSTEM");
                });
            }
        });
    }

    @Test
    @DisplayName("#1244 rulings 1, 5d: replay changes no closed row and adds no audit; employees without an account or without items are harmless")
    void replayAndEmployeesWithoutAnAccountOrItemsChangeNothing() throws Exception {
        UUID item = insertItem(personItem("replaySubject", SENSITIVE_ROLE_REVIEW));
        EmployeeSeparated event = separation("replaySubject");
        publishAndAwait(event);
        eventually(() -> assertThat(auditsOf(CLOSED, item)).hasSize(1));
        Map<String, Object> closed = rowOf(item);
        List<Map<String, Object>> audits = auditsOf(CLOSED, item);
        assertThat(accountsOfEmployee(UUID.fromString(employeeIdOf("noAccount")))).isEmpty();
        UUID noItemsAccount = UUID.fromString(userId("noItems"));
        assertThat(select("platform_identity", "select permission_adjustment_id from permission_adjustments where user_id = ?", noItemsAccount)).isEmpty();
        List<Map<String, Object>> before = select("platform_identity", "select * from permission_adjustments order by permission_adjustment_id");
        int auditCount = auditEntries(CLOSED).size();

        publishAndAwait(event);
        publishAndAwait(separation("noAccount"));
        publishAndAwait(separation("noItems"));

        assertThat(rowOf(item)).isEqualTo(closed);
        assertThat(auditsOf(CLOSED, item)).isEqualTo(audits);
        assertThat(eventsOf(CLOSED_EVENT, item)).hasSize(1);
        assertThat(select("platform_identity", "select * from permission_adjustments order by permission_adjustment_id")).isEqualTo(before);
        assertThat(auditEntries(CLOSED)).hasSize(auditCount);
    }
}
