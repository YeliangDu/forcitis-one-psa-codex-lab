package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #730 S2: the two daily jobs of the permission adjustments on a migrated database, driven by the injected clock (ADR-0044 item 2 clause 3,
 * f-1.md invariants 24-27 and 30, one-design rulings of #730). Items are written straight into the table in the states the jobs find them in;
 * the jobs are the entries {@code ExpirePermissionAdjustments} and {@code EscalatePermissionAdjustments} of platform.identity, public
 * no-argument {@code execute()} (assumed names, see F1_730_S2_ACCEPTANCE.md); a missing entry fails the test.
 */
class PermissionAdjustmentJobsIT extends PermissionAdjustmentApiSupport {

    static final TestDatabase postgres = TestPostgres.database(PermissionAdjustmentJobsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    static final String EXPIRES = "2026-12-31T16:00:00Z";
    static final Instant EXPIRY = Instant.parse(EXPIRES);
    static final String EXPIRED_ACTION = "platform.identity.PermissionAdjustmentExpired";
    static final String OPENED_ACTION = "platform.identity.PermissionAdjustmentOpened";
    static final String ESCALATED_ACTION = "platform.identity.PermissionAdjustmentEscalated";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext jobs;

    @BeforeAll
    void organisation() throws Exception {
        apiWorld();
    }

    // ---- fixtures -----------------------------------------------------------------------------------------------------------

    /** An upheld sensitive role review about the person, valid until the given instant, as the uphold command leaves it. */
    private UUID upheldAbout(String key, String expiresAt) throws Exception {
        return insertItem(base("SENSITIVE_ROLE_REVIEW", "ASSIGNMENT_CHANGE").with("user_id", UUID.fromString(userId(key)))
                .with("trigger_personnel_command_id", UUID.randomUUID()).with("trigger_field_kind", "ORG_UNIT")
                .with("before_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-A"))).with("after_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-B")))
                .with("status", "UPHELD").with("upheld_at", "2026-09-30T02:00:00Z").with("upheld_by", UUID.fromString(userId("oda")))
                .with("uphold_reason", "still needed for the audit").with("business_owner_employee_id", UUID.fromString(employeeIdOf("owner")))
                .with("uphold_expires_at", expiresAt).with("updated_at", "2026-09-30T02:00:00Z"));
    }

    private UUID openDue(String due) throws Exception {
        return insertItem(base("SENSITIVE_ROLE_REVIEW", "ASSIGNMENT_CHANGE").with("user_id", UUID.fromString(userId("mover")))
                .with("trigger_personnel_command_id", UUID.randomUUID()).with("trigger_field_kind", "ORG_UNIT")
                .with("before_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-A"))).with("after_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-B")))
                .with("escalation_due_at", due));
    }

    private UUID openScopeReviewDue(String due) throws Exception {
        return insertItem(base("SCOPE_REVIEW", "ASSIGNMENT_CHANGE").with("user_id", UUID.fromString(userId("mover2")))
                .with("trigger_personnel_command_id", UUID.randomUUID()).with("trigger_field_kind", "ORG_UNIT")
                .with("before_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-A"))).with("after_org_unit_id", UUID.fromString(orgUnitId(session("oda"), "F1-B")))
                .with("recipient_employee_id", UUID.fromString(employeeIdOf("headB"))).with("escalation_due_at", due));
    }

    private List<Map<String, Object>> regeneratedFrom(UUID original) throws SQLException {
        List<Map<String, Object>> found = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity
                .prepareStatement("select permission_adjustment_id from permission_adjustments where original_permission_adjustment_id = ?")) {
            select.setObject(1, original);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    found.add(rowOf(rows.getObject(1, UUID.class)));
                }
            }
        }
        return found;
    }

    private void separate(String key) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement separated = workforce
                .prepareStatement("update employees set employment_status = 'SEPARATED', separated_at = ? where id = ?")) {
            separated.setObject(1, OffsetDateTime.parse(CREATED));
            separated.setObject(2, stored(UUID.fromString(employeeIdOf(key))));
            separated.executeUpdate();
        }
    }

    /** A HELD separation of the person, written straight into the workforce schema as the personnel command tests do; answers the hold id. */
    private UUID holdSeparationOf(String key) throws Exception {
        UUID command = UUID.randomUUID();
        UUID hold = UUID.randomUUID();
        Object employee = stored(UUID.fromString(employeeIdOf(key)));
        OffsetDateTime at = OffsetDateTime.parse(CREATED);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            try (PreparedStatement insert = workforce.prepareStatement("""
                    insert into personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)
                    values (?, ?, 'SEPARATE', ?, 'HELD', ?, ?, ?)""")) {
                insert.setObject(1, command);
                insert.setObject(2, employee);
                insert.setObject(3, java.time.LocalDate.of(2027, 1, 20));
                insert.setObject(4, at);
                insert.setObject(5, UUID.fromString(userId("admin1")));
                insert.setObject(6, at);
                insert.executeUpdate();
            }
            try (PreparedStatement insert = workforce.prepareStatement("""
                    insert into separation_holds (separation_hold_id, personnel_command_id, employee_id, status, created_at, updated_at)
                    values (?, ?, ?, 'HELD', ?, ?)""")) {
                insert.setObject(1, hold);
                insert.setObject(2, command);
                insert.setObject(3, employee);
                insert.setObject(4, at);
                insert.setObject(5, at);
                insert.executeUpdate();
            }
        }
        return hold;
    }

    private void release(UUID hold) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement released = workforce.prepareStatement("""
                update separation_holds set status = 'RELEASED', released_at = ?, release_reason = 'checked', release_channel_kind = 'OPERATIONS_COMMAND', updated_at = ?
                 where separation_hold_id = ?""")) {
            released.setObject(1, OffsetDateTime.parse(EXPIRES).plusDays(1));
            released.setObject(2, OffsetDateTime.parse(EXPIRES).plusDays(1));
            released.setObject(3, hold);
            released.executeUpdate();
        }
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement command = workforce
                .prepareStatement("update personnel_commands set status = 'PENDING' where personnel_command_id = (select personnel_command_id from separation_holds where separation_hold_id = ?)")) {
            command.setObject(1, hold);
            command.executeUpdate();
        }
    }

    /** The job entry of platform.identity by its assumed type name; found at run time so this file compiles before it exists, never skipped. */
    private void runJob(String typeName) {
        for (String name : jobs.getBeanDefinitionNames()) {
            Class<?> type = jobs.getType(name);
            if (type == null) {
                continue;
            }
            List<Class<?>> candidates = new ArrayList<>(org.springframework.util.ClassUtils.getAllInterfacesForClassAsSet(org.springframework.util.ClassUtils.getUserClass(type)));
            candidates.addFirst(org.springframework.util.ClassUtils.getUserClass(type));
            for (Class<?> candidate : candidates) {
                if (candidate.getSimpleName().equals(typeName) && candidate.getPackageName().startsWith("com.forcitis.one.platform.identity")) {
                    try {
                        candidate.getMethod("execute").invoke(jobs.getBean(name));
                        return;
                    } catch (java.lang.reflect.InvocationTargetException failure) {
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

    // ---- expiry -------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("#730 S2 ADR-0044 第 2 项第 3 条（固定时钟）：时钟在有效至前一秒时作业不转换，恰在有效至时原条目转已到期，到期时刻为作业用的时钟时刻，此人的角色不变（不收回）")
    void anUpheldItemExpiresExactlyAtItsExpiryAndGrantsStay() throws Exception {
        UUID item = upheldAbout("mover", EXPIRES);
        List<String> rolesBefore = roleCodesOf(administrator("admin1"), "mover");

        advanceTo(EXPIRY.minusSeconds(1));
        runJob("ExpirePermissionAdjustments");
        assertThat(rowOf(item)).as("one second before the expiry").containsEntry("status", "UPHELD");
        assertThat(regeneratedFrom(item)).isEmpty();

        advanceTo(EXPIRY);
        runJob("ExpirePermissionAdjustments");

        Map<String, Object> row = rowOf(item);
        assertThat(row).containsEntry("status", "EXPIRED").containsEntry("expired_at", EXPIRES).containsEntry("is_held_at_expiry", false);
        assertThat(row.get("regeneration_decided_at")).as("not held: decided at once").isNotNull();
        assertThat(roleCodesOf(administrator("admin1"), "mover")).as("expiry changes no grant").containsExactlyInAnyOrderElementsOf(rolesBefore);
    }

    @Test
    @DisplayName("#730 S2 ADR-0044 第 2 项第 3 条：读取不转换——有效至已过而作业未跑时详情仍是已维持并带有效至；跑了作业才转已到期")
    void readingDoesNotExpireAnItem() throws Exception {
        UUID item = upheldAbout("mover2", EXPIRES);

        advanceTo(EXPIRY.plusSeconds(3600));
        MockHttpServletResponse read = read(session("oda"), item);

        assertThat(read.getStatus()).isEqualTo(200);
        assertThat(read.getContentAsString()).contains("\"status\":\"UPHELD\"");
        assertThat(rowOf(item)).containsEntry("status", "UPHELD");
        runJob("ExpirePermissionAdjustments");
        assertThat(rowOf(item)).containsEntry("status", "EXPIRED");
    }

    @Test
    @DisplayName("#730 S2 ADR-0044 第 2 项第 3 条与不变量 16：此人仍持有敏感角色、在职、未被暂停时，到期生成恰好一条新的待处理敏感角色复核（来源 UPHOLD_EXPIRY、引用原条目、同一个人、新升级期限在作业时刻之后）；作业重跑多次不重复生成")
    void anExpiryRegeneratesOneItemAndRerunsDoNotAddMore() throws Exception {
        UUID item = upheldAbout("mover", EXPIRES);
        advanceTo(EXPIRY);

        runJob("ExpirePermissionAdjustments");
        runJob("ExpirePermissionAdjustments");
        advanceTo(EXPIRY.plusSeconds(86400));
        runJob("ExpirePermissionAdjustments");

        List<Map<String, Object>> regenerated = regeneratedFrom(item);
        assertThat(regenerated).as("exactly one regenerated item").hasSize(1);
        Map<String, Object> next = regenerated.getFirst();
        assertThat(next).containsEntry("kind", "SENSITIVE_ROLE_REVIEW").containsEntry("origin_kind", "UPHOLD_EXPIRY").containsEntry("status", "OPEN")
                .containsEntry("original_permission_adjustment_id", item);
        assertThat(next.get("user_id").toString()).isEqualTo(userId("mover"));
        assertThat(Instant.parse((String) next.get("escalation_due_at"))).as("a regenerated item has its own escalation due date").isAfter(EXPIRY);
        assertThat(Instant.parse((String) next.get("created_at"))).isEqualTo(EXPIRY);
    }

    @Test
    @DisplayName("#730 S2 ADR-0044 第 2 项第 3 条第 4 点：此人不再持有任何敏感角色，或已离职，到期只转已到期、不生成新条目，且已判定（regeneration_decided_at 有值）")
    void noRegenerationForAPersonWithoutASensitiveRoleOrWhoLeft() throws Exception {
        UUID noRole = upheldAbout("plain", EXPIRES);
        UUID leaver = upheldAbout("leaver", EXPIRES);
        separate("leaver");

        advanceTo(EXPIRY);
        runJob("ExpirePermissionAdjustments");

        for (UUID item : List.of(noRole, leaver)) {
            assertThat(rowOf(item)).containsEntry("status", "EXPIRED").containsEntry("is_held_at_expiry", false);
            assertThat(rowOf(item).get("regeneration_decided_at")).isNotNull();
            assertThat(regeneratedFrom(item)).isEmpty();
        }
    }

    @Test
    @DisplayName("#730 S2 ADR-0044 第 2 项第 3 条与不变量 26：同步暂停（HELD）期间到期的，转已到期并记下到期时在暂停、尚未判定、不生成；暂停期间再跑作业仍不生成；放行后的下一次作业补生成恰好一条并记下判定时刻；再跑不重复")
    void aHeldPersonGetsTheRegenerationAfterTheRelease() throws Exception {
        UUID item = upheldAbout("held", EXPIRES);
        UUID hold = holdSeparationOf("held");

        advanceTo(EXPIRY);
        runJob("ExpirePermissionAdjustments");
        Map<String, Object> expired = rowOf(item);
        assertThat(expired).containsEntry("status", "EXPIRED").containsEntry("is_held_at_expiry", true);
        assertThat(expired.get("regeneration_decided_at")).as("waits for the release").isNull();
        assertThat(regeneratedFrom(item)).isEmpty();

        advanceTo(EXPIRY.plusSeconds(3600));
        runJob("ExpirePermissionAdjustments");
        assertThat(regeneratedFrom(item)).as("still held").isEmpty();
        assertThat(rowOf(item).get("regeneration_decided_at")).isNull();

        release(hold);
        advanceTo(EXPIRY.plusSeconds(2 * 86400));
        runJob("ExpirePermissionAdjustments");
        runJob("ExpirePermissionAdjustments");

        assertThat(regeneratedFrom(item)).as("generated once after the release").hasSize(1);
        assertThat(rowOf(item).get("regeneration_decided_at")).isNotNull();
    }

    @Test
    @DisplayName("#730 S2 审计（one-design 裁定 6602101721）：到期写 PermissionAdjustmentExpired（对象为原条目），再生成写 PermissionAdjustmentOpened（对象为新条目）；到期时刻与升级无关的条目不被误转")
    void expiryAndRegenerationAreAudited() throws Exception {
        UUID item = upheldAbout("mover", EXPIRES);
        UUID notYet = upheldAbout("mover2", "2027-03-31T16:00:00Z");
        advanceTo(EXPIRY);

        runJob("ExpirePermissionAdjustments");

        UUID next = (UUID) regeneratedFrom(item).getFirst().get("permission_adjustment_id");
        eventually(() -> {
            assertThat(auditEntries(EXPIRED_ACTION)).anySatisfy(entry -> assertThat(entry).containsEntry("subjectKind", "PERMISSION_ADJUSTMENT")
                    .containsEntry("subjectId", item.toString()));
            assertThat(auditEntries(OPENED_ACTION)).anySatisfy(entry -> assertThat(entry).containsEntry("subjectId", next.toString()));
        });
        assertThat(rowOf(notYet)).containsEntry("status", "UPHELD");
    }

    // ---- escalation ---------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("#730 S2 不变量 30、RULE-394：升级期限前一秒作业不升级；恰在期限时待处理条目（敏感角色复核与范围复核）升级，escalated_at 为作业时钟时刻、状态仍待处理；未到期限、已维持的条目不升级；再跑不改 escalated_at（只升级一次）；此人的角色不变（不收回）")
    void anOverdueOpenItemIsEscalatedOnceAndNothingIsRevoked() throws Exception {
        String due = "2026-11-05T10:00:00Z";
        Instant dueAt = Instant.parse(due);
        UUID sensitive = openDue(due);
        UUID scope = openScopeReviewDue(due);
        UUID later = openDue("2026-11-20T10:00:00Z");
        UUID upheld = upheldAbout("mover", EXPIRES);
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement overdue = identity
                .prepareStatement("update permission_adjustments set escalation_due_at = ?::timestamptz where permission_adjustment_id = ?")) {
            overdue.setString(1, due);
            overdue.setObject(2, upheld);
            overdue.executeUpdate();
        }
        List<String> rolesBefore = roleCodesOf(administrator("admin1"), "mover");

        advanceTo(dueAt.minusSeconds(1));
        runJob("EscalatePermissionAdjustments");
        assertThat(rowOf(sensitive).get("escalated_at")).as("one second before the due time").isNull();

        advanceTo(dueAt);
        runJob("EscalatePermissionAdjustments");

        for (UUID item : List.of(sensitive, scope)) {
            assertThat(rowOf(item)).containsEntry("status", "OPEN").containsEntry("escalated_at", due);
        }
        assertThat(rowOf(later).get("escalated_at")).as("not yet due").isNull();
        assertThat(rowOf(upheld).get("escalated_at")).as("only open items escalate").isNull();
        assertThat(roleCodesOf(administrator("admin1"), "mover")).containsExactlyInAnyOrderElementsOf(rolesBefore);

        advanceTo(dueAt.plusSeconds(86400));
        runJob("EscalatePermissionAdjustments");
        assertThat(rowOf(sensitive)).as("escalated once, never moved").containsEntry("escalated_at", due);
        eventually(() -> assertThat(auditEntries(ESCALATED_ACTION).stream().filter(entry -> sensitive.toString().equals(String.valueOf(entry.get("subjectId")))).toList())
                .as("one escalation audit entry for the item").hasSize(1));
    }
}
