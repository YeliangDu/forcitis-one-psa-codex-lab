package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** #1244, ruling 3-5 and F-1 invariant 28: the HTTP role revision and both real listeners form one observable chain. */
class RoleRevisedAdjustsScopeReviewIT extends PermissionAdjustmentConsumerSupport {

    static final TestDatabase postgres = TestPostgres.database(RoleRevisedAdjustsScopeReviewIT.class, TestPostgres.MIGRATED_TESTDATA)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void organisation() throws Exception {
        consumerWorld("F1244-adjust-D1", "F1244-adjust-D2", "F1244-kept-D", "F1244-upheld-D", "F1244-audit-D");
    }

    private void revise(String role, String body) throws Exception {
        advanceTo(clock.instant().plusSeconds(1));
        require(reviseRole(administrator("admin1"), role, body), 200, "revising an ordinary role over HTTP");
        revisionHandled(role);
    }

    private void assertAdjusted(UUID item, UUID audit) throws Exception {
        assertThat(rowOf(item)).containsEntry("status", "ADJUSTED").containsEntry("adjusted_by", null)
                .containsEntry("adjusted_audit_entry_id", audit).containsEntry("adjusted_authorization_request_id", null);
        assertThat(rowOf(item).get("adjusted_at")).isNotNull();
    }

    @Test
    @DisplayName("#1244 F-1 invariant 28: dropping only D1 adjusts only its OPEN item, with no adjusted_by and exactly the new role-version audit reference")
    void droppingOneUnitAdjustsOnlyItsItemWithTheNewVersionAuditId() throws Exception {
        UUID firstUnit = unitId("F1244-adjust-D1");
        UUID secondUnit = unitId("F1244-adjust-D2");
        String code = "f1244-adjust-two";
        String role = customRole(code, onlyUnit(firstUnit.toString()), onlyUnit(secondUnit.toString()));
        publishAndAwait(dissolution(firstUnit));
        publishAndAwait(dissolution(secondUnit));
        UUID first = itemFor(role, firstUnit);
        UUID second = itemFor(role, secondUnit);
        assertThat(rowOf(first)).containsEntry("status", "OPEN");
        assertThat(rowOf(second)).containsEntry("status", "OPEN");
        Map<String, Object> secondBefore = rowOf(second);
        Map<String, Object> previousVersion = currentVersion(role);

        revise(role, roleBody(code, "CUSTOM_ORG_UNITS", List.of(onlyUnit(secondUnit.toString())), ORG_UNIT_READ));

        Map<String, Object> version = currentVersion(role);
        assertThat(version.get("role_version_id")).isNotEqualTo(previousVersion.get("role_version_id"));
        assertThat(version.get("version_audit_entry_id")).isNotNull();
        assertAdjusted(first, (UUID) version.get("version_audit_entry_id"));
        assertThat(rowOf(second)).isEqualTo(secondBefore);
    }

    @Test
    @DisplayName("#1244 rulings 4, 5c: a revision still listing D and a RoleRevised naming the kept version leave its OPEN item unchanged")
    void aRevisionStillListingTheUnitAndAnEventNamingTheKeptVersionChangeNothing() throws Exception {
        UUID unit = unitId("F1244-kept-D");
        String code = "f1244-kept-unit";
        String role = customRole(code, onlyUnit(unit.toString()));
        publishAndAwait(dissolution(unit));
        UUID item = itemFor(role, unit);
        Map<String, Object> before = rowOf(item);
        assertThat(before).containsEntry("status", "OPEN");
        Map<String, Object> oldVersion = currentVersion(role);
        String renamed = roleBody(code, "CUSTOM_ORG_UNITS", List.of(onlyUnit(unit.toString())), ORG_UNIT_READ)
                .replace("Synthetic role " + code, "Synthetic renamed role");

        revise(role, renamed);

        assertThat(currentVersion(role).get("role_version_id")).isNotEqualTo(oldVersion.get("role_version_id"));
        assertThat(rowOf(item)).isEqualTo(before);
        publishAndAwait(keptVersion(role));
        assertThat(rowOf(item)).isEqualTo(before);
        assertThat(eventsOf("PermissionAdjustmentAdjusted", item)).isEmpty();
        assertThat(auditsOf(ADJUSTED_ACTION, item)).isEmpty();
    }

    @Test
    @DisplayName("#1244 ruling 5d: dropping a unit leaves an UPHELD source-2 item and the OPEN item of another role untouched")
    void anUpheldItemAndAnotherRolesItemStayUntouched() throws Exception {
        UUID unit = unitId("F1244-upheld-D");
        UUID retained = unitId("F1-A");
        String code = "f1244-upheld-target";
        String role = customRole(code, onlyUnit(unit.toString()), onlyUnit(retained.toString()));
        String otherRole = customRole("f1244-upheld-other", onlyUnit(unit.toString()));
        UUID upheld = sourceTwoItem(role, unit, true);
        publishAndAwait(dissolution(unit));
        UUID other = itemFor(otherRole, unit);
        Map<String, Object> upheldBefore = rowOf(upheld);
        Map<String, Object> otherBefore = rowOf(other);
        assertThat(upheldBefore).containsEntry("status", "UPHELD");
        assertThat(otherBefore).containsEntry("status", "OPEN");

        revise(role, roleBody(code, "CUSTOM_ORG_UNITS", List.of(onlyUnit(retained.toString())), ORG_UNIT_READ));

        assertThat(rowOf(upheld)).isEqualTo(upheldBefore);
        assertThat(rowOf(other)).isEqualTo(otherBefore);
        assertThat(auditsOf(ADJUSTED_ACTION, upheld)).isEmpty();
        assertThat(auditsOf(ADJUSTED_ACTION, other)).isEmpty();
    }

    @Test
    @DisplayName("#1244 ruling 3: system adjustment emits one Adjusted event with adjustedBy null and one system audit containing only the registered item and audit reference")
    void aSystemAdjustmentHasNoAdjustedByInTheRowEventOrAudit() throws Exception {
        UUID unit = unitId("F1244-audit-D");
        UUID retained = unitId("F1-A");
        String code = "f1244-system-audit";
        String role = customRole(code, onlyUnit(unit.toString()), onlyUnit(retained.toString()));
        publishAndAwait(dissolution(unit));
        UUID item = itemFor(role, unit);

        revise(role, roleBody(code, "CUSTOM_ORG_UNITS", List.of(onlyUnit(retained.toString())), ORG_UNIT_READ));

        UUID reference = (UUID) currentVersion(role).get("version_audit_entry_id");
        assertAdjusted(item, reference);
        eventually(() -> {
            assertThat(auditsOf(ADJUSTED_ACTION, item)).singleElement().satisfies(entry -> {
                assertThat(entry).containsEntry("subjectKind", "PERMISSION_ADJUSTMENT").containsEntry("subjectId", item.toString())
                        .containsEntry("detail", "item " + item + " SCOPE_REVIEW adjusted; audit entry " + reference);
                assertThat(entry.get("actorId")).isNull();
            });
            assertThat(eventsOf("PermissionAdjustmentAdjusted", item)).singleElement().satisfies(event -> {
                assertThat(idValue(event.get("permissionAdjustmentId"))).isEqualTo(item.toString());
                assertThat(event.get("adjustedBy")).isNull();
                assertThat(idValue(event.get("auditEntryId"))).isEqualTo(reference.toString());
                assertThat(event.get("authorizationRequestId")).isNull();
                assertThat(((Map<?, ?>) event.get("envelope")).get("actorType")).isEqualTo("SYSTEM");
            });
        });
    }
}
