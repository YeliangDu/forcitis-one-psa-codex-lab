package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmploymentVerdict;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.domain.UpholdReason;
import java.util.UUID;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The handler keeps the authorizations as they are and says why (ADR-0032 decision 5). A sensitive-role review also names the business owner. */
@Service
public class UpholdPermissionAdjustment {

    private final PermissionAdjustmentRepository adjustments;
    private final PermissionAdjustmentAccess access;
    private final PermissionAdjustmentViews views;
    private final CurrentUser currentUser;
    private final AccountEmployees accountEmployees;
    private final OrgDirectory orgDirectory;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    UpholdPermissionAdjustment(PermissionAdjustmentRepository adjustments, PermissionAdjustmentAccess access, CurrentUser currentUser,
            AccountEmployees accountEmployees, OrgDirectory orgDirectory, DomainEvents events, Clock clock, BusinessZone zone, PermissionAdjustmentViews views) {
        this.views = views;
        this.adjustments = adjustments;
        this.access = access;
        this.currentUser = currentUser;
        this.accountEmployees = accountEmployees;
        this.orgDirectory = orgDirectory;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public PermissionAdjustmentView execute(PermissionAdjustmentId id, ExpectedVersion expected, @Nullable String reasonText, @Nullable UUID ownerId) {
        UpholdReason reason = new UpholdReason(reasonText);
        EmployeeId owner = ownerId == null ? null : new EmployeeId(ownerId);
        PermissionAdjustment item = access.handleable(id, expected);
        if (item.kind() != PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW) {
            if (owner != null) {
                throw new InvalidRequestException("only a sensitive-role review takes a business owner");
            }
        } else {
            requireOwner(item, owner);
        }
        item.uphold(currentUser.userId(), reason, owner, UpholdExpiry.after(clock.instant(), zone).value());
        adjustments.update(item);
        events.publish(item);
        return views.of(item);
    }

    private void requireOwner(PermissionAdjustment item, @Nullable EmployeeId owner) {
        if (owner == null) {
            throw new InvalidRequestException("a sensitive-role review is upheld with a business owner");
        }
        if (orgDirectory.employmentOf(owner) != EmploymentVerdict.EMPLOYED) {
            throw new InvalidRequestException("the business owner is not an employed employee");
        }
        if (item.user() != null && owner.equals(accountEmployees.employeeOf(item.user()).orElse(null))) {
            throw new InvalidRequestException("the business owner cannot be the person the item is about");
        }
    }
}
