package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubjectKind;
import com.forcitis.one.platform.integration.api.WecomCard;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

/**
 * #1151 (escalation notice of the overdue permission adjustments): what the tests of the notice share. The WeCom gateway is the double of the
 * acceptance ground; its contract says {@code send} is called inside the caller's transaction and the card goes out only after commit. So the
 * double files a card as <em>delivered</em> only when the transaction that called it commits, and a call with no transaction is filed apart.
 * The new {@link DeliveryPurpose} has no name in the card; it is found as the one purpose besides PERMISSION_ADJUSTMENT_ASSIGNED that is about
 * a PERMISSION_ADJUSTMENT, and a missing or second one fails the test. The name must not be PERMISSION_ADJUSTMENT_ESCALATED: the older
 * OutboundDeliveryPermissionAdjustmentRowRulesIT asserts that value is refused and implementers may not edit it.
 */
abstract class PermissionAdjustmentEscalationSupport extends PermissionAdjustmentApiSupport {

    /** After the due time of every item written by {@link PermissionAdjustmentApiSupport} (DUE, 2026-09-08T02:00Z). */
    static final Instant RUN_AT = Instant.parse("2026-09-09T00:00:00Z");
    static final String NOT_YET_DUE = "2026-09-10T00:00:00Z";
    private static final String JOB = "EscalatePermissionAdjustments";

    private final List<WecomCard> delivered = new CopyOnWriteArrayList<>();
    private final List<WecomCard> sentOutsideATransaction = new CopyOnWriteArrayList<>();

    @Autowired
    private ApplicationContext jobs;

