package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CallerPermissions;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CountersignRequestKind;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Who may read a countersign request or the basis of an org-structure command (#910 amendment B, ruling comment 6588705997): everyone else is told "not found", and the attempt is audited
 * (ADR-0026 item 1.7). The function permissions are checked by the controller before this runs; the read use cases only format what this lets through.
 */
@Component
class CountersignReadAccess {
    /** A request the caller may decide, with the other requests of its command and the command itself (exactly one of the two commands is set). */
    record Readable(AttributionCountersign row, List<AttributionCountersign> requests, @Nullable OrgStructureCommand orgCommand, @Nullable PersonnelCommand personnelCommand) {
    }

    private final AttributionCountersignRepository countersigns;
    private final PersonnelCommandRepository personnel;
    private final OrgStructureCommandRepository orgCommands;
    private final CountersignDeciders deciders;
    private final CallerPermissions permissions;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final BusinessZone zone;

    CountersignReadAccess(AttributionCountersignRepository countersigns, PersonnelCommandRepository personnel, OrgStructureCommandRepository orgCommands, CountersignDeciders deciders,
                          CallerPermissions permissions, AuditLog audit, CurrentUser currentUser, Clock clock, BusinessZone zone) {
        this.countersigns = countersigns;
        this.personnel = personnel;
        this.orgCommands = orgCommands;
        this.deciders = deciders;
        this.permissions = permissions;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
        this.zone = zone;
    }

    Readable ofRequest(AttributionCountersignId id) {
        AttributionCountersign row = countersigns.find(id).orElseThrow(() -> notFound("no such countersign request"));
        if (!deciders.mayDecide(row, currentUser.userId(), zone.dateOf(clock.instant()))) {
            throw denied(row.isOrg() ? AuditSubject.of(AuditSubjectKind.ORG_STRUCTURE_COMMAND, row.orgCommandId().value()) : AuditSubject.of(AuditSubjectKind.EMPLOYEE, row.employeeId().value()),
                    "no such countersign request");
        }
        if (row.isOrg()) {
            OrgStructureCommand command = orgCommands.find(row.orgCommandId()).orElseThrow(() -> notFound("no such countersign request"));
            return new Readable(row, countersigns.ofOrgCommand(row.orgCommandId()), command, null);
        }
        PersonnelCommand command = personnel.find(row.commandId()).orElseThrow(() -> notFound("no such countersign request"));
        return new Readable(row, countersigns.ofCommand(row.commandId()), null, command);
    }

    /** The org data administrator reads the basis of any command; a finance head of an entity the command asks to countersign reads it too. */
    void requireBasisReadable(OrgStructureCommandId command) {
        if (orgCommands.find(command).isEmpty()) {
            throw notFound("no such org-structure command");
        }
        if (permissions.holds(OrgStructureCommandService.PERMISSION)) {
            return;
        }
        EmployeeId caller = currentUser.employeeId();
        LocalDate today = zone.dateOf(clock.instant());
        boolean head = countersigns.ofOrgCommand(command).stream().filter(r -> r.requestKind() == CountersignRequestKind.CHANGE).map(AttributionCountersign::legalEntityId).distinct()
                .anyMatch(entity -> countersigns.isFinanceHead(entity, caller, today));
        if (!head) {
            throw denied(AuditSubject.of(AuditSubjectKind.ORG_STRUCTURE_COMMAND, command.value()), "no such org-structure command");
        }
    }

    private DomainException denied(AuditSubject subject, String message) {
        audit.recordIndependently(new AuditEntry("organization.workforce.countersign-read.denied", currentUser.userId(), null, subject, "not the decider of the request or a reader of the basis"));
        return notFound(message);
    }

    private static DomainException notFound(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND, ErrorKind.NOT_FOUND, message);
    }
}
