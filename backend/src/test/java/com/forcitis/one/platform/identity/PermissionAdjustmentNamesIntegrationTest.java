package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.domain.UpholdReason;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * #1297 item 0: the permission adjustment read contract names the people, the role and the org unit an item is about and carries the trigger, so the page needs no
 * second call. A name that cannot be resolved is an explicit null; the id stays.
 */
@DisplayName("#1297 permission adjustment read contract: names and trigger")
class PermissionAdjustmentNamesIntegrationTest extends IdentityIntegrationTest {

    private static final String ITEMS = "/api/v1/platform/permission-adjustments";

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PermissionAdjustmentRepository adjustments;

    @AfterEach
    void removeItems() {
        jdbc.sql("delete from platform_identity.permission_adjustments").update();
    }

    /** A person whose custom role holds only the read code: the reader that sees every item not about themselves. */
    private String readerSession() throws Exception {
        TestRoles.insertCustom(jdbc, "adjustment-reader", "Adjustment reader", "ENABLED", "SELF", Permission.Codes.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_READ);
        createAccount(9, "adjustment-reader");
        return loginWithWecom(9);
    }

    private JsonNode read(String administrator, PermissionAdjustmentId id) throws Exception {
        var response = mvc.perform(get(ITEMS + "/" + id.value()).cookie(new Cookie("SESSION", administrator))).andReturn();
        assertThat(response.getResponse().getStatus()).as(response.getResponse().getContentAsString()).isEqualTo(200);
        return body(response);
    }

    private PermissionAdjustmentId insert(PermissionAdjustment item) {
        tx.executeWithoutResult(status -> adjustments.insert(item));
        return item.id();
    }

    private static PermissionAdjustmentId newId() {
        return new PermissionAdjustmentId(UUID.randomUUID());
    }

