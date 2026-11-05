package com.forcitis.one.acceptance.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * #1436 acceptance part (PY3 bonus input port). Sources: the signed PY3 record, section "Approval seam, process and permissions" (docs/data/reviews/
 * organization-payroll-py3.md), and the user's routing decision of 2026-11-05 (docs/decisions/2026-11-05-195500-one-lead-均采纳推荐.md, item 1): a bonus input
 * enters through a real approval of the process {@code organization_payroll_bonus_input} with the five signed form fields, in ONE stage: the initiator's line
 * manager ({@code INITIATOR_SUPERVISOR} level 1), then a holder of the payroll role who is not the initiator. The payroll consumer of
 * {@code ApprovalCompleted} writes the row (its own id; {@code approval_instance_id} is the link, the document id is payroll's request id and is no table
 * key); the void and the reads are the {@code BonusInputs} api of {@code com.forcitis.one.organization.payroll.api}. Types are looked up by name and called by
 * reflection, so the class compiles before the slice exists. ONE role holds submit, update (the void) and read; the record does not name its code:
 * {@code payroll_specialist} is used here as a placeholder the implementer confirms with lead (a rename is one constant). Not tested here, owed until the
 * Java signature of the {@code RequestBonusInput} command is ruled: its submit permission and the retry-reuses-the-instance line; the start below is the
 * record's own call of {@code ApprovalProcesses.start} (process key, document, legal entity of the request, initiator, five fields).
 */
abstract class BonusInputSupport extends P5Support {

