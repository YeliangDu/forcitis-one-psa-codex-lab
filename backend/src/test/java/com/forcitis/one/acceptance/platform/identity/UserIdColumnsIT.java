package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
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
 * R-1b P2 (ADR-0033 section 3 item 1, NAMING_STANDARD 2.4 "_id" and "_by"): every column that holds the id of a user account is a uuid,
 * in identity and in every other schema at once; the account row names its employee in a column of its own that is unique. Read from the
 * catalogue of the migrated database, so the test asserts the tables the application really runs on.
 */
class UserIdColumnsIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(UserIdColumnsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** schema, table, column: the user id columns listed for P2 (NEXT_BATCH_PLAN 7.2, R-1b split plan section 2). */
    static final List<String[]> USER_ID_COLUMNS = List.of(
            new String[] {"platform_identity", "user_accounts", "user_id"},
            new String[] {"platform_identity", "emergency_admins", "user_id"},
            new String[] {"platform_identity", "account_roles", "user_id"},
            new String[] {"platform_identity", "browser_identifiers", "user_id"},
            new String[] {"platform_identity", "authorization_requests", "requested_by"},
            new String[] {"platform_identity", "authorization_requests", "subject_user_id"},
            new String[] {"platform_identity", "authorization_requests", "decided_by"},
            new String[] {"platform_idempotency", "idempotent_responses", "user_id"},
            new String[] {"platform_audit", "audit_entries", "actor_id"},
            new String[] {"platform_audit", "audit_entries", "on_behalf_of_id"},
            new String[] {"platform_approval", "approval_tasks", "assignee_id"},
            new String[] {"platform_approval", "approval_task_records", "actor_id"},
            new String[] {"platform_approval", "approval_task_records", "on_behalf_of_id"},
            new String[] {"platform_approval", "approval_task_records", "assignee_id"},
            new String[] {"platform_numbering", "numbering_rule_versions", "author_id"},
            new String[] {"platform_numbering", "numbering_rule_versions", "confirmed_by"},
            new String[] {"platform_numbering", "numbering_rule_versions", "finance_confirmed_by"},
            new String[] {"organization_workforce", "sync_runs", "released_by"},
            new String[] {"organization_workforce", "legal_entity_attribution_corrections", "corrected_by_user_id"},
            new String[] {"platform_integration", "source_references", "created_by"},
            new String[] {"platform_integration", "source_references", "decided_by"});

    private String typeOf(Connection catalogue, String schema, String table, String column) throws SQLException {
        try (PreparedStatement select = catalogue.prepareStatement(
                "select data_type from information_schema.columns where table_schema = ? and table_name = ? and column_name = ?")) {
            select.setString(1, schema);
            select.setString(2, table);
            select.setString(3, column);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 1 条 / NAMING_STANDARD 2.4：存用户 ID 的列在所有 schema 中一次改为 uuid")
    void everyUserIdColumnIsUuid() throws Exception {
        List<String> wrong = new ArrayList<>();
        try (Connection catalogue = schemaConnection("public")) {
            for (String[] column : USER_ID_COLUMNS) {
                String type = typeOf(catalogue, column[0], column[1], column[2]);
                if (!"uuid".equals(type)) {
                    wrong.add(column[0] + "." + column[1] + "." + column[2] + " is " + (type == null ? "missing" : type));
                }
            }
        }

        assertThat(wrong).as("user id columns that are not uuid").isEmpty();
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 1 条：账号表另有 employee_id 列，非空、与员工 ID 同型、单列唯一；user_id 是主键")
    void theAccountNamesItsEmployeeInAUniqueColumn() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            String employeeIdType = typeOf(catalogue, "organization_workforce", "employees", "id");
            assertThat(typeOf(catalogue, "platform_identity", "user_accounts", "employee_id")).as("type of user_accounts.employee_id")
                    .isNotNull().isEqualTo(employeeIdType);
            try (PreparedStatement select = catalogue.prepareStatement("""
                    select is_nullable from information_schema.columns
                     where table_schema = 'platform_identity' and table_name = 'user_accounts' and column_name = 'employee_id'""");
                 ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).as("employee_id is NOT NULL").isEqualTo("NO");
            }
            assertThat(singleColumnUniqueIndexes(catalogue, "employee_id", false)).as("unique indexes on user_accounts (employee_id) alone").isEqualTo(1);
            assertThat(singleColumnUniqueIndexes(catalogue, "user_id", true)).as("primary key on user_accounts (user_id)").isEqualTo(1);
        }
    }

    private int singleColumnUniqueIndexes(Connection catalogue, String column, boolean primary) throws SQLException {
        try (PreparedStatement select = catalogue.prepareStatement("""
                select count(*) from pg_index i
                  join pg_class c on c.oid = i.indrelid
                  join pg_namespace n on n.oid = c.relnamespace
                  join pg_attribute a on a.attrelid = c.oid and a.attnum = i.indkey[0]
                 where n.nspname = 'platform_identity' and c.relname = 'user_accounts' and i.indisunique and i.indnkeyatts = 1
                   and i.indpred is null and a.attname = ? and i.indisprimary = ?""")) {
            select.setString(1, column);
            select.setBoolean(2, primary);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 影响面「ticket_issuers.key_id 改名」/ NAMING_STANDARD 2.4：签发方的密钥标识不再叫 key_id，identity 中没有文本型 _id 列")
    void noTextColumnIsCalledAnId() throws Exception {
        List<String> textIds = new ArrayList<>();
        try (Connection catalogue = schemaConnection("public"); PreparedStatement select = catalogue.prepareStatement("""
                select table_name || '.' || column_name from information_schema.columns
                 where table_schema = 'platform_identity' and data_type = 'text' and column_name like '%\\_id' escape '\\'
                   and table_name not in ('sessions', 'sessions_attributes')
                 order by 1"""); ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                textIds.add(rs.getString(1));
            }
        }

        assertThat(textIds).as("text columns of platform_identity whose name ends in _id").doesNotContain("ticket_issuers.key_id").isEmpty();
    }
}
