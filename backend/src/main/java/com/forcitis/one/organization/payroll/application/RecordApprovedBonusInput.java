package com.forcitis.one.organization.payroll.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.payroll.api.BonusInputApprovalProcess;
import com.forcitis.one.organization.payroll.api.BonusInputId;
import com.forcitis.one.organization.payroll.domain.BonusDetermination;
import com.forcitis.one.organization.payroll.domain.BonusDeterminationRepository;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * RecordApprovedBonusInput (PY3): the approval owns the process, payroll owns the row, and this is where they meet. Runs for this process and the outcome APPROVED
 * only: nothing was written at start, so every other outcome has nothing to undo. Idempotent by approval instance. A malformed or duplicate approval writes no row
 * and is counted and logged for payroll operators; the log never carries the amount or the reason (SENSITIVE).
 */
@SystemActorName("organization.payroll.bonus-input-approval")
@Component
class RecordApprovedBonusInput {
    private static final Logger log = LoggerFactory.getLogger(RecordApprovedBonusInput.class);

    private final BonusDeterminationRepository inputs;
    private final ApprovalProcesses approvals;
    private final IdGenerator ids;
    private final Clock clock;
    private final MeterRegistry meters;

    RecordApprovedBonusInput(BonusDeterminationRepository inputs, ApprovalProcesses approvals, IdGenerator ids, Clock clock, MeterRegistry meters) {
        this.inputs = inputs;
        this.approvals = approvals;
        this.ids = ids;
        this.clock = clock;
        this.meters = meters;
    }

    @ApplicationModuleListener
    void on(ApprovalCompleted event) {
        if (!BonusInputApprovalProcess.KEY.equals(event.processKey()) || event.outcome() != ApprovalOutcome.APPROVED) {
            return;
        }
        if (inputs.existsForApproval(event.instanceId())) {
            return;
        }
        LegalEntityId payingEntity = event.envelope().legalEntityId();
        BonusDetermination input;
        try {
            if (payingEntity == null) {
                throw new IllegalStateException("an approval of a bonus input without its legal entity");
            }
            input = BonusDetermination.approved(new BonusInputId(ids.next()), payingEntity, event.initiator(), event.instanceId(), approvals.formFields(event.instanceId()),
                    clock.instant());
        } catch (DomainException | IllegalStateException malformed) {
            log.error("ALERT organization.payroll the approval {} of a bonus input is malformed and wrote no row: {}", event.instanceId().value(), malformed.getMessage());
            meters.counter("forcitis.organization.payroll.bonus.input.malformed").increment();
            return;
        }
        if (!inputs.insertApproved(input)) {
            log.warn("ALERT organization.payroll the approval {} of a bonus input repeats an active key and wrote no row", event.instanceId().value());
            meters.counter("forcitis.organization.payroll.bonus.input.duplicate").increment();
        }
    }
}
