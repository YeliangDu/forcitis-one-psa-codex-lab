package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentAdjusted;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentUpheld;
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

    private static PermissionAdjustment open() {
        PermissionAdjustment item = PermissionAdjustment.assignmentChangeReview(new PermissionAdjustmentId(UUID.randomUUID()), USER,
                new PersonnelCommandId(UUID.randomUUID()), TriggerFieldKind.ORG_UNIT, new OrgUnitId(UUID.randomUUID()), new OrgUnitId(UUID.randomUUID()), null, null,
                AT.plusSeconds(86_400), AT);
        item.drainEvents(TestEnvelopes.INSTANCE);
        return item;
    }

    private static final UserId HANDLER = TestAccounts.account(2);
    private static final Instant EXPIRY = Instant.parse("2026-12-31T16:00:00Z");

    @Test
    void upholdingASensitiveReviewKeepsTheOwnerAndTheExpiryAndSaysSo() {
        PermissionAdjustment item = open();
        EmployeeId owner = new EmployeeId(UUID.randomUUID());

        item.uphold(HANDLER, new UpholdReason("still needed"), owner, EXPIRY);

        assertThat(item.status()).isEqualTo(PermissionAdjustmentStatus.UPHELD);
        assertThat(item.upheldBy()).isEqualTo(HANDLER);
        assertThat(item.businessOwner()).isEqualTo(owner);
        assertThat(item.upholdExpiresAt()).isEqualTo(EXPIRY);
        assertThat(item.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentUpheld.class,
                upheld -> assertThat(upheld.auditDetail()).isEqualTo("item " + item.id().value() + " SENSITIVE_ROLE_REVIEW upheld; business owner " + owner.value()
                        + "; expires " + EXPIRY));
    }

    @Test
    void anItemTakesOneCommandAndNeverOneAboutTheHandler() {
        PermissionAdjustment item = open();
        item.uphold(HANDLER, new UpholdReason("first"), new EmployeeId(UUID.randomUUID()), EXPIRY);

        assertThatThrownBy(() -> item.markAdjusted(HANDLER, new AdjustmentReference(new AuthorizationRequestId(UUID.randomUUID()), null)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(IdentityError.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_NOT_OPEN));
        assertThatThrownBy(() -> open().markAdjusted(USER, new AdjustmentReference(null, new AuditEntryId(UUID.randomUUID()))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(IdentityError.PLATFORM_IDENTITY_OWN_PERMISSION_ADJUSTMENT_FORBIDDEN));
    }

    @Test
    void markingAdjustedKeepsTheReferenceAndSaysSo() {
        PermissionAdjustment item = open();
        AuditEntryId entry = new AuditEntryId(UUID.randomUUID());

        item.markAdjusted(HANDLER, new AdjustmentReference(null, entry));

        assertThat(item.status()).isEqualTo(PermissionAdjustmentStatus.ADJUSTED);
        assertThat(item.adjustedBy()).isEqualTo(HANDLER);
        assertThat(item.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentAdjusted.class,
                adjusted -> assertThat(adjusted.auditDetail()).isEqualTo("item " + item.id().value() + " SENSITIVE_ROLE_REVIEW adjusted; audit entry " + entry.value()));
    }
}
