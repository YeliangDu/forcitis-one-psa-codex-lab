package com.forcitis.one.acceptance.platform.identity.authorization_history;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0034-IMPL card row 0034-G10-01, the database part that PR-1 owns: the two new tables and the two new authorization_requests columns
 * are exactly what card 7.1 declares (columns, uuid primary keys, constraint, index and trigger names, RESTRICT foreign keys), and no
 * column of the new tables takes a value from a database default, so every moment comes from the injected clock (TimeSourceGuardTest,
 * PrimaryKeyTypeGuardTest, DdlNamingTest read the same catalogue). Read from the migrated database, not from the SQL text.
 */
class AuthorizationHistorySchemaIT {

    private AuthorizationHistoryRows db;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new AuthorizationHistoryRows(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private Map<String, String> columns(String table) throws SQLException {
        Map<String, String> found = new LinkedHashMap<>();
        for (Object row : db.column("""
                select a.attname || ' ' || format_type(a.atttypid, a.atttypmod) || case when a.attnotnull then ' NOT NULL' else '' end
                       || coalesce(' DEFAULT ' || pg_get_expr(d.adbin, d.adrelid), '')
                  from pg_attribute a left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
                 where a.attrelid = ?::regclass and a.attnum > 0 and not a.attisdropped order by a.attnum""", table)) {
            String line = (String) row;
            found.put(line.substring(0, line.indexOf(' ')), line.substring(line.indexOf(' ') + 1));
        }
        return found;
    }

    private List<Object> names(String sql, String table) throws SQLException {
        return db.column(sql, table);
    }

    private static final String CONSTRAINTS = "select conname || ' ' || contype::text from pg_constraint where conrelid = ?::regclass and contype <> 'n' order by conname";
    private static final String INDEXES = "select c.relname::text from pg_index i join pg_class c on c.oid = i.indexrelid where i.indrelid = ?::regclass order by 1";
    private static final String TRIGGERS = "select pg_get_triggerdef(oid) from pg_trigger where tgrelid = ?::regclass and not tgisinternal order by tgname";
    private static final String FOREIGN_KEYS = """
            select conname || ' -> ' || confrelid::regclass::text || ' ' || confdeltype::text from pg_constraint
             where conrelid = ?::regclass and contype = 'f' order by conname""";

    @Test
    @DisplayName("0034-G10-01 role_versions 的列、类型、非空与卡 7.1 逐列一致，无任何列默认值（时刻由注入时钟写入，不读数据库时钟）")
    void roleVersionColumnsAreTheCardsWithoutDefaults() throws SQLException {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("role_version_id", "uuid NOT NULL");
        expected.put("role_id", "uuid NOT NULL");
        expected.put("version_number", "integer NOT NULL");
        expected.put("effective_from_at", "timestamp with time zone NOT NULL");
        expected.put("effective_to_at", "timestamp with time zone");
        expected.put("role_name", "text NOT NULL");
        expected.put("role_status", "text NOT NULL");
        expected.put("access_scope_kind", "text NOT NULL");
        expected.put("permission_codes", "text[] NOT NULL");
        expected.put("change_channel_kind", "text NOT NULL");
        expected.put("recorded_by", "uuid");
        expected.put("change_reason", "text NOT NULL");
        expected.put("authorization_request_id", "uuid");
        expected.put("declaration_revision_number", "integer");
        expected.put("version_audit_entry_id", "uuid NOT NULL");
        expected.put("closed_by", "uuid");
        expected.put("closure_reason", "text");
        expected.put("closure_audit_entry_id", "uuid");
        expected.put("created_at", "timestamp with time zone NOT NULL");
        expected.put("updated_at", "timestamp with time zone NOT NULL");

        assertThat(columns("platform_identity.role_versions")).containsExactlyEntriesOf(expected);
    }

    @Test
    @DisplayName("0034-G10-01 account_role_grants 的列、类型、非空与卡 7.1 逐列一致，无任何列默认值")
    void grantColumnsAreTheCardsWithoutDefaults() throws SQLException {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("account_role_grant_id", "uuid NOT NULL");
        expected.put("user_id", "uuid NOT NULL");
        expected.put("role_id", "uuid NOT NULL");
        expected.put("effective_from_at", "timestamp with time zone NOT NULL");
        expected.put("effective_to_at", "timestamp with time zone");
        expected.put("granted_by", "uuid");
        expected.put("grant_channel_kind", "text NOT NULL");
        expected.put("grant_authorization_request_id", "uuid");
        expected.put("migration_batch_id", "uuid");
        expected.put("grant_audit_entry_id", "uuid NOT NULL");
        expected.put("revoked_by", "uuid");
        expected.put("revocation_cause_kind", "text");
        expected.put("revocation_authorization_request_id", "uuid");
        expected.put("revocation_separation_command_id", "uuid");
        expected.put("revocation_audit_entry_id", "uuid");
        expected.put("created_at", "timestamp with time zone NOT NULL");
        expected.put("updated_at", "timestamp with time zone NOT NULL");

        assertThat(columns("platform_identity.account_role_grants")).containsExactlyEntriesOf(expected);
    }

    @Test
    @DisplayName("0034-G10-01 authorization_requests 新增 base_account_version_number bigint 可空、base_role_version_ids uuid[] NOT NULL DEFAULT '{}'，紧跟 permission_codes 之后")
    void authorizationRequestsCarryTheBaseColumns() throws SQLException {
        Map<String, String> columns = columns("platform_identity.authorization_requests");
        List<String> order = List.copyOf(columns.keySet());

        assertThat(columns).containsEntry("base_account_version_number", "bigint").containsEntry("base_role_version_ids", "uuid[] NOT NULL DEFAULT '{}'::uuid[]");
        int permissions = order.indexOf("permission_codes");
        assertThat(permissions).isNotNegative();
        assertThat(order.subList(permissions + 1, permissions + 3)).containsExactly("base_account_version_number", "base_role_version_ids");
    }

    @Test
    @DisplayName("0034-G10-01 role_versions 的约束名集合与卡 7.1 完全一致（主键 pk_、唯一 uk_、外键 fk_、CHECK ck_、排他 ex_），主键单列 uuid")
    void roleVersionConstraintsAreTheCards() throws SQLException {
        assertThat(names(CONSTRAINTS, "platform_identity.role_versions")).containsExactly(
                "ck_role_versions_access_scope_kind c", "ck_role_versions_change_channel_kind c", "ck_role_versions_change_reason c",
                "ck_role_versions_closure c", "ck_role_versions_closure_reason c", "ck_role_versions_declaration_revision_number c",
                "ck_role_versions_finite c", "ck_role_versions_manual c", "ck_role_versions_period c", "ck_role_versions_permission_codes c",
                "ck_role_versions_role_name c", "ck_role_versions_role_status c", "ck_role_versions_seed c", "ck_role_versions_version_number c",
                "ex_role_versions_role_id_period x", "ex_role_versions_role_name_period x", "fk_role_versions_authorization_request_id f",
                "fk_role_versions_closed_by f", "fk_role_versions_recorded_by f", "fk_role_versions_role_id f", "pk_role_versions p",
                "uk_role_versions_role_id_version_number u");
        assertThat(names(FOREIGN_KEYS, "platform_identity.role_versions")).containsExactly(
                "fk_role_versions_authorization_request_id -> platform_identity.authorization_requests r",
                "fk_role_versions_closed_by -> platform_identity.user_accounts r",
                "fk_role_versions_recorded_by -> platform_identity.user_accounts r",
                "fk_role_versions_role_id -> platform_identity.roles r");
        assertThat(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'pk_role_versions'")).containsExactly("PRIMARY KEY (role_version_id)");
    }

    @Test
    @DisplayName("0034-G10-01 account_role_grants 的约束名集合与卡 7.1 完全一致，外键全部 ON DELETE RESTRICT，主键单列 uuid")
    void grantConstraintsAreTheCards() throws SQLException {
        assertThat(names(CONSTRAINTS, "platform_identity.account_role_grants")).containsExactly(
                "ck_account_role_grants_finite c", "ck_account_role_grants_grant c", "ck_account_role_grants_grant_channel_kind c",
                "ck_account_role_grants_period c", "ck_account_role_grants_revocation c", "ck_account_role_grants_revocation_cause_kind c",
                "ex_account_role_grants_user_id_role_id_period x", "fk_account_role_grants_grant_authorization_request_id f",
                "fk_account_role_grants_granted_by f", "fk_account_role_grants_revocation_authorization_request_id f",
                "fk_account_role_grants_revoked_by f", "fk_account_role_grants_role_id f", "fk_account_role_grants_user_id f",
                "pk_account_role_grants p");
        assertThat(names(FOREIGN_KEYS, "platform_identity.account_role_grants")).containsExactly(
                "fk_account_role_grants_grant_authorization_request_id -> platform_identity.authorization_requests r",
                "fk_account_role_grants_granted_by -> platform_identity.user_accounts r",
                "fk_account_role_grants_revocation_authorization_request_id -> platform_identity.authorization_requests r",
                "fk_account_role_grants_revoked_by -> platform_identity.user_accounts r",
                "fk_account_role_grants_role_id -> platform_identity.roles r",
                "fk_account_role_grants_user_id -> platform_identity.user_accounts r");
        assertThat(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'pk_account_role_grants'"))
                .containsExactly("PRIMARY KEY (account_role_grant_id)");
    }

    @Test
    @DisplayName("0034-G10-01 两张表的索引名集合与卡 7.1 完全一致（约束自带索引 + ix_ 部分索引）")
    void indexesAreTheCards() throws SQLException {
        assertThat(names(INDEXES, "platform_identity.role_versions")).containsExactly(
                "ex_role_versions_role_id_period", "ex_role_versions_role_name_period", "ix_role_versions_authorization_request_id",
                "ix_role_versions_closed_by", "ix_role_versions_recorded_by", "ix_role_versions_role_id_current", "pk_role_versions",
                "uk_role_versions_role_id_version_number");
        assertThat(names(INDEXES, "platform_identity.account_role_grants")).containsExactly(
                "ex_account_role_grants_user_id_role_id_period", "ix_account_role_grants_grant_authorization_request_id",
                "ix_account_role_grants_granted_by", "ix_account_role_grants_migration_batch_id", "ix_account_role_grants_revocation_authorization_request_id",
                "ix_account_role_grants_revoked_by", "ix_account_role_grants_role_id_current", "ix_account_role_grants_user_id_current",
                "ix_account_role_grants_user_id_effective_from_at", "pk_account_role_grants");
        assertThat(db.column("select pg_get_indexdef('platform_identity.ix_role_versions_role_id_current'::regclass)"))
                .containsExactly("CREATE INDEX ix_role_versions_role_id_current ON platform_identity.role_versions USING btree (role_id) WHERE (effective_to_at IS NULL)");
        assertThat(db.column("select pg_get_indexdef('platform_identity.ix_account_role_grants_user_id_current'::regclass)"))
                .containsExactly("CREATE INDEX ix_account_role_grants_user_id_current ON platform_identity.account_role_grants USING btree (user_id) WHERE (effective_to_at IS NULL)");
        assertThat(db.column("select pg_get_indexdef('platform_identity.ix_account_role_grants_role_id_current'::regclass)"))
                .containsExactly("CREATE INDEX ix_account_role_grants_role_id_current ON platform_identity.account_role_grants USING btree (role_id) WHERE (effective_to_at IS NULL)");
    }

    @Test
    @DisplayName("0034-G10-01 触发器与卡 7.1 一致：两表各有行级 BEFORE UPDATE OR DELETE 与语句级 BEFORE TRUNCATE 的 refuse_history_rewrite（关闭列参数逐个列出），授予另有 BEFORE INSERT 的 require_open_role_version")
    void triggersAreTheCards() throws SQLException {
        assertThat(names(TRIGGERS, "platform_identity.role_versions")).containsExactly(
                "CREATE TRIGGER tg_role_versions_append_only BEFORE DELETE OR UPDATE ON platform_identity.role_versions FOR EACH ROW EXECUTE FUNCTION"
                        + " platform_identity.refuse_history_rewrite('effective_to_at', 'closed_by', 'closure_reason', 'closure_audit_entry_id', 'updated_at')",
                "CREATE TRIGGER tg_role_versions_append_only_truncate BEFORE TRUNCATE ON platform_identity.role_versions FOR EACH STATEMENT EXECUTE FUNCTION"
                        + " platform_identity.refuse_history_rewrite()");
        assertThat(names(TRIGGERS, "platform_identity.account_role_grants")).containsExactly(
                "CREATE TRIGGER tg_account_role_grants_append_only BEFORE DELETE OR UPDATE ON platform_identity.account_role_grants FOR EACH ROW EXECUTE FUNCTION"
                        + " platform_identity.refuse_history_rewrite('effective_to_at', 'revoked_by', 'revocation_cause_kind', 'revocation_authorization_request_id',"
                        + " 'revocation_separation_command_id', 'revocation_audit_entry_id', 'updated_at')",
                "CREATE TRIGGER tg_account_role_grants_append_only_truncate BEFORE TRUNCATE ON platform_identity.account_role_grants FOR EACH STATEMENT EXECUTE FUNCTION"
                        + " platform_identity.refuse_history_rewrite()",
                "CREATE TRIGGER tg_account_role_grants_open_role_version BEFORE INSERT ON platform_identity.account_role_grants FOR EACH ROW EXECUTE FUNCTION"
                        + " platform_identity.require_open_role_version()");
    }
}
