package com.forcitis.one.organization.payroll.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.payroll.api.BonusInputApprovalProcess;
import com.forcitis.one.organization.payroll.api.BonusInputId;
import com.forcitis.one.organization.payroll.api.BonusInputSummary;
import com.forcitis.one.organization.payroll.api.BonusInputs;
import com.forcitis.one.organization.payroll.api.PayrollError;
import com.forcitis.one.organization.payroll.api.RequestBonusInput;
import com.forcitis.one.organization.payroll.api.VoidBonusInput;
import com.forcitis.one.organization.payroll.domain.BonusDetermination;
import com.forcitis.one.organization.payroll.domain.BonusDeterminationRepository;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationships;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bonus input port. Starting the approval writes no payroll row (a row is always an approved input); the void is the one update. The approval is started
 * directly in the command's transaction, not through a broadcast event, so the amount reaches no subscriber.
 */
@Component
class BonusInputsService implements BonusInputs {
    static final String SUBMIT = "organization.payroll:bonus-input:submit";
    static final String UPDATE = "organization.payroll:bonus-input:update";
    static final String READ = "organization.payroll:bonus-input:read";
    private static final String REQUEST_NAMESPACE = "organization.payroll.bonus-input-request:";

    private final BonusDeterminationRepository inputs;
    private final ApprovalProcesses approvals;
    private final EmploymentRelationships relationships;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    BonusInputsService(BonusDeterminationRepository inputs, ApprovalProcesses approvals, EmploymentRelationships relationships, AuditLog audit, CurrentUser currentUser,
            Clock clock) {
        this.inputs = inputs;
        this.approvals = approvals;
        this.relationships = relationships;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Override
    @PreAuthorize("hasAuthority('" + SUBMIT + "')")
    @Transactional
    public ApprovalInstanceId request(RequestBonusInput command) {
        BonusDetermination.check(command.payrollMonthStartDate(), command.bonus(), command.reasonKey());
        if (relationships.get(command.employmentRelationshipId()).isEmpty()) {
            throw new DomainException(PayrollError.ORGANIZATION_PAYROLL_BONUS_INPUT_INVALID, ErrorKind.UNPROCESSABLE, "no such employment relationship");
        }
        UUID requestId = UUID.nameUUIDFromBytes((REQUEST_NAMESPACE + command.idempotencyKey()).getBytes(StandardCharsets.UTF_8));
        List<ApprovalField> fields = List.of(field(BonusInputApprovalProcess.EMPLOYMENT_RELATIONSHIP_ID, command.employmentRelationshipId().toString()),
                field(BonusInputApprovalProcess.PAYROLL_MONTH_START_DATE, command.payrollMonthStartDate().toString()),
                field(BonusInputApprovalProcess.BONUS_AMOUNT, command.bonus().amount().toPlainString()),
                field(BonusInputApprovalProcess.BONUS_CURRENCY, command.bonus().currency().getCurrencyCode()), field(BonusInputApprovalProcess.REASON_KEY, command.reasonKey()));
        return approvals.start(new ApprovalStartRequest(BonusInputApprovalProcess.KEY,
                new DocumentReference(BonusInputApprovalProcess.DOCUMENT_TYPE, new DocumentId(requestId)), command.payingLegalEntityId(), currentUser.employeeId(),
                new RoundNumber(1), command.idempotencyKey(), Set.of(), fields, contentHash(fields),
                new ApprovalSummary("奖金输入 " + command.payrollMonthStartDate() + " " + command.reasonKey() + " " + command.bonus().amount().toPlainString() + " "
                        + command.bonus().currency().getCurrencyCode())));
    }

    @Override
    @PreAuthorize("hasAuthority('" + UPDATE + "')")
    @Transactional
    public void voidInput(VoidBonusInput command) {
        BonusDetermination input = inputs.findForUpdate(command.id()).orElseThrow(() -> notFound(command.id()));
        // PY4 adds the refusal while an APPROVED settlement of the entity and month exists, taking FOR UPDATE on that settlement first; no settlement exists yet.
        UserId by = currentUser.userId();
        input.voidBy(by, clock.instant());
        inputs.saveVoided(input);
        audit.record(new AuditEntry("organization.payroll.bonus-input.voided", by, null, AuditSubject.without(AuditSubjectKind.OPERATION),
                "bonusInputId=" + input.id()));
    }

    @Override
    @PreAuthorize("hasAuthority('" + READ + "')")
    @Transactional(readOnly = true)
    public BonusInputSummary get(BonusInputId id) {
        return summary(inputs.find(id).orElseThrow(() -> notFound(id)));
    }

    @Override
    @PreAuthorize("hasAuthority('" + READ + "')")
    @Transactional(readOnly = true)
    public List<BonusInputSummary> listForRelationship(EmploymentRelationshipId relationship, LocalDate fromMonth, LocalDate toMonth) {
        return inputs.listForRelationship(relationship, fromMonth, toMonth).stream().map(BonusInputsService::summary).toList();
    }

    private static BonusInputSummary summary(BonusDetermination input) {
        return new BonusInputSummary(input.id(), input.employmentRelationshipId(), input.payingLegalEntityId(), input.payrollMonthStartDate(), input.bonus(),
                input.reasonKey(), input.status(), input.enteredByEmployeeId(), input.voidedByUserId());
    }

    private static DomainException notFound(BonusInputId id) {
        return new DomainException(PayrollError.ORGANIZATION_PAYROLL_BONUS_INPUT_NOT_FOUND, ErrorKind.NOT_FOUND, "no bonus input " + id);
    }

    private static ApprovalField field(String name, String value) {
        return new ApprovalField(name, List.of(value));
    }

    private static ContentHash contentHash(List<ApprovalField> fields) {
        StringBuilder content = new StringBuilder();
        fields.forEach(each -> content.append(each.name()).append('=').append(each.values().getFirst()).append('\n'));
        try {
            return new ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