    @BeforeEach
    void recordWhatTheGatewayDelivers() {
        delivered.clear();
        sentOutsideATransaction.clear();
        doAnswer(call -> {
            WecomCard card = call.getArgument(0);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            delivered.add(card);
                        }
                    }
                });
            } else {
                sentOutsideATransaction.add(card);
            }
            return null;
        }).when(wecomGateway).send(any());
    }

    // ---- the new purpose and the cards ----------------------------------------------------------------------------------------

    static DeliveryPurpose escalationPurpose() {
        List<DeliveryPurpose> found = Arrays.stream(DeliveryPurpose.values())
                .filter(purpose -> purpose.subjectKind() == DeliverySubjectKind.PERMISSION_ADJUSTMENT && purpose != DeliveryPurpose.PERMISSION_ADJUSTMENT_ASSIGNED).toList();
        assertThat(found).as("#1151 adds exactly one DeliveryPurpose about a PERMISSION_ADJUSTMENT besides PERMISSION_ADJUSTMENT_ASSIGNED").hasSize(1);
        return found.getFirst();
    }

    /** Cards delivered (their transaction committed) about the item, whatever their purpose. */
    protected List<WecomCard> deliveredAbout(UUID item) {
        return delivered.stream().filter(card -> card.subject().id().equals(item)).toList();
    }

    /** The one escalation card of the item; fails if there is none or more than one. */
    protected WecomCard escalationCardOf(UUID item) {
        List<WecomCard> cards = deliveredAbout(item);
        assertThat(cards).as("cards delivered about " + item).hasSize(1);
        WecomCard card = cards.getFirst();
        assertThat(card.subject().kind()).isEqualTo(DeliverySubjectKind.PERMISSION_ADJUSTMENT);
        assertThat(card.subject().purpose()).isEqualTo(escalationPurpose());
        return card;
    }

    protected static Set<String> recipientsOf(WecomCard card) {
        return card.wecomUserIds().stream().map(id -> id.value()).collect(Collectors.toSet());
    }

    protected static Set<String> wecomIdsOf(String... keys) {
        return Arrays.stream(keys).map(key -> wecomUserIdOf(key)).collect(Collectors.toSet());
    }

    /** ADR-0032 section 4 item 3, content minimisation (user decision Q4): the person may be named; no permission code, no scope, no employee code. */
    protected void assertMinimised(WecomCard card, String... subjectKeys) {
        String text = card.title() + "\n" + (card.description() == null ? "" : card.description());
        assertThat(text).as("card text").doesNotContain("organization.workforce:", "platform.identity:", "platform.", "CUSTOM_ORG_UNITS", "OWN_ORG_UNIT", "accessScope");
        for (String key : subjectKeys) {
            assertThat(text).as("the employee code of " + key + " is not written to the card").doesNotContain(employeeCode(key));
        }
    }

    protected void assertNothingSentOutsideATransaction() {
        assertThat(sentOutsideATransaction).as("the gateway contract: send is called inside the caller's transaction").isEmpty();
    }

    // ---- the job ------------------------------------------------------------------------------------------------------------------

    /** Runs the daily escalation job once at {@link #RUN_AT}; the entry is found by its type name at run time. */
    protected void escalate() {
        advanceTo(RUN_AT);
        runJob(JOB);
    }

    protected void runJob(String typeName) {
        for (String name : jobs.getBeanDefinitionNames()) {
            Class<?> type = jobs.getType(name);
            if (type == null) {
                continue;
            }
            List<Class<?>> candidates = new ArrayList<>(ClassUtils.getAllInterfacesForClassAsSet(ClassUtils.getUserClass(type)));
            candidates.addFirst(ClassUtils.getUserClass(type));
            for (Class<?> candidate : candidates) {
                if (candidate.getSimpleName().equals(typeName) && candidate.getPackageName().startsWith("com.forcitis.one.platform.identity")) {
                    try {
                        candidate.getMethod("execute").invoke(jobs.getBean(name));
                        return;
                    } catch (InvocationTargetException failure) {
                        if (failure.getCause() instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        throw new IllegalStateException(failure.getCause());
                    } catch (ReflectiveOperationException missing) {
                        throw new AssertionError(typeName + " has no public execute() without arguments", missing);
                    }
                }
            }
        }
        throw new AssertionError("no bean of a type named " + typeName + " in platform.identity (the job entry)");
    }

    // ---- items --------------------------------------------------------------------------------------------------------------------

    protected void dueAt(UUID item, String due) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement update = identity
                .prepareStatement("update permission_adjustments set escalation_due_at = ?::timestamptz where permission_adjustment_id = ?")) {
            update.setString(1, due);
            update.setObject(2, item);
            update.executeUpdate();
        }
    }

    protected void alreadyEscalatedAt(UUID item, Instant at) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement update = identity
                .prepareStatement("update permission_adjustments set escalated_at = ? where permission_adjustment_id = ?")) {
            update.setObject(1, OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
            update.setObject(2, item);
            update.executeUpdate();
        }
    }

    /** An open item turned into an UPHELD, EXPIRED or ADJUSTED one with the columns the card of F-1 gives each state; the due time stays in the past. */
    protected void inState(UUID item, String status) throws Exception {
        OffsetDateTime at = OffsetDateTime.parse("2026-09-02T02:00:00Z");
        String sql = switch (status) {
            case "UPHELD" -> "update permission_adjustments set status = 'UPHELD', upheld_at = ?, upheld_by = ?, uphold_reason = 'still needed for the audit',"
                    + " business_owner_employee_id = ?, uphold_expires_at = ?, updated_at = ? where permission_adjustment_id = ?";
            case "EXPIRED" -> "update permission_adjustments set status = 'EXPIRED', upheld_at = ?, upheld_by = ?, uphold_reason = 'still needed for the audit',"
                    + " business_owner_employee_id = ?, uphold_expires_at = ?, expired_at = ?, is_held_at_expiry = false, regeneration_decided_at = ?,"
                    + " updated_at = ? where permission_adjustment_id = ?";
            case "ADJUSTED" -> "update permission_adjustments set status = 'ADJUSTED', adjusted_at = ?, adjusted_by = ?, adjusted_audit_entry_id = ?, updated_at = ?"
                    + " where permission_adjustment_id = ?";
            default -> throw new IllegalArgumentException(status);
        };
        List<Object> values = switch (status) {
            case "UPHELD" -> List.of(at, UUID.fromString(userId("oda")), UUID.fromString(employeeIdOf("owner")), at.plusDays(30), at, item);
            case "EXPIRED" -> List.of(at, UUID.fromString(userId("oda")), UUID.fromString(employeeIdOf("owner")), at.plusDays(3), at.plusDays(3), at.plusDays(3),
                    at.plusDays(3), item);
            default -> List.of(at, UUID.fromString(userId("oda")), UUID.randomUUID(), at, item);
        };
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement update = identity.prepareStatement(sql)) {
            for (int position = 0; position < values.size(); position++) {
                update.setObject(position + 1, values.get(position));
            }
            update.executeUpdate();
        }
    }

    protected void assertEscalatedAtRunTime(UUID item) throws SQLException {
        assertThat(rowOf(item)).containsEntry("status", "OPEN").containsEntry("escalated_at", RUN_AT.toString());
    }

    protected void assertNotEscalated(UUID item) throws SQLException {
        assertThat(rowOf(item).get("escalated_at")).as("not escalated").isNull();
    }
}
