package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.acceptance.P4Names;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P4 ADR-0033 section 8 item 2 (card 3.1, acceptance item 2; written like UserIdColumnsIT): the approval tables carry the engine's
 * and the document's ids as uuid, the child tables hang on the approval request by a foreign key to its primary key instead of
 * the text keys, and the indexes and constraints of DDL-04 and DDL-08 exist. Read from the catalogue of the migrated database.
 */
class ApprovalOwnIdColumnsIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(ApprovalOwnIdColumnsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** schema, table, column: the columns that are uuid in the final shape. */
    static final List<String[]> UUID_COLUMNS = List.of(
            new String[] {"platform_approval", "approval_requests", "document_id"},
            new String[] {"platform_approval", "approval_requests", "current_instance_id"},
            new String[] {"platform_approval", "start_rounds", "instance_id"},
            new String[] {"platform_approval", "start_rounds", P4Names.APPROVAL_REQUEST_ID},
            new String[] {"platform_approval", "approval_tasks", "task_id"},
            new String[] {"platform_approval", "approval_tasks", "instance_id"},
            new String[] {"platform_approval", "approval_tasks", P4Names.APPROVAL_REQUEST_ID},
            new String[] {"platform_approval", "approval_task_records", "task_id"},
            new String[] {"platform_approval", "approval_task_records", "instance_id"},
            new String[] {"platform_approval", "approval_task_records", P4Names.APPROVAL_REQUEST_ID},
            new String[] {"platform_approval", "process_archive_skips", "process_instance_id"},
            new String[] {"platform_approval", "process_archive_skips", P4Names.PROCESS_ARCHIVE_SKIP_ID},
            new String[] {"platform_integration", "outbound_deliveries", P4Names.SUBJECT_ID},
            new String[] {"platform_integration", "outbound_deliveries", P4Names.TRIGGER_EVENT_ID},
            new String[] {"platform_integration", "outbound_deliveries", P4Names.TARGET_DELIVERY_ID});

    /** schema, table, column: columns that no longer exist. */
    static final List<String[]> GONE_COLUMNS = List.of(
            new String[] {"platform_approval", "approval_requests", "document_key"},
            new String[] {"platform_approval", "start_rounds", "process_key"},
            new String[] {"platform_approval", "start_rounds", "document_key"},
            new String[] {"platform_approval", "approval_tasks", "process_key"},
            new String[] {"platform_approval", "approval_tasks", "document_key"},
            new String[] {"platform_approval", "approval_task_records", "process_key"},
            new String[] {"platform_approval", "approval_task_records", "document_key"},
            new String[] {"platform_integration", "outbound_deliveries", "deduplication_key"},
            new String[] {"platform_integration", "outbound_deliveries", "target_key"});

    private String scalar(Connection catalogue, String sql, String... parameters) throws SQLException {
        try (PreparedStatement select = catalogue.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                select.setString(i + 1, parameters[i]);
            }
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String typeOf(Connection catalogue, String schema, String table, String column) throws SQLException {
        return scalar(catalogue, "select data_type from information_schema.columns where table_schema = ? and table_name = ? and column_name = ?",
                schema, table, column);
    }

    private List<String> indexColumns(Connection catalogue, String schema, String indexName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement select = catalogue.prepareStatement("""
                select a.attname from pg_index i
                  join pg_class c on c.oid = i.indexrelid
                  join pg_namespace n on n.oid = c.relnamespace
                  join lateral unnest(i.indkey::int2[]) with ordinality k(attnum, ord) on true
                  join pg_attribute a on a.attrelid = i.indrelid and a.attnum = k.attnum
                 where n.nspname = ? and c.relname = ? and k.ord <= i.indnkeyatts order by k.ord""")) {
            select.setString(1, schema);
            select.setString(2, indexName);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }

    private String indexDefinition(Connection catalogue, String schema, String indexName) throws SQLException {
        return scalar(catalogue, "select indexdef from pg_indexes where schemaname = ? and indexname = ?", schema, indexName);
    }

    private String constraintDefinition(Connection catalogue, String schema, String name) throws SQLException {
        return scalar(catalogue, """
                select pg_get_constraintdef(k.oid) from pg_constraint k join pg_namespace n on n.oid = k.connamespace
                 where n.nspname = ? and k.conname = ?""", schema, name);
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 8 项第 2 条（新列断言，写法同 UserIdColumnsIT）：任务、实例、单据 ID 与新增的审批请求 ID、投递对象 ID 等列为 uuid；按文本键挂根的旧列、去重键与 target_key 已不存在")
    void theIdColumnsAreUuidAndTheTextKeysAreGone() throws Exception {
        List<String> wrong = new ArrayList<>();
        try (Connection catalogue = schemaConnection("public")) {
            for (String[] column : UUID_COLUMNS) {
                String type = typeOf(catalogue, column[0], column[1], column[2]);
                if (!"uuid".equals(type)) {
                    wrong.add(column[0] + "." + column[1] + "." + column[2] + " is " + (type == null ? "missing" : type));
                }
            }
            for (String[] column : GONE_COLUMNS) {
                String type = typeOf(catalogue, column[0], column[1], column[2]);
                if (type != null) {
                    wrong.add(column[0] + "." + column[1] + "." + column[2] + " still exists as " + type);
                }
            }
            assertThat(scalar(catalogue, "select count(*) from pg_constraint k join pg_namespace n on n.oid = k.connamespace where n.nspname = 'platform_integration'"
                    + " and k.conname = 'outbound_deliveries_deduplication_key_key'")).as("the deduplication key constraint").isEqualTo("0");
        }

        assertThat(wrong).as("columns that are not in the final shape").isEmpty();
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 8 项第 2 条：approval_tasks 与 process_archive_skips 的主键是单列 uuid（task_id、process_archive_skip_id），名称 pk_* 不变")
    void theKeysAreSingleUuidColumns() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            assertThat(indexColumns(catalogue, "platform_approval", "pk_approval_tasks")).containsExactly("task_id");
            assertThat(indexColumns(catalogue, "platform_approval", "pk_process_archive_skips")).containsExactly(P4Names.PROCESS_ARCHIVE_SKIP_ID);
            assertThat(typeOf(catalogue, "platform_approval", "approval_tasks", "task_id")).isEqualTo("uuid");
            assertThat(typeOf(catalogue, "platform_approval", "process_archive_skips", P4Names.PROCESS_ARCHIVE_SKIP_ID)).isEqualTo("uuid");
        }
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 8 项第 2 条：三张子表（发起轮次、任务、办理记录）有 approval_request_id 非空列，外键指向 approval_requests (id)")
    void childTablesHangOnTheApprovalRequest() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            for (String table : List.of("start_rounds", "approval_tasks", "approval_task_records")) {
                assertThat(scalar(catalogue, "select is_nullable from information_schema.columns where table_schema = 'platform_approval'"
                        + " and table_name = ? and column_name = ?", table, P4Names.APPROVAL_REQUEST_ID)).as("nullable " + table).isEqualTo("NO");
                assertThat(scalar(catalogue, """
                        select count(*) from pg_constraint k
                          join pg_class c on c.oid = k.conrelid join pg_namespace n on n.oid = c.relnamespace
                          join pg_class t on t.oid = k.confrelid
                          join pg_attribute a on a.attrelid = k.conrelid and a.attnum = k.conkey[1]
                          join pg_attribute f on f.attrelid = k.confrelid and f.attnum = k.confkey[1]
                         where k.contype = 'f' and n.nspname = 'platform_approval' and c.relname = ? and t.relname = 'approval_requests'
                           and array_length(k.conkey, 1) = 1 and a.attname = ? and f.attname = 'id'""", table, P4Names.APPROVAL_REQUEST_ID))
                        .as("foreign key of %s.%s to approval_requests (id)", table, P4Names.APPROVAL_REQUEST_ID).isEqualTo("1");
                assertThat(scalar(catalogue, """
                        select count(*) from pg_constraint k join pg_class c on c.oid = k.conrelid join pg_namespace n on n.oid = c.relnamespace
                         where k.contype = 'f' and n.nspname = 'platform_approval' and c.relname = ? and k.confrelid = 'platform_approval.approval_requests'::regclass
                           and array_length(k.conkey, 1) > 1""", table)).as("no foreign key on the text keys remains in " + table).isEqualTo("0");
            }
        }
    }

    @Test
    @DisplayName("R-1b P4 DDL-04 / DDL-08：审批请求与发起轮次的唯一约束、办理记录序号、待办与到期升级的部分索引都在；保留的旧名不变（C10）")
    void theConstraintsAndIndexesExist() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            assertThat(indexColumns(catalogue, "platform_approval", P4Names.UQ_REQUEST_DOCUMENT)).containsExactly("process_key", P4Names.DOCUMENT_ID);
            assertThat(indexColumns(catalogue, "platform_approval", P4Names.UQ_ROUND)).containsExactly(P4Names.APPROVAL_REQUEST_ID, "round_number");
            assertThat(indexColumns(catalogue, "platform_approval", P4Names.UK_RECORD_NUMBER)).containsExactly(P4Names.APPROVAL_REQUEST_ID, P4Names.RECORD_NUMBER);
            assertThat(indexColumns(catalogue, "platform_approval", P4Names.UK_ROUND_INSTANCE)).containsExactly("instance_id");
            assertThat(indexColumns(catalogue, "platform_approval", P4Names.UK_SKIP_INSTANCE)).containsExactly("process_instance_id");
            assertThat(indexColumns(catalogue, "platform_approval", "ix_approval_tasks_approval_request_id")).containsExactly(P4Names.APPROVAL_REQUEST_ID);

            String currentInstance = indexDefinition(catalogue, "platform_approval", P4Names.UK_REQUEST_CURRENT_INSTANCE);
            assertThat(currentInstance).as(P4Names.UK_REQUEST_CURRENT_INSTANCE).isNotNull().contains("UNIQUE").contains("(current_instance_id)")
                    .doesNotContainIgnoringCase("WHERE");
            assertThat(scalar(catalogue, "select is_nullable from information_schema.columns where table_schema = 'platform_approval'"
                    + " and table_name = 'approval_requests' and column_name = 'current_instance_id'")).as("current_instance_id nullable").isEqualTo("NO");
            assertThat(indexDefinition(catalogue, "platform_approval", "ix_approval_requests_instance")).as("the plain instance index is replaced").isNull();

            String open = indexDefinition(catalogue, "platform_approval", P4Names.IX_TASKS_OPEN);
            assertThat(open).as(P4Names.IX_TASKS_OPEN).isNotNull().contains("created_at").contains("PENDING").contains("CLAIMED");
            String escalate = indexDefinition(catalogue, "platform_approval", P4Names.IX_TASKS_ESCALATE_AT);
            assertThat(escalate).as(P4Names.IX_TASKS_ESCALATE_AT).isNotNull().contains("escalate_at").contains("PENDING").contains("CLAIMED")
                    .contains("overdue_at IS NOT NULL").contains("escalated_at IS NULL");
            for (String kept : List.of("ix_approval_tasks_due_at", "ix_approval_tasks_remind_at", "ix_approval_tasks_assignee_id", "ix_approval_tasks_instance_id")) {
                assertThat(indexDefinition(catalogue, "platform_approval", kept)).as("kept index " + kept).isNotNull();
            }
            for (String gone : List.of("ix_approval_tasks_process_key_document_key", "ix_approval_task_records_process_key_document_key")) {
                assertThat(indexDefinition(catalogue, "platform_approval", gone)).as("dropped index " + gone).isNull();
            }
        }
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 1 项第 10 条 / D-3：办理记录有 record_number（integer、非空、不小于 1）；record_id 仍是主键且为 uuid")
    void recordsHaveANumber() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            assertThat(typeOf(catalogue, "platform_approval", "approval_task_records", P4Names.RECORD_NUMBER)).isEqualTo("integer");
            assertThat(scalar(catalogue, "select is_nullable from information_schema.columns where table_schema = 'platform_approval'"
                    + " and table_name = 'approval_task_records' and column_name = ?", P4Names.RECORD_NUMBER)).isEqualTo("NO");
            assertThat(scalar(catalogue, """
                    select string_agg(pg_get_constraintdef(k.oid), ' | ') from pg_constraint k
                     where k.conrelid = 'platform_approval.approval_task_records'::regclass and k.contype = 'c'
                       and pg_get_constraintdef(k.oid) like '%record_number%'""")).as("check on record_number").isNotNull().contains(">= 1");
            assertThat(indexColumns(catalogue, "platform_approval", "pk_approval_task_records")).containsExactly("record_id");
        }
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 7 项第 3 条 / D-4：外发投递的去重键拆列——对象类别与取值、对象 ID、用途与取值、触发事件 ID、被替换的投递 ID；卡片唯一键与说明唯一键；已处理说明约束名不变")
    void deliveriesAreKeyedByColumns() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            for (String column : List.of(P4Names.SUBJECT_KIND, P4Names.SUBJECT_ID, P4Names.PURPOSE_KIND)) {
                assertThat(scalar(catalogue, "select is_nullable from information_schema.columns where table_schema = 'platform_integration'"
                        + " and table_name = 'outbound_deliveries' and column_name = ?", column)).as("nullable " + column).isEqualTo("NO");
            }
            assertThat(typeOf(catalogue, "platform_integration", "outbound_deliveries", P4Names.SUBJECT_KIND)).isEqualTo("text");
            assertThat(typeOf(catalogue, "platform_integration", "outbound_deliveries", P4Names.PURPOSE_KIND)).isEqualTo("text");
            for (String column : List.of(P4Names.TRIGGER_EVENT_ID, P4Names.TARGET_DELIVERY_ID)) {
                assertThat(scalar(catalogue, "select is_nullable from information_schema.columns where table_schema = 'platform_integration'"
                        + " and table_name = 'outbound_deliveries' and column_name = ?", column)).as("nullable " + column).isEqualTo("YES");
            }
            String kinds = constraintDefinition(catalogue, "platform_integration", P4Names.CK_SUBJECT_KIND);
            assertThat(kinds).as(P4Names.CK_SUBJECT_KIND).isNotNull().contains(P4Names.SUBJECT_APPROVAL_TASK).contains(P4Names.SUBJECT_APPROVAL_INSTANCE);
            String purposes = constraintDefinition(catalogue, "platform_integration", P4Names.CK_PURPOSE_KIND);
            assertThat(purposes).as(P4Names.CK_PURPOSE_KIND).isNotNull().contains(P4Names.PURPOSE_ASSIGNED, P4Names.PURPOSE_TRANSFERRED,
                    P4Names.PURPOSE_REASSIGNED, P4Names.PURPOSE_OVERDUE, P4Names.PURPOSE_ESCALATED, P4Names.PURPOSE_COMPLETED);

            String card = indexDefinition(catalogue, "platform_integration", P4Names.UK_DELIVERY_CARD);
            assertThat(card).as(P4Names.UK_DELIVERY_CARD).isNotNull().contains("UNIQUE")
                    .contains("(" + P4Names.SUBJECT_KIND + ", " + P4Names.SUBJECT_ID + ", " + P4Names.PURPOSE_KIND + ", " + P4Names.TRIGGER_EVENT_ID + ")")
                    .contains("NULLS NOT DISTINCT").contains("CARD");
            assertThat(indexColumns(catalogue, "platform_integration", P4Names.UK_DELIVERY_TARGET)).containsExactly(P4Names.TARGET_DELIVERY_ID);
            assertThat(constraintDefinition(catalogue, "platform_integration", P4Names.FK_DELIVERY_TARGET)).isNotNull()
                    .contains("REFERENCES platform_integration.outbound_deliveries(id)");
            assertThat(constraintDefinition(catalogue, "platform_integration", P4Names.HANDLED_NOTE_TARGET)).as("kept name, new expression").isNotNull()
                    .contains(P4Names.TARGET_DELIVERY_ID).doesNotContain("target_key");
        }
    }
}
