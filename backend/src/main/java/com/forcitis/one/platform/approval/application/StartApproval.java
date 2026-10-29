package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.InstanceStart;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts of one document are serialised and keyed by (process type, document, round): asking again for the round that exists
 * returns its instance, the next round opens only after a return, rejection or cancellation, and only the previous round's
 * initiator may open it (D-1a-78).
 */
@Service
public class StartApproval {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StartApproval.class);

    private final ApprovalRequestRepository requests;
    private final ApprovalEngine engine;
    private final Approvals approvals;
    private final TaskRegistrar registrar;
    private final DenialAudit audit;
    private final BusinessTime time;
    private final ObjectProvider<CurrentUser> currentUser;

    StartApproval(ApprovalRequestRepository requests, ApprovalEngine engine, Approvals approvals, TaskRegistrar registrar,
            DenialAudit audit, BusinessTime time, ObjectProvider<CurrentUser> currentUser) {
        this.currentUser = currentUser;
        this.requests = requests;
        this.engine = engine;
        this.approvals = approvals;
        this.registrar = registrar;
        this.audit = audit;
        this.time = time;
    }

    /** The signed-in account asking; a start from an event handler acts as SYSTEM and is audited without an actor. */
    private UserId caller() {
        if (WorkContext.systemActor().isPresent()) {
            return null;
        }
        return currentUser.getObject().userId();
    }

    @Transactional
    public ApprovalInstanceId execute(ApprovalStartRequest request) {
        approvals.requireAcceptedDocumentType(request);
        requests.lockForStart(request.processKey(), request.document());
        Optional<ApprovalRequest> existing = requests.findForUpdate(request.processKey(), request.document());
        if (existing.isPresent()) {
            ApprovalRequest current = existing.get();
            if (!current.initiator().equals(request.initiator())) {
                audit.record(new AuditEntry("platform.approval.start.denied", caller(), null,
                        AuditSubject.of(AuditSubjectKind.APPROVAL_REQUEST, current.id().value()), "not the initiator"));
                throw new AccessDeniedException("The approval of " + current.id().value() + " was started by another user");
            }
            if (request.round().value() == current.round()) {
                return current.instanceId();
            }
            if (request.round().value() != current.round() + 1) {
                throw ApprovalError.PLATFORM_APPROVAL_ROUND_MISMATCH.refusal(current.id().value() + " is in round " + current.round()
                        + ", round " + request.round().value() + " was asked for");
            }
            if (current.status() == ApprovalStatus.APPROVED) {
                throw ApprovalError.PLATFORM_APPROVAL_ALREADY_APPROVED.refusal(current.id().value() + " is approved; it cannot be started again");
            }
        } else if (request.round().value() != 1) {
            throw ApprovalError.PLATFORM_APPROVAL_ROUND_MISMATCH.refusal("No approval of " + request.document().documentId().value()
                    + " exists, round " + request.round().value() + " was asked for");
        }
        requireKnownRoute(request);
        requireKnownFormFields(request);

        ApprovalRequest aggregate = approvals.openRound(request, existing);
        try (TaskRegistrar.Scope scope = registrar.open(aggregate)) {
            engine.startInstance(new InstanceStart(aggregate.instanceId(), request.processKey(), request.document(), request.legalEntityId(),
                    request.initiator(), request.contentHash(), variablesOf(request)));
        }
        if (!engine.isRunning(aggregate.instanceId())) {
            aggregate.approve(time.moment());
        }
        approvals.save(aggregate);
        return aggregate.instanceId();
    }

    private void requireKnownFormFields(ApprovalStartRequest request) {
        Set<String> known = engine.formFieldsOf(request.processKey());
        for (ApprovalField field : request.formFields()) {
            if (!known.contains(field.name())) {
                log.error("ALERT platform.approval the start of {} names the form field {}, which {} does not read: refused so that no node is skipped for lack of its approver",
                        request.document(), field.name(), request.processKey());
                throw ApprovalError.PLATFORM_APPROVAL_FORM_FIELD_UNKNOWN.refusal("The form field " + field.name() + " is not read by "
                        + request.processKey() + "; it reads " + known);
            }
        }
    }

    private void requireKnownRoute(ApprovalStartRequest request) {
        if (engine.routingFlagRequiredOf(request.processKey()) && request.routingFlags().isEmpty()) {
            throw ApprovalError.PLATFORM_APPROVAL_NO_ROUTE.refusal("No routing flag is set: " + request.processKey() + " would route nowhere");
        }
        Set<String> known = engine.routingFlagsOf(request.processKey());
        for (String flag : request.routingFlags()) {
            if (!known.contains(flag)) {
                throw ApprovalError.PLATFORM_APPROVAL_ROUTING_FLAG_UNKNOWN.refusal("The routing flag " + flag + " is not used by "
                        + request.processKey() + "; it knows " + known);
            }
        }
    }

    private static ProcessVariables variablesOf(ApprovalStartRequest request) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        for (ApprovalField field : request.formFields()) {
            fields.put(field.name(), field.values());
        }
        Map<String, List<String>> variables = new LinkedHashMap<>();
        request.routingFlags().forEach(flag -> variables.put(flag, List.of("true")));
        return new ProcessVariables(fields, variables);
    }
}
