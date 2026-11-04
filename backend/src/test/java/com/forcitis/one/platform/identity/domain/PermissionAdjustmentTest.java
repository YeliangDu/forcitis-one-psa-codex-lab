package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentClosedBySeparation;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentAdjusted;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentEscalated;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentExpired;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentUpheld;
import com.forcitis.one.platform.identity.events.PermissionAdjustmentOpened;
import java.time.Instant;
import java.util.List;
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
    void theUpholdEventCarriesTheReasonForTheAuditChangeReason() {
        PermissionAdjustment item = open();

        item.uphold(HANDLER, new UpholdReason("still needed"), new EmployeeId(UUID.randomUUID()), EXPIRY);

        assertThat(item.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentUpheld.class,
                upheld -> assertThat(upheld.reason().value()).isEqualTo("still needed"));
    }

    @Test
    void aReasonWithANulCharacterIsRefusedAsInvalidInput() {
        assertThatThrownBy(() -> new UpholdReason("a\u0000b")).isInstanceOf(com.forcitis.one.kernel.InvalidRequestException.class);
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

    private static PermissionAdjustment upheld() {
        PermissionAdjustment item = open();
        item.uphold(HANDLER, new UpholdReason("still needed"), new EmployeeId(UUID.randomUUID()), EXPIRY);
        item.drainEvents(TestEnvelopes.INSTANCE);
        return item;
    }

    @Test
    void anUpheldItemExpiresAtTheJobClockAndSaysWhetherThePersonWasHeld() {
        PermissionAdjustment item = upheld();

        item.expire(EXPIRY.plusSeconds(60), false);

        assertThat(item.status()).isEqualTo(PermissionAdjustmentStatus.EXPIRED);
        assertThat(item.expiredAt()).isEqualTo(EXPIRY.plusSeconds(60));
        assertThat(item.heldAtExpiry()).isFalse();
        assertThat(item.regenerationDecidedAt()).as("not held: decided at once").isEqualTo(EXPIRY.plusSeconds(60));
        assertThat(item.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentExpired.class,
                expired -> assertThat(expired.auditDetail()).isEqualTo("item " + item.id().value() + " SENSITIVE_ROLE_REVIEW expired at " + EXPIRY.plusSeconds(60)
                        + "; was valid until " + EXPIRY));
    }

    @Test
    void aHeldPersonsItemWaitsForTheDecisionAndTheDecisionIsTakenOnce() {
        PermissionAdjustment item = upheld();
        item.expire(EXPIRY, true);

        assertThat(item.regenerationDecidedAt()).isNull();

        item.decideRegeneration(EXPIRY.plusSeconds(3600));

        assertThat(item.regenerationDecidedAt()).isEqualTo(EXPIRY.plusSeconds(3600));
        assertThatThrownBy(() -> item.decideRegeneration(EXPIRY.plusSeconds(7200))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyAnUpheldItemAtItsExpiryExpires() {
        assertThatThrownBy(() -> upheld().expire(EXPIRY.minusSeconds(1), false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> open().expire(EXPIRY, false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theItemAnExpiryLeavesNamesTheExpiredOneAndNoTrigger() {
        PermissionAdjustment expired = upheld();
        expired.expire(EXPIRY, false);
        PermissionAdjustmentId id = new PermissionAdjustmentId(UUID.randomUUID());

        PermissionAdjustment next = PermissionAdjustment.expiryReview(id, expired, EXPIRY.plusSeconds(86_400), EXPIRY);

        assertThat(next.kind()).isEqualTo(PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW);
        assertThat(next.origin()).isEqualTo(PermissionAdjustmentOrigin.UPHOLD_EXPIRY);
        assertThat(next.status()).isEqualTo(PermissionAdjustmentStatus.OPEN);
        assertThat(next.user()).isEqualTo(USER);
        assertThat(next.original()).isEqualTo(expired.id());
        assertThat(next.triggerCommand()).isNull();
        assertThat(next.afterOrgUnit()).isNull();
        assertThat(next.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentOpened.class,
                opened -> assertThat(opened.auditDetail()).isEqualTo("item " + id.value() + " SENSITIVE_ROLE_REVIEW origin UPHOLD_EXPIRY; user " + USER.value()
                        + "; regenerates " + expired.id().value() + "; escalation due " + EXPIRY.plusSeconds(86_400)));
    }

    @Test
    void anOpenItemIsEscalatedOnceAtItsDueTimeAndStaysOpen() {
        PermissionAdjustment item = open();
        Instant due = AT.plusSeconds(86_400);

        assertThatThrownBy(() -> item.escalate(due.minusSeconds(1))).isInstanceOf(IllegalStateException.class);
        item.escalate(due);

        assertThat(item.status()).isEqualTo(PermissionAdjustmentStatus.OPEN);
        assertThat(item.escalatedAt()).isEqualTo(due);
        assertThat(item.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentEscalated.class,
                escalated -> assertThat(escalated.auditDetail()).isEqualTo("item " + item.id().value() + " SENSITIVE_ROLE_REVIEW escalated; was due " + due));
        assertThatThrownBy(() -> item.escalate(due.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> upheld().escalate(due)).isInstanceOf(IllegalStateException.class);
    }

    private static PermissionAdjustment openForUnit() {
        PermissionAdjustment item = PermissionAdjustment.openForInactiveOrgUnit(new PermissionAdjustmentId(UUID.randomUUID()), new RoleId(UUID.randomUUID()),
                new OrgUnitId(UUID.randomUUID()), new OrgStructureCommandId(UUID.randomUUID()), AT.plusSeconds(86_400), AT);
        item.drainEvents(TestEnvelopes.INSTANCE);
        return item;
    }

    @Test
    void aDissolvedOrgUnitOpensARoleItemWithNoPersonAndNoRecipient() {
        PermissionAdjustmentId id = new PermissionAdjustmentId(UUID.randomUUID());
        RoleId role = new RoleId(UUID.randomUUID());
        OrgUnitId unit = new OrgUnitId(UUID.randomUUID());
        OrgStructureCommandId command = new OrgStructureCommandId(UUID.randomUUID());

        PermissionAdjustment item = PermissionAdjustment.openForInactiveOrgUnit(id, role, unit, command, AT.plusSeconds(86_400), AT);

        assertThat(item.kind()).isEqualTo(PermissionAdjustmentKind.SCOPE_REVIEW);
        assertThat(item.origin()).isEqualTo(PermissionAdjustmentOrigin.ORG_UNIT_INACTIVE);
        assertThat(item.status()).isEqualTo(PermissionAdjustmentStatus.OPEN);
        assertThat(item.user()).isNull();
        assertThat(item.recipient()).isNull();
        assertThat(item.role()).isEqualTo(role);
        assertThat(item.inactiveOrgUnit()).isEqualTo(unit);
        assertThat(item.triggerOrgStructureCommand()).isEqualTo(command);
        assertThat(item.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentOpened.class,
                opened -> assertThat(opened.auditDetail()).isEqualTo("item " + id.value() + " SCOPE_REVIEW origin ORG_UNIT_INACTIVE; role " + role.value() + " org unit "
                        + unit.value() + "; trigger org structure command " + command.value() + "; escalation due " + AT.plusSeconds(86_400)));
    }

    @Test
    void theOpenedEventTakesExactlyTheSubjectOfItsOrigin() {
        PermissionAdjustment person = open();
        PermissionAdjustment role = openForUnit();
        assertThat(person.user()).isNotNull();
        assertThat(role.user()).isNull();
        EventEnvelope envelope = TestEnvelopes.INSTANCE.wrap(new EventDraft("platform.identity.PermissionAdjustmentOpened", 1, "PermissionAdjustment",
                UUID.randomUUID(), 1, null, null, List.of()));
        PermissionAdjustmentId id = new PermissionAdjustmentId(UUID.randomUUID());

        assertThatThrownBy(() -> new PermissionAdjustmentOpened(envelope, id, null, null, null, PermissionAdjustmentKind.SCOPE_REVIEW,
                PermissionAdjustmentOrigin.ORG_UNIT_INACTIVE, null, null, null, null, null, null, null, null, AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PermissionAdjustmentOpened(envelope, id, USER, null, null, PermissionAdjustmentKind.SCOPE_REVIEW,
                PermissionAdjustmentOrigin.ASSIGNMENT_CHANGE, null, new OrgStructureCommandId(UUID.randomUUID()), null, null, null, null, null, null, AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOpenPersonItemClosesBySeparationAndSaysSo() {
        PermissionAdjustment item = open();
        PersonnelCommandId command = new PersonnelCommandId(UUID.randomUUID());

        item.closeBySeparation(command, AT);

        assertThat(item.status()).isEqualTo(PermissionAdjustmentStatus.CLOSED_BY_SEPARATION);
        assertThat(item.closedAt()).isEqualTo(AT);
        assertThat(item.closurePersonnelCommand()).isEqualTo(command);
        assertThat(item.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentClosedBySeparation.class,
                closed -> assertThat(closed.auditDetail()).isEqualTo("item " + item.id().value() + " SENSITIVE_ROLE_REVIEW closed by separation; user " + USER.value()
                        + "; command " + command.value()));
    }

    @Test
    void onlyAnOpenPersonItemClosesBySeparation() {
        PermissionAdjustment upheld = open();
        upheld.uphold(HANDLER, new UpholdReason("still needed"), new EmployeeId(UUID.randomUUID()), EXPIRY);
        PersonnelCommandId command = new PersonnelCommandId(UUID.randomUUID());

        assertThatThrownBy(() -> upheld.closeBySeparation(command, AT)).isInstanceOfSatisfying(DomainException.class,
                refused -> assertThat(refused.errorCode()).isEqualTo(IdentityError.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_NOT_OPEN));
        assertThat(upheld.status()).isEqualTo(PermissionAdjustmentStatus.UPHELD);
        assertThatThrownBy(() -> openForUnit().closeBySeparation(command, AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theSystemMarksARoleItemAdjustedWithTheRoleVersionAuditEntryAndNamesNobody() {
        PermissionAdjustment item = openForUnit();
        AuditEntryId entry = new AuditEntryId(UUID.randomUUID());

        item.markAdjustedBySystem(entry);

        assertThat(item.status()).isEqualTo(PermissionAdjustmentStatus.ADJUSTED);
        assertThat(item.adjustedBy()).isNull();
        assertThat(item.adjustedWith()).isEqualTo(new AdjustmentReference(null, entry));
        assertThat(item.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(PermissionAdjustmentAdjusted.class, adjusted -> {
            assertThat(adjusted.adjustedBy()).isNull();
            assertThat(adjusted.auditDetail()).isEqualTo("item " + item.id().value() + " SCOPE_REVIEW adjusted; audit entry " + entry.value());
        });
    }

    @Test
    void theSystemMarksOnlyAnOpenRoleItemAdjusted() {
        AuditEntryId entry = new AuditEntryId(UUID.randomUUID());
        PermissionAdjustment done = openForUnit();
        done.markAdjustedBySystem(entry);

        assertThatThrownBy(() -> done.markAdjustedBySystem(entry)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> open().markAdjustedBySystem(entry)).isInstanceOf(IllegalStateException.class);
    }
}