    @Test
    @DisplayName("#1297 every origin shows the ids, the resolved names and the trigger it has; a person the directory does not know is a null name")
    void namesAndTriggersPerOrigin() throws Exception {
        String administrator = readerSession();
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        createAccount(1001);
        createAccount(1002); // the directory does not know this person
        knownEmployee(1003, EmploymentStatus.EMPLOYED);
        OrgUnitId unit = TestOrgIds.orgUnit(10);
        OrgUnitId inactive = TestOrgIds.orgUnit(77);
        when(orgDirectory.findOrgUnit(inactive)).thenReturn(Optional.of(new OrgUnitProfile(inactive, "Dissolved unit", null, false)));
        RoleId role = new RoleId(jdbc.sql("select role_id from platform_identity.roles where lower(code) = 'employee'").query(UUID.class).single());
        var at = clock.instant();
        PersonnelCommandId command = new PersonnelCommandId(UUID.randomUUID());
        OrgStructureCommandId structure = new OrgStructureCommandId(UUID.randomUUID());

        PermissionAdjustmentId assignment = insert(PermissionAdjustment.assignmentChangeReview(newId(), TestAccounts.account(1001), command,
                TriggerFieldKind.ORG_UNIT, null, unit, null, null, at.plusSeconds(86_400), at));
        PermissionAdjustmentId unpaid = insert(PermissionAdjustment.unpaidLeaveReview(newId(), TestAccounts.account(1002),
                new PersonnelCommandId(UUID.randomUUID()), at.plusSeconds(86_400), at));
        PermissionAdjustmentId upheld = insert(PermissionAdjustment.assignmentChangeReview(newId(), TestAccounts.account(1001),
                new PersonnelCommandId(UUID.randomUUID()), TriggerFieldKind.ORG_UNIT, inactive, unit, null, null, at.plusSeconds(86_400), at));
        PermissionAdjustment toUphold = adjustments.findById(upheld).orElseThrow();
        toUphold.uphold(TestAccounts.account(1002), new UpholdReason("still needed"), TestAccounts.employee(1003), at.plusSeconds(86_400 * 30L));
        tx.executeWithoutResult(status -> adjustments.update(toUphold));
        PermissionAdjustmentId org = insert(PermissionAdjustment.openForInactiveOrgUnit(newId(), role, inactive, structure, at.plusSeconds(86_400), at));
        PermissionAdjustmentId expired = insert(PermissionAdjustment.expiryReview(newId(), adjustments.findById(assignment).orElseThrow(), at.plusSeconds(86_400), at));

        JsonNode assignmentItem = read(administrator, assignment);
        assertThat(assignmentItem.path("userId").asString()).isEqualTo(TestAccounts.account(1001).value().toString());
        assertThat(assignmentItem.path("userName").asString()).isEqualTo("Synthetic");
        assertThat(assignmentItem.path("triggerField").asString()).isEqualTo("ORG_UNIT");
        assertThat(assignmentItem.path("triggerPersonnelCommandId").asString()).isEqualTo(command.value().toString());
        assertThat(assignmentItem.path("triggerOrgStructureCommandId").isNull()).isTrue();
        assertThat(assignmentItem.path("roleName").isNull()).isTrue();
        assertThat(assignmentItem.path("businessOwnerName").isNull()).isTrue();

        JsonNode unpaidItem = read(administrator, unpaid);
        assertThat(unpaidItem.path("userId").asString()).isEqualTo(TestAccounts.account(1002).value().toString());
        assertThat(unpaidItem.path("userName").isNull()).as("an unknown person is an explicit null, the id stays").isTrue();
        assertThat(unpaidItem.path("triggerField").isNull()).isTrue();
        assertThat(unpaidItem.path("triggerPersonnelCommandId").isString()).isTrue();

        JsonNode upheldItem = read(administrator, upheld);
        assertThat(upheldItem.path("status").asString()).isEqualTo("UPHELD");
        assertThat(upheldItem.path("businessOwnerEmployeeId").asString()).isEqualTo(TestAccounts.employee(1003).value().toString());
        assertThat(upheldItem.path("businessOwnerName").asString()).isEqualTo("Synthetic");
        assertThat(upheldItem.path("upholdExpiresAt").isString()).isTrue();

        JsonNode orgItem = read(administrator, org);
        assertThat(orgItem.path("roleId").asString()).isEqualTo(role.value().toString());
        assertThat(orgItem.path("roleName").asString()).isNotBlank();
        assertThat(orgItem.path("orgUnitId").asString()).isEqualTo(inactive.value().toString());
        assertThat(orgItem.path("orgUnitName").asString()).isEqualTo("Dissolved unit");
        assertThat(orgItem.path("userId").isNull()).isTrue();
        assertThat(orgItem.path("userName").isNull()).isTrue();
        assertThat(orgItem.path("triggerOrgStructureCommandId").asString()).isEqualTo(structure.value().toString());
        assertThat(orgItem.path("triggerPersonnelCommandId").isNull()).isTrue();

        JsonNode expiredItem = read(administrator, expired);
        assertThat(expiredItem.path("triggerField").isNull()).as("an expiry successor names no trigger").isTrue();
        assertThat(expiredItem.path("triggerPersonnelCommandId").isNull()).isTrue();
        assertThat(expiredItem.path("triggerOrgStructureCommandId").isNull()).isTrue();
        assertThat(expiredItem.path("userName").asString()).isEqualTo("Synthetic");
    }

    @Test
    @DisplayName("#1297 the list carries the same names as the single read")
    void listCarriesNames() throws Exception {
        String administrator = readerSession();
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        createAccount(1001);
        var at = clock.instant();
        PermissionAdjustmentId id = insert(PermissionAdjustment.unpaidLeaveReview(newId(), TestAccounts.account(1001), new PersonnelCommandId(UUID.randomUUID()),
                at.plusSeconds(86_400), at));
        var response = mvc.perform(get(ITEMS).cookie(new Cookie("SESSION", administrator))).andReturn();
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        JsonNode listed = null;
        for (JsonNode item : body(response).path("items")) {
            if (id.value().toString().equals(item.path("permissionAdjustmentId").asString())) {
                listed = item;
            }
        }
        assertThat(listed).as(body(response).toString()).isNotNull();
        assertThat(listed.path("userName").asString()).isEqualTo("Synthetic");
        assertThat(listed.path("originKind").asString()).isEqualTo("UNPAID_LEAVE_START");
    }
}