    static final String API = "com.forcitis.one.organization.payroll.api.";
    static final String RELATIONSHIP_ID = "com.forcitis.one.organization.workforce.api.EmploymentRelationshipId";
    static final String PAYROLL_ROLE = "payroll_specialist";
    static final ProcessKey PROCESS = ProcessKey.of("organization_payroll_bonus_input");
    static final DocumentTypeCode DOCUMENT_TYPE = new DocumentTypeCode("BONUS_INPUT");
    static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);

    @Autowired
    ApplicationContext context;

    /** The initiator: holds the payroll role, has a line manager. */
    UUID specialist;
    /** The initiator's line manager: no payroll role, decides the first stage. */
    UUID lineManager;
    /** A second holder of the payroll role: decides the second stage. */
    UUID secondSpecialist;
    UUID outsider;
    SignedIn specialistAccount;
    SignedIn lineManagerAccount;
    SignedIn secondSpecialistAccount;
    SignedIn outsiderAccount;

    /** One bonus input sent to approval: {@code requestId} is the document id (payroll's own, not a table key), {@code instance} links the row. */
    record Submitted(UUID requestId, UUID relationship, ApprovalInstanceId instance, DocumentReference document) {
    }

    @BeforeEach
    void thePayrollPeople() throws Exception {
        lineManager = newEmployeeId();
        employee(lineManager, unit, null);
        lineManagerAccount = signIn(lineManager);
        specialist = newEmployeeId();
        employee(specialist, unit, lineManager);
        grantByOperations(specialist, PAYROLL_ROLE);
        specialistAccount = signIn(specialist);
        secondSpecialist = newEmployeeId();
        employee(secondSpecialist, unit, null);
        grantByOperations(secondSpecialist, PAYROLL_ROLE);
        secondSpecialistAccount = signIn(secondSpecialist);
        outsider = newEmployeeId();
        employee(outsider, unit, null);
        outsiderAccount = signIn(outsider);
    }

    // ---- the approval path ----

    Submitted submit(UUID relationship, LocalDate month, String amount, String reason) {
        UUID requestId = UUID.randomUUID();
        DocumentReference document = new DocumentReference(DOCUMENT_TYPE, documentIdOf(requestId));
        List<ApprovalField> fields = List.of(new ApprovalField("employmentRelationshipId", List.of(text(relationship))),
                new ApprovalField("payrollMonthStartDate", List.of(month.toString())), new ApprovalField("bonusAmount", List.of(amount)),
                new ApprovalField("bonusCurrency", List.of("CNY")), new ApprovalField("reasonKey", List.of(reason)));
        actInSession(specialistAccount);
        ApprovalInstanceId instance;
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "acceptance.bonus-input-request")) {
            instance = approvals.start(new ApprovalStartRequest(PROCESS, document, legalEntity, employeeIdOf(specialist), new RoundNumber(1), UUID.randomUUID(),
                    Set.of(), fields, new ContentHash("synthetic-bonus-" + requestId), new ApprovalSummary("Synthetic bonus input " + requestId)));
        }
        return new Submitted(requestId, relationship, instance, document);
    }

    /** The open task of the document once there is one that is not in {@code seen}. */
    UUID nextTask(Submitted submitted, List<UUID> seen) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> openTaskIds(submitted.document()).stream().anyMatch(task -> !seen.contains(task)));
        try {
            return openTaskIds(submitted.document()).stream().filter(task -> !seen.contains(task)).findFirst().orElseThrow();
        } catch (SQLException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    void claimAndDecide(UUID task, SignedIn who, String outcome) throws Exception {
        actOk(task, who, "claim", null);
        actOk(task, who, "decide", "{\"outcome\":\"" + outcome + "\",\"reason\":\"synthetic\"}");
    }

    /** The first stage: the line manager decides. Returns the task so a caller can look for the next one. */
    UUID decideFirstStage(Submitted submitted, String outcome) throws Exception {
        UUID task = nextTask(submitted, List.of());
        claimAndDecide(task, lineManagerAccount, outcome);
        return task;
    }

    /** Both stages: the line manager, then a payroll-role holder who is not the initiator. */
    void decide(Submitted submitted, String outcome) throws Exception {
        UUID first = decideFirstStage(submitted, outcome);
        if (outcome.equals("APPROVE")) {
            claimAndDecide(nextTask(submitted, List.of(first)), secondSpecialistAccount, outcome);
        }
    }

    /** Submitted, approved by both stages and the payroll consumer done with the result. */
    Submitted approved(UUID relationship, LocalDate month, String amount, String reason) throws Exception {
        Submitted submitted = submit(relationship, month, amount, reason);
        decide(submitted, "APPROVE");
        awaitConsumed(submitted.requestId());
        return submitted;
    }

    /** The result event of the document is published and no listener still owes it. */
    void awaitConsumed(UUID documentId) {
        String mention = "%" + documentId + "%";
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100)).until(() -> published(mention) && !pending(mention));
    }

    /** Publishes a second result event for the same approval instance, as a redelivery by another publisher would. */
    void replay(Submitted submitted, ApprovalOutcome outcome) {
        EventEnvelope envelope = (EventEnvelope) envelopeFor("platform.approval.ApprovalCompleted", "ApprovalRequest", UUID.randomUUID());
        ApprovalCompleted again = new ApprovalCompleted(envelope, submitted.document(), PROCESS, submitted.instance(), new RoundNumber(1), employeeIdOf(specialist),
                outcome, null, null);
        transaction.executeWithoutResult(status -> publisher.publishEvent(again));
        awaitConsumed(UUID.fromString(envelope.eventId().toString()));
    }

    private boolean published(String mention) throws SQLException {
        return !query("platform_integration", "select 1 from event_publication_archive where event_type like '%.ApprovalCompleted' and serialized_event like ? "
                + "union all select 1 from event_publication where event_type like '%.ApprovalCompleted' and serialized_event like ?", mention, mention).isEmpty();
    }

    private boolean pending(String mention) throws SQLException {
        return !query("platform_integration", "select 1 from event_publication where completion_date is null and serialized_event like ?", mention).isEmpty();
    }

    // ---- the rows ----

    /** The rows of one approval: found by {@code approval_instance_id}, the only link (the document id is no table key). */
    List<Map<String, Object>> rowsOf(Submitted submitted) throws SQLException {
        return query("organization_payroll", "select * from bonus_inputs where approval_instance_id = ?", submitted.instance().value());
    }

    /** The id of the one row of the approval (the row has its own id, not the document id). */
    UUID rowId(Submitted submitted) throws SQLException {
        List<Map<String, Object>> rows = rowsOf(submitted);
        assertThat(rows).as("the row of the approval %s", submitted.instance().value()).hasSize(1);
        return (UUID) rows.getFirst().get("id");
    }

    long activeRowsOf(UUID relationship, LocalDate month, String reason) throws SQLException {
        return (long) query("organization_payroll", "select count(*) as n from bonus_inputs where employment_relationship_id = ? and payroll_month_start_date = ? "
                + "and reason_key = ? and status = 'ACTIVE'", relationship, month, reason).getFirst().get("n");
    }

    // ---- the api, by name ----

    static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("the type " + name + " does not exist yet", missing);
        }
    }

    static Object newId(String simpleName, UUID value) {
        try {
            return type(API + simpleName).getDeclaredConstructor(UUID.class).newInstance(value);
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError("cannot build " + simpleName, unexpected);
        }
    }

    static Object relationshipId(UUID value) {
        try {
            return type(RELATIONSHIP_ID).getDeclaredConstructor(UUID.class).newInstance(value);
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError("cannot build EmploymentRelationshipId", unexpected);
        }
    }

    /** The api bean's method of that name, called as the signed-in user; a refusal is thrown as it is. */
    Object call(SignedIn caller, String method, Object... arguments) {
        actInSession(caller);
        Object api = context.getBean(type(API + "BonusInputs"));
        Method found = java.util.Arrays.stream(api.getClass().getMethods()).filter(m -> m.getName().equals(method) && m.getParameterCount() == arguments.length)
                .findFirst().orElseThrow(() -> new AssertionError("BonusInputs has no method " + method + "/" + arguments.length));
        try {
            return found.invoke(api, arguments);
        } catch (InvocationTargetException refused) {
            if (refused.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(refused.getCause());
        } catch (IllegalAccessException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    Object summary(SignedIn caller, UUID id) {
        return call(caller, "get", newId("BonusInputId", id));
    }

    void voidInput(SignedIn caller, UUID id) {
        try {
            call(caller, "voidInput", type(API + "VoidBonusInput").getDeclaredConstructor(type(API + "BonusInputId")).newInstance(newId("BonusInputId", id)));
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    @SuppressWarnings("unchecked")
    List<Object> list(SignedIn caller, UUID relationship, LocalDate from, LocalDate to) {
        return (List<Object>) call(caller, "listForRelationship", relationshipId(relationship), from, to);
    }

    /** A record component of a summary, ids unwrapped to their UUID. */
    static Object field(Object summary, String name) {
        try {
            Object value = summary.getClass().getMethod(name).invoke(summary);
            if (value != null && value.getClass().isRecord() && value.getClass().getRecordComponents().length == 1 && value.getClass().getName().endsWith("Id")) {
                return value.getClass().getMethod("value").invoke(value);
            }
            return value;
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError("a summary has no " + name, unexpected);
        }
    }

    static void assertRefusedWith(Runnable action, String code) {
        try {
            action.run();
        } catch (DomainException refused) {
            assertThat(refused.errorCode().name()).as("the refusal code").isEqualTo(code);
            return;
        }
        throw new AssertionError("expected the refusal " + code);
    }
}
