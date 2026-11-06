package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.recruitment.api.RecruitmentError;
import com.forcitis.one.organization.recruitment.api.RequisitionApprovalProcess;
import com.forcitis.one.organization.recruitment.api.RequisitionDocument;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionRepository;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.organization.workforce.api.PositionLookup;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The requester and the legal entity come from the session, never from the request. The approval starts in the same transaction because the
 * row cannot be written without its approval instance. Submitting the same id again answers the first result and changes nothing.
 * The headcount is what the requester states and is checked against nothing (Q26).
 */
@Service
public class SubmitRequisition {

    private final CurrentUser currentUser;
    private final RequisitionRepository repository;
    private final RequisitionScope scope;
    private final PositionLookup positions;
    private final OrgDirectory org;
    private final EmployeeLegalEntities legalEntities;
    private final DocumentNumbers numbers;
    private final ApprovalProcesses approvals;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    SubmitRequisition(CurrentUser currentUser, RequisitionRepository repository, RequisitionScope scope, PositionLookup positions, OrgDirectory org,
            EmployeeLegalEntities legalEntities, DocumentNumbers numbers, ApprovalProcesses approvals, DomainEvents events, Clock clock, BusinessZone zone) {
        this.currentUser = currentUser;
        this.repository = repository;
        this.scope = scope;
        this.positions = positions;
        this.org = org;
        this.legalEntities = legalEntities;
        this.numbers = numbers;
        this.approvals = approvals;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public RequisitionView execute(SubmitRequisitionCommand command) {
        EmployeeId requester = currentUser.employeeId();
        repository.lockForCreate(command.requisitionId());
        Optional<RecruitmentRequisition> existing = repository.findForUpdate(command.requisitionId());
        if (existing.isPresent()) {
            if (!existing.get().requestedBy().equals(requester)) {
                throw RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND.refusal("No such requisition");
            }
            return RequisitionView.of(existing.get());
        }
        Instant now = clock.instant();
        LocalDate today = zone.today(clock);
        requireOrgUnit(command);
        if (!positions.isActive(command.positionId())) {
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_POSITION_NOT_ACTIVE.refusal("Position " + command.positionId().value() + " is not active today");
        }
        LegalEntityId legalEntity = legalEntities.on(requester, today);
        IssuedNumber issued = numbers.next(RequisitionDocument.TYPE, legalEntity, today);
        DocumentReference document = new DocumentReference(RequisitionDocument.TYPE, new DocumentId(command.requisitionId().value()));
        ApprovalInstanceId instance = approvals.start(new ApprovalStartRequest(RequisitionApprovalProcess.KEY, document, legalEntity, requester,
                new RoundNumber(1), command.requisitionId().value(), Set.of(),
                List.of(new ApprovalField(RequisitionApprovalProcess.ORG_UNIT_FIELD, List.of(command.orgUnitId().value().toString()))),
                contentHash(command), new ApprovalSummary(issued.number().value() + " x" + command.requestedCount())));
        RecruitmentRequisition requisition = RecruitmentRequisition.submit(command.requisitionId(), issued.number(), command.positionId(),
                command.orgUnitId(), legalEntity, requester, command.requestedCount(), command.expectedStartDate(), command.description(), instance,
                new AggregateReference("NumberingCounter", issued.counterId().value()), now, today);
        repository.save(requisition);
        events.publish(requisition);
        return RequisitionView.of(requisition);
    }

    /** An org unit that does not exist, is not active or lies outside the requester's scope for submitting is all the same answer. */
    private void requireOrgUnit(SubmitRequisitionCommand command) {
        Optional<OrgUnitProfile> unit = org.findOrgUnit(command.orgUnitId());
        if (unit.isEmpty() || !unit.get().active()) {
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_ORG_UNIT_INVALID.refusal("Org unit " + command.orgUnitId().value() + " is unknown or not active");
        }
        if (!scope.covers(RecruitmentPermissions.SUBMIT, command.orgUnitId())) {
            scope.denied(command.orgUnitId());
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_ORG_UNIT_INVALID.refusal("Org unit " + command.orgUnitId().value() + " is unknown or not active");
        }
    }

    private static ContentHash contentHash(SubmitRequisitionCommand c) {
        String text = c.positionId().value() + "|" + c.orgUnitId().value() + "|" + c.requestedCount() + "|" + c.expectedStartDate() + "|" + c.description();
        try {
            return new ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", impossible);
        }
    }
}
