package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.kernel.WorkContext;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ADR-0033 section 2 item 1: the table of source references and its constraints, probed in the database. Every probe row is a copy of a
 * row the service wrote (so the probes need no knowledge of audit columns), with a few columns overridden; all probes run in one
 * transaction that is rolled back.
 */
class SourceReferenceConstraintIT extends AcceptanceSupport {

    static final String UNIQUE_VIOLATION = "23505";
    static final String EXCLUSION_VIOLATION = "23P01";
    static final String CHECK_VIOLATION = "23514";
    static final String ACCEPTED = "accepted";

    static final UUID LEGAL_ENTITY_A = UUID.fromString("01900000-0000-7000-8000-0000000000e1");
    static final UUID LEGAL_ENTITY_B = UUID.fromString("01900000-0000-7000-8000-0000000000e2");

    static final TestDatabase postgres = TestPostgres.database(SourceReferenceConstraintIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ApplicationContext context;

    private Connection integration;
    private UUID template;
    private int probes;
    private final java.util.List<String> confirmationColumns = new java.util.ArrayList<>();

    @BeforeEach
    void oneRowWrittenByTheService() throws Exception {
        SourceReferencesProbe references = SourceReferencesProbe.of(context);
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
            references.allocate("ORG_SOURCE", "EMPLOYEE", -8_000_001L);
        }
        integration = schemaConnection("platform_integration");
        try (PreparedStatement select = integration.prepareStatement(
                "select source_reference_id from source_references where source_system = 'ORG_SOURCE' and source_object_kind = 'EMPLOYEE' and source_id = ?")) {
            select.setLong(1, -8_000_001L);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).as("the allocated reference is a row of source_references").isTrue();
                template = rs.getObject(1, UUID.class);
            }
        }
        try (PreparedStatement columns = integration.prepareStatement("""
                select column_name from information_schema.columns
                 where table_schema = current_schema() and table_name = 'source_references' and column_name like 'confirmed%'
                """); ResultSet rs = columns.executeQuery()) {
            while (rs.next()) {
                confirmationColumns.add(rs.getString(1));
            }
        }
        integration.setAutoCommit(false);
    }

    @AfterEach
    void rollBackEveryProbe() throws Exception {
        if (integration != null) {
            if (!integration.getAutoCommit()) {
                integration.rollback();
            }
            integration.close();
        }
    }

    private UUID nextProbeId() {
        return UUID.fromString(String.format("01900000-0000-7000-9000-%012x", ++probes));
    }

    /** A user id of whatever type the audit columns have (bigint now, uuid once user accounts move to UUIDv7), as a JSON value. */
    private Object userId(int n) throws SQLException {
        try (PreparedStatement type = integration.prepareStatement("""
                select data_type from information_schema.columns
                 where table_schema = current_schema() and table_name = 'source_references' and column_name = 'created_by'
                """); ResultSet rs = type.executeQuery()) {
            assertThat(rs.next()).as("source_references.created_by").isTrue();
            return "uuid".equals(rs.getString(1)) ? String.format("01900000-0000-7000-8000-%012x", 0xd0 + n) : (Object) (70_000L + n);
        }
    }

    /** An accounting organization code is always registered by hand: it has an author, and a decider once it is decided. */
    private Map<String, Object> accountingCode(long code, UUID legalEntity, String status, String from, String to) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("created_by", userId(1));
        if ("CONFIRMED".equals(status) || "REJECTED".equals(status)) {
            row.put("decided_by", userId(2));
            row.put("decided_at", "2026-11-01T00:00:00Z");
        }
        row.put("source_system", "ACCOUNTING_SYSTEM");
        row.put("source_object_kind", "LEGAL_ENTITY");
        row.put("source_id", code);
        row.put("own_id", legalEntity.toString());
        row.put("confirmation_status", status);
        row.put("effective_from_date", from);
        row.put("effective_to_date", to);
        return row;
    }

    private static String json(Map<String, Object> overrides) {
        StringBuilder out = new StringBuilder("{");
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            if (out.length() > 1) {
                out.append(',');
            }
            Object value = entry.getValue();
            out.append('"').append(entry.getKey()).append("\":").append(value == null ? "null" : value instanceof Number ? value.toString() : "\"" + value + "\"");
        }
        return out.append('}').toString();
    }

    /** Inserts a copy of the template with the overrides; keeps it when keep is true, otherwise rolls it back. Answers ACCEPTED or the SQL state. */
    private String insert(Map<String, Object> overrides, boolean keep) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(overrides);
        row.put("source_reference_id", nextProbeId().toString());
        if (row.containsKey("confirmation_status") && !"CONFIRMED".equals(row.get("confirmation_status"))) {
            confirmationColumns.forEach(column -> row.putIfAbsent(column, null));
        }
        Savepoint before = integration.setSavepoint();
        try (PreparedStatement insert = integration.prepareStatement("""
                insert into source_references
                select (jsonb_populate_record(null::source_references, to_jsonb(t) || cast(? as jsonb))).*
                  from source_references t where t.source_reference_id = ?
                """)) {
            insert.setString(1, json(row));
            insert.setObject(2, template);
            int inserted = insert.executeUpdate();
            if (inserted != 1) {
                throw new AssertionError("the template row is gone");
            }
            if (!keep) {
                integration.rollback(before);
            }
            return ACCEPTED;
        } catch (SQLException refused) {
            integration.rollback(before);
            return refused.getSQLState();
        }
    }

    private String probe(Map<String, Object> overrides) throws SQLException {
        return insert(overrides, false);
    }

    private String update(String assignment) throws SQLException {
        return update(template, assignment);
    }

    private String update(UUID row, String assignment) throws SQLException {
        return statement("update source_references set " + assignment + " where source_reference_id = ?", row);
    }

    /** Runs one statement in a savepoint that is always rolled back; answers ACCEPTED or the SQL state. A row-scoped statement must touch its row. */
    private String statement(String sql, UUID row) throws SQLException {
        Savepoint before = integration.setSavepoint();
        try (PreparedStatement statement = integration.prepareStatement(sql)) {
            if (row != null) {
                statement.setObject(1, row);
            }
            int touched = statement.executeUpdate();
            integration.rollback(before);
            if (row != null && touched != 1) {
                throw new AssertionError("the statement touched " + touched + " rows instead of the one probed: " + sql);
            }
            return ACCEPTED;
        } catch (SQLException refused) {
            integration.rollback(before);
            return refused.getSQLState();
        }
    }

    private boolean present(UUID row) throws SQLException {
        try (PreparedStatement select = integration.prepareStatement("select 1 from source_references where source_reference_id = ?")) {
            select.setObject(1, row);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** A user id literal of whatever type the audit columns have (bigint now, uuid once user accounts move to UUIDv7). */
    private String someOtherUser() throws SQLException {
        try (PreparedStatement type = integration.prepareStatement("""
                select data_type from information_schema.columns
                 where table_schema = current_schema() and table_name = 'source_references' and column_name = 'decided_by'
                """); ResultSet rs = type.executeQuery()) {
            assertThat(rs.next()).as("source_references.decided_by").isTrue();
            return "uuid".equals(rs.getString(1)) ? "cast('01900000-0000-7000-8000-0000000000c9' as uuid)" : "70099";
        }
    }

    /** An accounting organization code registered by hand through HTTP: PENDING, with its author, not yet decided. */
    private UUID manualRegistration(String legalEntityCode, long code) throws Exception {
        UUID legalEntity = createLegalEntity(legalEntityCode, "Synthetic " + legalEntityCode);
        MockHttpServletResponse created = mvc.perform(post("/api/v1/platform/source-references").with(csrf())
                .with(person(31, "platform.integration:source-reference:update")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceSystem\":\"ACCOUNTING_SYSTEM\",\"sourceObjectKind\":\"LEGAL_ENTITY\",\"sourceId\":\"" + code + "\",\"ownId\":\""
                        + legalEntity + "\",\"effectiveFrom\":\"" + TODAY + "\"}")).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        UUID row = UUID.fromString(JsonPath.read(created.getContentAsString(), "$.sourceReferenceId"));
        try (PreparedStatement select = integration.prepareStatement(
                "select confirmation_status, created_by is not null, decided_by is null from source_references where source_reference_id = ?")) {
            select.setObject(1, row);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1) + " " + rs.getBoolean(2) + " " + rs.getBoolean(3)).as("pending, with an author, undecided")
                        .isEqualTo("PENDING true true");
            }
        }
        return row;
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条：来源对照表的列与类型——UUIDv7 代理主键、来源 ID 为有符号 bigint、自有 ID 为 uuid、确认状态与生效区间两列")
    void columnsAndTypes() throws Exception {
        Map<String, String> types = new LinkedHashMap<>();
        try (PreparedStatement columns = integration.prepareStatement(
                "select column_name, data_type from information_schema.columns where table_schema = current_schema() and table_name = 'source_references'");
             ResultSet rs = columns.executeQuery()) {
            while (rs.next()) {
                types.put(rs.getString(1), rs.getString(2));
            }
        }
        assertThat(types).containsEntry("source_reference_id", "uuid").containsEntry("source_system", "text").containsEntry("source_object_kind", "text")
                .containsEntry("source_id", "bigint").containsEntry("own_id", "uuid").containsEntry("confirmation_status", "text")
                .containsEntry("effective_from_date", "date").containsEntry("effective_to_date", "date");
        try (PreparedStatement key = integration.prepareStatement("""
                select a.attname from pg_index i join pg_attribute a on a.attrelid = i.indrelid and a.attnum = any (i.indkey)
                 where i.indrelid = cast('source_references' as regclass) and i.indisprimary
                """); ResultSet rs = key.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("source_reference_id");
            assertThat(rs.next()).as("a single-column primary key").isFalse();
        }
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条：来源系统、来源对象类别、确认状态的取值由 CHECK 约束；（核算系统, LEGAL_ENTITY）的核算组织编码须 > 0，其他类别允许负数")
    void valueChecks() throws Exception {
        assertThat(probe(accountingCode(0, LEGAL_ENTITY_A, "PENDING", "2026-11-02", null))).as("accounting organization code 0").isEqualTo(CHECK_VIOLATION);
        assertThat(probe(accountingCode(-1, LEGAL_ENTITY_A, "PENDING", "2026-11-02", null))).as("accounting organization code -1").isEqualTo(CHECK_VIOLATION);
        assertThat(probe(accountingCode(1021, LEGAL_ENTITY_A, "PENDING", "2026-11-02", null))).as("accounting organization code 1021").isEqualTo(ACCEPTED);

        Map<String, Object> unknownSystem = accountingCode(1021, LEGAL_ENTITY_A, "PENDING", "2026-11-02", null);
        unknownSystem.put("source_system", "BOGUS");
        assertThat(probe(unknownSystem)).isEqualTo(CHECK_VIOLATION);
        Map<String, Object> unknownKind = accountingCode(1021, LEGAL_ENTITY_A, "PENDING", "2026-11-02", null);
        unknownKind.put("source_object_kind", "BOGUS");
        assertThat(probe(unknownKind)).isEqualTo(CHECK_VIOLATION);
        assertThat(probe(accountingCode(1021, LEGAL_ENTITY_A, "MAYBE", "2026-11-02", null))).isEqualTo(CHECK_VIOLATION);

        Map<String, Object> negativePerson = new LinkedHashMap<>();
        negativePerson.put("source_id", Long.MIN_VALUE);
        negativePerson.put("own_id", "01900000-0000-7000-8000-0000000000e9");
        assertThat(probe(negativePerson)).as("an organisation-source person id may be any signed 64-bit number").isEqualTo(ACCEPTED);
    }

    @Test
    @DisplayName("R-1b P3a 复审 M-1（ADR-0033 第 5 项第 4 条）：核算系统的法人编码只由人工登记——录入人为空（伪装成来源同步写入）的核算行，库内直接写入已确认也被拒绝")
    void accountingCodeCannotPassAsASynchronisedRow() throws Exception {
        Map<String, Object> disguised = accountingCode(1021, LEGAL_ENTITY_A, "CONFIRMED", "2026-11-02", null);
        disguised.put("created_by", null);
        disguised.put("decided_by", null);
        disguised.put("decided_at", null);
        assertThat(probe(disguised)).as("a confirmed accounting code with no author, as a synchronisation would write it").isEqualTo(CHECK_VIOLATION);

        assertThat(probe(accountingCode(1021, LEGAL_ENTITY_A, "CONFIRMED", "2026-11-02", null)))
                .as("the same row with its author and decider named").isEqualTo(ACCEPTED);
    }

    // RULE-468
    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条：已确认行的两个自然键在任一时点各自唯一；区间不重叠的已确认行可以并存")
    void confirmedRowsAreUniqueAtEveryPointInTime() throws Exception {
        assertThat(insert(accountingCode(1021, LEGAL_ENTITY_A, "CONFIRMED", "2026-11-02", null), true)).isEqualTo(ACCEPTED);

        assertThat(probe(accountingCode(1021, LEGAL_ENTITY_B, "CONFIRMED", "2026-11-20", null)))
                .as("the same accounting code confirmed for a second legal entity while the first is in effect").isIn(EXCLUSION_VIOLATION, UNIQUE_VIOLATION);
        assertThat(probe(accountingCode(1099, LEGAL_ENTITY_A, "CONFIRMED", "2026-11-20", null)))
                .as("a second confirmed accounting code for the same legal entity at the same time").isIn(EXCLUSION_VIOLATION, UNIQUE_VIOLATION);
        assertThat(probe(accountingCode(1021, LEGAL_ENTITY_B, "CONFIRMED", "2026-10-01", "2026-11-02")))
                .as("the same code for another legal entity in a period that does not overlap").isEqualTo(ACCEPTED);
        assertThat(probe(accountingCode(1099, LEGAL_ENTITY_A, "CONFIRMED", "2026-10-01", "2026-11-02")))
                .as("another code of the same legal entity in a period that does not overlap").isEqualTo(ACCEPTED);

        Map<String, Object> samePersonTwice = new LinkedHashMap<>();
        samePersonTwice.put("own_id", "01900000-0000-7000-8000-0000000000e8");
        assertThat(probe(samePersonTwice)).as("one organisation-source person, a second own id").isIn(EXCLUSION_VIOLATION, UNIQUE_VIOLATION);
        Map<String, Object> twoPeopleOneOwnId = new LinkedHashMap<>();
        twoPeopleOneOwnId.put("source_id", -8_000_002L);
        assertThat(probe(twoPeopleOneOwnId)).as("two organisation-source persons, one own id").isIn(EXCLUSION_VIOLATION, UNIQUE_VIOLATION);
    }

    // RULE-468
    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条：待确认行与已拒绝行不占约束，可与生效中的已确认行重叠")
    void pendingAndRejectedRowsTakeNoKey() throws Exception {
        assertThat(insert(accountingCode(1021, LEGAL_ENTITY_A, "CONFIRMED", "2026-11-02", null), true)).isEqualTo(ACCEPTED);

        assertThat(probe(accountingCode(1021, LEGAL_ENTITY_B, "PENDING", "2026-11-20", null))).isEqualTo(ACCEPTED);
        assertThat(probe(accountingCode(1021, LEGAL_ENTITY_B, "REJECTED", "2026-11-20", null))).isEqualTo(ACCEPTED);
        assertThat(probe(accountingCode(1099, LEGAL_ENTITY_A, "PENDING", "2026-11-20", null))).isEqualTo(ACCEPTED);
        assertThat(insert(accountingCode(1099, LEGAL_ENTITY_A, "PENDING", "2026-11-20", null), true)).isEqualTo(ACCEPTED);
        assertThat(insert(accountingCode(1098, LEGAL_ENTITY_A, "PENDING", "2026-11-20", null), true))
                .as("two pending corrections side by side").isEqualTo(ACCEPTED);
    }

    // RULE-463
    @Test
    @DisplayName("R-1b P3a 复审 M1（ADR-0033 第 5 项第 4 条）：人工登记的行（有录入人）没有决定人时，库内直接改为已确认或已拒绝都被拒绝；写明他人为决定人才可以")
    void manualRowIsNeverDecidedWithoutADecider() throws Exception {
        UUID manual = manualRegistration("SKA", 9101);

        assertThat(update(manual, "confirmation_status = 'CONFIRMED'")).as("confirmed with no decider").isNotEqualTo(ACCEPTED);
        assertThat(update(manual, "confirmation_status = 'REJECTED'")).as("rejected with no decider").isNotEqualTo(ACCEPTED);
        assertThat(update(manual, "confirmation_status = 'CONFIRMED', decided_at = now()")).as("confirmed with a time but no decider").isNotEqualTo(ACCEPTED);

        String decider = someOtherUser();
        assertThat(update(manual, "confirmation_status = 'CONFIRMED', decided_by = " + decider + ", decided_at = now()"))
                .as("confirmed by another person who is named as the decider").isEqualTo(ACCEPTED);
        assertThat(update(manual, "confirmation_status = 'REJECTED', decided_by = " + decider + ", decided_at = now()"))
                .as("rejected by another person who is named as the decider").isEqualTo(ACCEPTED);
    }

    // RULE-469
    @Test
    @DisplayName("R-1b P3a 复审 M1（ADR-0033 第 2 项第 1 条、§14.1 D17）：来源对照只关闭不删除——DELETE 与 TRUNCATE 都被数据库拒绝，行仍在")
    void referencesAreNeverDeleted() throws Exception {
        UUID manual = manualRegistration("SKB", 9201);

        assertThat(statement("delete from source_references where source_reference_id = ?", template)).as("deleting a synchronised reference")
                .isNotEqualTo(ACCEPTED);
        assertThat(statement("delete from source_references where source_reference_id = ?", manual)).as("deleting a pending registration")
                .isNotEqualTo(ACCEPTED);
        assertThat(statement("truncate source_references", null)).as("truncating the table").isNotEqualTo(ACCEPTED);

        assertThat(present(template)).isTrue();
        assertThat(present(manual)).isTrue();
    }

    /** Runs one update of a row and keeps it when accepted; answers ACCEPTED or the SQL state. */
    private String keptUpdate(UUID row, String assignment) throws SQLException {
        Savepoint before = integration.setSavepoint();
        try (PreparedStatement statement = integration.prepareStatement("update source_references set " + assignment + " where source_reference_id = ?")) {
            statement.setObject(1, row);
            int touched = statement.executeUpdate();
            if (touched != 1) {
                throw new AssertionError("the update touched " + touched + " rows instead of the one probed: " + assignment);
            }
            return ACCEPTED;
        } catch (SQLException refused) {
            integration.rollback(before);
            return refused.getSQLState();
        }
    }

    private UUID lastProbeId() {
        return UUID.fromString(String.format("01900000-0000-7000-9000-%012x", probes));
    }

    @Test
    @DisplayName("R-1b P3a 复审 M2（ADR-0033 第 2 项第 1 条，用户口径「允许同日更正」）：已确认行可关闭为空区间 [D, D)，也可直接以空区间写入；空区间不占排他约束，同日起的另一已确认行可以并存；失效日早于生效日仍被拒绝")
    void anEmptyPeriodTakesNoKey() throws Exception {
        assertThat(insert(accountingCode(1021, LEGAL_ENTITY_A, "CONFIRMED", "2026-11-02", null), true)).isEqualTo(ACCEPTED);
        UUID wrong = lastProbeId();

        assertThat(keptUpdate(wrong, "effective_to_date = effective_from_date")).as("closing the confirmed row on its own start day").isEqualTo(ACCEPTED);
        assertThat(insert(accountingCode(1099, LEGAL_ENTITY_A, "CONFIRMED", "2026-11-02", null), true))
                .as("the correction of the same legal entity, confirmed from the same day").isEqualTo(ACCEPTED);
        assertThat(probe(accountingCode(1021, LEGAL_ENTITY_B, "CONFIRMED", "2026-11-02", null)))
                .as("the emptied code confirmed for another legal entity from the same day").isEqualTo(ACCEPTED);
        assertThat(probe(accountingCode(1099, LEGAL_ENTITY_B, "CONFIRMED", "2026-11-02", "2026-11-02")))
                .as("a row written with an empty period beside the code in effect").isEqualTo(ACCEPTED);

        assertThat(probe(accountingCode(1050, LEGAL_ENTITY_B, "CONFIRMED", "2026-11-02", "2026-11-01"))).as("a period that ends before it starts")
                .isEqualTo(CHECK_VIOLATION);
        assertThat(probe(accountingCode(1099, LEGAL_ENTITY_B, "CONFIRMED", "2026-11-20", null)))
                .as("the code in effect is still exclusive to its legal entity").isIn(EXCLUSION_VIOLATION, UNIQUE_VIOLATION);
    }

    // RULE-469
    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条：既有行的来源 ID 与自有 ID 不得改写")
    void sourceIdAndOwnIdAreNeverRewritten() throws Exception {
        assertThat(update("source_id = source_id - 1")).as("rewriting the source id").isNotEqualTo(ACCEPTED);
        assertThat(update("own_id = cast('01900000-0000-7000-8000-0000000000e7' as uuid)")).as("rewriting the own id").isNotEqualTo(ACCEPTED);
    }
}
