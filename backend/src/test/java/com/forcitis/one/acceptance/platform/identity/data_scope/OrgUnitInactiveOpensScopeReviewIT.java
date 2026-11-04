package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.events.OrgUnitDissolved;
import com.forcitis.one.organization.workforce.events.OrgUnitMerged;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** #1244, RULE-387 and rulings 1, 5a-5b: source-2 reviews are about the current role and the inactive unit. */
class OrgUnitInactiveOpensScopeReviewIT extends PermissionAdjustmentConsumerSupport {

    private static final String ALERT = "ALERT P3 platform.identity ";

    static final TestDatabase postgres = TestPostgres.database(OrgUnitInactiveOpensScopeReviewIT.class, TestPostgres.MIGRATED_TESTDATA)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void organisation() throws Exception {
        consumerWorld("F1244-open-D", "F1244-open-E", "F1244-merge-D", "F1244-merge-E", "F1244-dedupe-D", "F1244-alert-D", "F1244-read-D");
    }

    private void assertSourceTwo(UUID item, String role, UUID unit, UUID command) throws Exception {
        assertThat(rowOf(item)).containsEntry("kind", SCOPE_REVIEW).containsEntry("origin_kind", "ORG_UNIT_INACTIVE").containsEntry("status", "OPEN")
                .containsEntry("role_id", UUID.fromString(role)).containsEntry("org_unit_id", unit).containsEntry("trigger_org_structure_command_id", command)
                .containsEntry("user_id", null).containsEntry("recipient_employee_id", null).containsEntry("trigger_personnel_command_id", null)
                .containsEntry("trigger_field_kind", null).containsEntry("before_org_unit_id", null).containsEntry("after_org_unit_id", null)
                .containsEntry("before_position_id", null).containsEntry("after_position_id", null);
    }

    @Test
    @DisplayName("#1244 RULE-387 ruling 5a: every CURRENT custom role listing D gets one OPEN source-2 item, regardless of descendants, enabled status or holders")
    void onlyCurrentCustomVersionsListingTheDissolvedUnitGetItems() throws Exception {
        UUID unit = unitId("F1244-open-D");
        UUID otherUnit = unitId("F1244-open-E");
        Map<String, String> affected = new LinkedHashMap<>();
        List<String> heldCodes = new ArrayList<>();
        List<String> disabledCodes = new ArrayList<>();
        for (boolean descendants : List.of(false, true)) {
            for (boolean enabled : List.of(false, true)) {
                for (boolean held : List.of(false, true)) {
                    String code = "f1244-open-" + descendants + "-" + enabled + "-" + held;
                    String role = customRole(code, descendants ? withDescendants(unit.toString()) : onlyUnit(unit.toString()));
                    affected.put(code, role);
                    if (held) {
                        heldCodes.add(code);
                    }
                    if (!enabled) {
                        disabledCodes.add(code);
                    }
                }
            }
        }
        grantOrdinaryRoles(administrator("admin1"), "other", heldCodes.toArray(String[]::new));
        for (String code : disabledCodes) {
            advanceTo(clock.instant().plusSeconds(1));
            boolean descendants = code.startsWith("f1244-open-true-");
            String body = roleBody(code, "CUSTOM_ORG_UNITS", List.of(descendants ? withDescendants(unit.toString()) : onlyUnit(unit.toString())), ORG_UNIT_READ)
                    .replace("\"enabled\":true", "\"enabled\":false");
            require(reviseRole(administrator("admin1"), affected.get(code), body), 200, "disabling an ordinary role");
        }
        String notListed = customRole("f1244-not-listed", onlyUnit(otherUnit.toString()));
        String all = ordinaryRole(administrator("admin1"), "f1244-all", "ALL", null, ORG_UNIT_READ);
        String historical = customRole("f1244-old-version-only", onlyUnit(unit.toString()));
        advanceTo(clock.instant().plusSeconds(1));
        require(reviseRole(administrator("admin1"), historical,
                roleBody("f1244-old-version-only", "CUSTOM_ORG_UNITS", List.of(onlyUnit(otherUnit.toString())), ORG_UNIT_READ)), 200, "dropping D before dissolution");
        List<Map<String, Object>> references = select("platform_identity", "select * from role_version_custom_org_units order by role_version_id, org_unit_id");
        OrgUnitDissolved event = dissolution(unit);

        publishAndAwait(event);

        assertThat(itemsForUnit(unit)).extracting(row -> row.get("role_id"))
                .containsExactlyInAnyOrderElementsOf(affected.values().stream().map(UUID::fromString).toList());
        for (String role : affected.values()) {
            assertSourceTwo(itemFor(role, unit), role, unit, event.orgStructureCommandId().value());
        }
        for (String excluded : List.of(notListed, all, historical)) {
            assertThat(select("platform_identity", "select permission_adjustment_id from permission_adjustments where role_id = ?::uuid", excluded)).isEmpty();
        }
        assertThat(select("platform_identity", "select * from role_version_custom_org_units order by role_version_id, org_unit_id")).isEqualTo(references);
    }

