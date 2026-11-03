package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.AccountRoles;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Who may decide a countersign request, whatever command it belongs to: the finance head of the row's legal entity in today's version, or a business administrator when
 * the row has escalated to level 2 (user answer Q3 of 2026-10-30); never the drafter or the requester.
 */
@Component
class CountersignDeciders {
    /** The role that may decide a request from escalation level 2 on. */
    static final String ESCALATED_ROLE = "business_admin";

    private final AttributionCountersignRepository countersigns;
    private final AccountRoles roles;
    private final CurrentUser currentUser;

    CountersignDeciders(AttributionCountersignRepository countersigns, AccountRoles roles, CurrentUser currentUser) {
        this.countersigns = countersigns;
        this.roles = roles;
        this.currentUser = currentUser;
    }

    void require(AttributionCountersign row, UserId by, LocalDate today) {
        requireNotParty(row, by);
        requireHead(row, by, today);
    }

    void requireNotParty(AttributionCountersign row, UserId by) {
        if (by.equals(row.draftedBy()) || by.equals(row.requestedBy())) {
            throw forbidden("the drafter and the requester of a countersign do not decide it");
        }
    }

    void requireHead(AttributionCountersign row, UserId by, LocalDate today) {
        boolean head = countersigns.isFinanceHead(row.legalEntityId(), currentUser.employeeId(), today);
        boolean escalated = row.escalationLevel() >= AttributionCountersign.ESCALATED_FROM_LEVEL && roles.holds(by, ESCALATED_ROLE);
        if (!head && !escalated) {
            throw forbidden("only the finance head of the legal entity decides this countersign, a business administrator from escalation level "
                    + AttributionCountersign.ESCALATED_FROM_LEVEL);
        }
    }

    private static DomainException forbidden(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_FORBIDDEN, ErrorKind.FORBIDDEN, message);
    }
}
