package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.platform.approval.api.ApprovalError;
import org.springframework.security.access.AccessDeniedException;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalProgress;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.events.DocumentSubmittedForApproval;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Starts the round a document's owner asked for, in a transaction of its own that changes the approval request and nothing else.
 * Delivery is at least once and a round starts once per (document, round), so a redelivery returns the instance that exists, and a
 * request for a round older than the current one is ignored. A start that breaks a rule the initiator can put right (the approver
 * named on the document is not an employed person) ends the round as returned to its initiator by the system, with the reason; the
 * owner applies it like any other return. A fault of the set-up (no route, nobody for a node, a missing calendar year) and any
 * technical failure are logged as an alert, counted and left in the event registry for redelivery. A start asked for by somebody
 * other than the initiator is alerted and dropped: retrying cannot change who asked.
 */
@Component
class StartApprovalOnSubmission {

    private static final Logger log = LoggerFactory.getLogger(StartApprovalOnSubmission.class);

    /** What the initiator can put right by revising the document; every other refusal is a fault of the set-up and is retried after it is repaired. */
    private static final Set<ErrorCode> INITIATOR_CAN_FIX = Set.of(ApprovalError.PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID);

    private final ApprovalProcesses approvals;
    private final RefuseApprovalStart refuseStart;
    private final MeterRegistry meters;
    private final TransactionTemplate attempt;

    StartApprovalOnSubmission(ApprovalProcesses approvals, RefuseApprovalStart refuseStart, MeterRegistry meters, PlatformTransactionManager transactions) {
        this.approvals = approvals;
        this.refuseStart = refuseStart;
        this.meters = meters;
        this.attempt = new TransactionTemplate(transactions);
        this.attempt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @ApplicationModuleListener
    void on(DocumentSubmittedForApproval event) {
        Optional<ApprovalProgress> current = approvals.progress(event.processKey(), event.document());
        if (current.isPresent() && current.get().round().value() > event.round().value()) {
            log.warn("The request for round {} of {} is older than round {}: ignored", event.round().value(),
                    event.document().documentId().value(), current.get().round().value());
            meters.counter("forcitis.platform.approval.start.ignored").increment();
            return;
        }
        Set<String> flags = event.routingFlags().stream().map(flag -> flag.name()).collect(Collectors.toSet());
        ApprovalStartRequest request = new ApprovalStartRequest(event.processKey(), event.document(), event.envelope().legalEntityId(),
                event.initiator(), event.round(), event.envelope().eventId(), flags, event.formFields(), event.contentHash(), event.summary());
        try {
            attempt.executeWithoutResult(status -> approvals.start(request));
        } catch (DomainException refused) {
            if (INITIATOR_CAN_FIX.contains(refused.errorCode())) {
                log.warn("The approval of {} round {} could not start and goes back to its initiator: {}", event.document().documentId().value(),
                        event.round().value(), refused.toString());
                meters.counter("forcitis.platform.approval.start.refused").increment();
                refuseStart.execute(request, ApprovalTexts.startRefused(refused.errorCode().code()));
                return;
            }
            log.error("ALERT the approval of {} round {} could not start for a reason the initiator cannot fix: {}",
                    event.document().documentId().value(), event.round().value(), refused.toString());
            meters.counter("forcitis.platform.approval.start.failed").increment();
            throw refused;
        } catch (AccessDeniedException foreign) {
            log.error("ALERT the approval of {} round {} was asked for by somebody who is not its initiator: not retried, nothing started",
                    event.document().documentId().value(), event.round().value());
            meters.counter("forcitis.platform.approval.start.denied").increment();
        } catch (RuntimeException failed) {
            log.error("ALERT the approval of {} round {} could not start: {}", event.document().documentId().value(), event.round().value(),
                    failed.toString());
            meters.counter("forcitis.platform.approval.start.failed").increment();
            throw failed;
        }
    }
}