    @Test
    @DisplayName("#1244 RULE-387 ruling 5a: merging D into E raises reviews for D alone; a successor-only role gets none and no item names E")
    void aMergeNeverWritesTheSuccessorUnit() throws Exception {
        UUID unit = unitId("F1244-merge-D");
        UUID successor = unitId("F1244-merge-E");
        String awayOnly = customRole("f1244-merge-away", onlyUnit(unit.toString()));
        String successorOnly = customRole("f1244-merge-successor", onlyUnit(successor.toString()));
        String both = customRole("f1244-merge-both", onlyUnit(unit.toString()), withDescendants(successor.toString()));
        OrgUnitMerged event = merge(unit, successor);

        publishAndAwait(event);

        assertThat(itemsForUnit(unit)).extracting(row -> row.get("role_id")).containsExactlyInAnyOrder(UUID.fromString(awayOnly), UUID.fromString(both));
        for (String role : List.of(awayOnly, both)) {
            assertSourceTwo(itemFor(role, unit), role, unit, event.orgStructureCommandId().value());
        }
        assertThat(itemsForUnit(successor)).isEmpty();
        assertThat(select("platform_identity", "select permission_adjustment_id from permission_adjustments where role_id = ?::uuid", successorOnly)).isEmpty();
        MockHttpServletResponse unchanged = readRole(administrator("admin1"), both);
        require(unchanged, 200, "reading the unchanged role");
        assertThat(JsonPath.<List<String>>read(unchanged.getContentAsString(), "$.customOrgUnits[*].orgUnitId"))
                .containsExactlyInAnyOrder(unit.toString(), successor.toString());
    }

    @Test
    @DisplayName("#1244 F-1 invariant 17: replay and another command for the same pair add nothing; existing OPEN and UPHELD rows are skipped unchanged")
    void replayAndExistingOpenOrUpheldPairsAreSkipped() throws Exception {
        UUID unit = unitId("F1244-dedupe-D");
        String freshRole = customRole("f1244-dedupe-fresh", onlyUnit(unit.toString()));
        String openRole = customRole("f1244-dedupe-open", onlyUnit(unit.toString()));
        String upheldRole = customRole("f1244-dedupe-upheld", onlyUnit(unit.toString()));
        UUID open = sourceTwoItem(openRole, unit, false);
        UUID upheld = sourceTwoItem(upheldRole, unit, true);
        Map<String, Object> openBefore = rowOf(open);
        Map<String, Object> upheldBefore = rowOf(upheld);
        OrgUnitDissolved event = dissolution(unit);
        publishAndAwait(event);
        UUID fresh = itemFor(freshRole, unit);
        Map<String, Object> freshBefore = rowOf(fresh);

        publishAndAwait(event);
        publishAndAwait(dissolution(unit));
        publishAndAwait(merge(unit, unitId("F1-A")));

        assertThat(itemsForUnit(unit)).hasSize(3);
        assertThat(rowOf(open)).isEqualTo(openBefore);
        assertThat(rowOf(upheld)).isEqualTo(upheldBefore);
        assertThat(rowOf(fresh)).isEqualTo(freshBefore);
    }

    @Test
    @DisplayName("#1244 one-design ruling 6631627109 (3): exactly one ALERT P3 platform.identity line of the stated shape per opened source-2 item, no names or codes; a skipped duplicate logs none")
    void alertsContainOnlyItemAndEventIdsAndDuplicatesLogNothing() throws Exception {
        UUID unit = unitId("F1244-alert-D");
        String firstCode = "f1244-alert-first";
        String secondCode = "f1244-alert-second";
        String first = customRole(firstCode, onlyUnit(unit.toString()));
        String second = customRole(secondCode, withDescendants(unit.toString()));
        String account = userId("other");
        String employee = employeeIdOf("other");
        OrgUnitDissolved event = dissolution(unit);
        int mark = logMark();

        publishAndAwait(event);

        List<String> alerts = alertsSince(mark).stream().filter(line -> line.startsWith(ALERT)).toList();
        assertThat(alerts).as("exactly one ALERT line per opened item, no second default-calendar line").hasSize(2);
        for (String role : List.of(first, second)) {
            String item = itemFor(role, unit).toString();
            String expected = "permission-adjustment source-2 opened; event " + event.envelope().eventId() + "; item " + item + "; role " + role
                    + "; org unit " + unit + "(; trigger command " + event.orgStructureCommandId().value() + ")?";
            assertThat(alerts.stream().filter(line -> line.contains(item)).toList()).singleElement().satisfies(line -> {
                assertThat(line.substring(ALERT.length())).matches(expected);
                assertThat(line).doesNotContain("Synthetic", "F1-", "f1-wx-", "@example.test", firstCode, secondCode, "F1244-alert-D", account, employee);
            });
        }
        int beforeReplay = logMark();
        publishAndAwait(event);
        publishAndAwait(dissolution(unit));
        assertThat(alertsSince(beforeReplay).stream().filter(line -> line.startsWith(ALERT)).toList()).isEmpty();
    }

    @Test
    @DisplayName("#1244 ruling 1 source-2 Opened: the real event path makes a readable source-2 item with no user, role and org unit set, and no recipient")
    void aSourceTwoOpeningIsVisibleThroughTheReadApiAndStoredRow() throws Exception {
        UUID unit = unitId("F1244-read-D");
        String role = customRole("f1244-source2-read", onlyUnit(unit.toString()));
        OrgUnitDissolved event = dissolution(unit);

        publishAndAwait(event);

        UUID item = itemFor(role, unit);
        assertSourceTwo(item, role, unit, event.orgStructureCommandId().value());
        MockHttpServletResponse answer = read(session("oda"), item);
        require(answer, 200, "reading the item opened through the event bus");
        Map<String, Object> body = JsonPath.read(answer.getContentAsString(), "$");
        assertThat(body).containsEntry("permissionAdjustmentId", item.toString()).containsEntry("kind", SCOPE_REVIEW).containsEntry("status", "OPEN")
                .containsEntry("roleId", role).containsEntry("orgUnitId", unit.toString());
        assertThat(body.get("userId")).isNull();
        assertThat(select("platform_integration", "select subject_id from outbound_deliveries where subject_id = ?", item)).isEmpty();
    }
}
