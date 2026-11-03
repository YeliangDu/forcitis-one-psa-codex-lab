package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentOpened;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PermissionAdjustment (domain): the review item of an undone separation")
class PermissionAdjustmentTest {

    static final Instant AT = Instant.parse("2026-11-02T08:00:00Z");
    static final UserId USER = TestAccounts.account(1);

    @Test
    void anUndoneSeparationOpensAPersonItemWithNoDepartmentBefore() {
        PermissionAdjustmentId id = new PermissionAdjustmentId(UUID.randomUUID());
        OrgUnitId unit = new OrgUnitId(UUID.randomUUID());

        PersonnelCommandId command = new PersonnelCommandId(UUID.randomUUID());
        PermissionAdjustment item = PermissionAdjustment.assignmentChangeReview(id, USER, command, TriggerFieldKind.ORG_UNIT, null, unit, null, null,
                AT.plusSeconds(86_400), AT);

        assertThat(item.kind()).isEqualTo(PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW);
        assertThat(item.origin()).isEqualTo(PermissionAdjustmentOrigin.ASSIGNMENT_CHANGE);
        assertThat(item.status()).isEqualTo(PermissionAdjustmentStatus.OPEN);
        assertThat(item.triggerField()).isEqualTo(TriggerFieldKind.ORG_UNIT);
        assertThat(item.beforeOrgUnit()).isNull();
        assertThat(item.beforePosition()).isNull();
        assertThat(item.afterOrgUnit()).isEqualTo(unit);
        assertThat(item.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentOpened.class,
                opened -> {
                    assertThat(opened.permissionAdjustmentId()).isEqualTo(id);
                    assertThat(opened.auditDetail()).isEqualTo("item " + id.value() + " SENSITIVE_ROLE_REVIEW origin ASSIGNMENT_CHANGE; user " + USER.value()
                            + "; trigger command " + command.value() + " field ORG_UNIT; after org " + unit.value() + "; escalation due " + AT.plusSeconds(86_400));
                });
    }
}
