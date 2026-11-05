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
 * #1436 acceptance part (PY3 bonus input port), the black-box contract ruled by lead on #1446: a bonus input enters through a real approval of the
 * process {@code payroll_bonus_input_approval} (document type BONUS_INPUT, document id = the new bonus input id, one step, form fields below), and the
 * payroll consumer of {@code ApprovalCompleted} writes the row; the void and the reads are the {@code BonusInputs} api of
 * {@code com.forcitis.one.organization.payroll.api}. The payroll types are looked up by name and called by reflection, so the class compiles before
 * the slice exists. Roles: payroll_specialist holds request, void and read; payroll_approver holds approve only.
 */
abstract class BonusInputSupport extends P5Support {

    static final String API = "com.forcitis.one.organization.payroll.api.";
    static final String RELATIONSHIP_ID = "com.forcitis.one.organization.workforce.api.EmploymentRelationshipId";
    static final ProcessKey PROCESS = ProcessKey.of("payroll_bonus_input_approval");
    static final DocumentTypeCode DOCUMENT_TYPE = new DocumentTypeCode("BONUS_INPUT");
    static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);

    @Autowired
    ApplicationContext context;

    UUID specialist;
    UUID approver;
    UUID outsider;
    SignedIn specialistAccount;
    SignedIn approverAccount;
    SignedIn outsiderAccount;

    /** One bonus input sent to approval: its id is the document id. */
    record Submitted(UUID id, UUID relationship, ApprovalInstanceId instance, DocumentReference document) {
    }

    @BeforeEach
    void thePayrollPeople() throws Exception {
        specialist = newEmployeeId();
        employee(specialist, unit, null);
        grantByOperations(specialist, "payroll_specialist");
        specialistAccount = signIn(specialist);
        approver = newEmployeeId();
        employee(approver, unit, null);
        grantByOperations(approver, "payroll_approver");
        approverAccount = signIn(approver);
        outsider = newEmployeeId();
        employee(outsider, unit, null);
        outsiderAccount = signIn(outsider);
    }

    // ---- the approval path ----

    Submitted submit(UUID relationship, LocalDate month, String amount, String reason) {
        UUID id = UUID.randomUUID();
        DocumentReference document = new DocumentReference(DOCUMENT_TYPE, documentIdOf(id));
        List<ApprovalField> fields = List.of(new ApprovalField("employmentRelationshipId", List.of(text(relationship))),
                new ApprovalField("payrollMonthStartDate", List.of(month.toString())), new ApprovalField("payingLegalEntityId", List.of(text(legalEntity.value()))),
                new ApprovalField("bonusAmount", List.of(amount)), new ApprovalField("bonusCurrency", List.of("CNY")), new ApprovalField("reasonKey", List.of(reason)));
        actInSession(specialistAccount);
        ApprovalInstanceId instance;
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "acceptance.bonus-input-request")) {
            instance = approvals.start(new ApprovalStartRequest(PROCESS, document, legalEntity, employeeIdOf(specialist), new RoundNumber(1), UUID.randomUUID(),
                    Set.of(), fields, new ContentHash("synthetic-bonus-" + id), new ApprovalSummary("Synthetic bonus input " + id)));
        }
        return new Submitted(id, relationship, instance, document);
    }

    void decide(Submitted submitted, String outcome) throws Exception {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> !openTaskIds(submitted.document()).isEmpty());
        UUID task = openTaskIds(submitted.document()).getFirst();
        actOk(task, approverAccount, "claim", null);
        actOk(task, approverAccount, "decide", "{\"outcome\":\"" + outcome + "\",\"reason\":\"synthetic\"}");
    }

    /** Submitted, approved and the payroll consumer done with the result. */
    Submitted approved(UUID relationship, LocalDate month, String amount, String reason) throws Exception {
        Submitted submitted = submit(relationship, month, amount, reason);
        decide(submitted, "APPROVE");
        awaitConsumed(submitted.id());
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

    List<Map<String, Object>> rowsOf(UUID id) throws SQLException {
        return query("organization_payroll", "select * from bonus_inputs where id = ?", id);
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
