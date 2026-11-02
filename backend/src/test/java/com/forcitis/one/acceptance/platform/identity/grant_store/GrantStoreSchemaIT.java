package com.forcitis.one.acceptance.platform.identity.grant_store;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #732 part A, DD sign-off section A (open baseline edited in place): platform_identity.account_roles is gone with its index and comments, and
 * account_role_grants stays exactly as it is, no new column. Read from the catalogue of the database the application's own migrations build.
 */
class GrantStoreSchemaIT {

    static final TestDatabase postgres = TestPostgres.database(GrantStoreSchemaIT.class, TestPostgres.MIGRATED);

    private List<String> column(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(postgres.url(), postgres.username(), postgres.password());
             PreparedStatement select = connection.prepareStatement(sql); ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    @Test
    @DisplayName("DD 签发 #732 A：account_roles 表、ix_account_roles_role 及其约束都不存在；没有表的外键指向它")
    void accountRolesIsDropped() throws SQLException {
        assertThat(column("select to_regclass('platform_identity.account_roles')::text")).containsExactly((String) null);
        assertThat(column("select indexname from pg_indexes where schemaname = 'platform_identity' and indexname like '%account\\_roles%' escape '\\'")).isEmpty();
        assertThat(column("select conname from pg_constraint where conname like '%account\\_roles%' escape '\\'")).isEmpty();
        assertThat(column("select table_name from information_schema.tables where table_schema = 'platform_identity' and table_name = 'account_roles'")).isEmpty();
    }

    @Test
    @DisplayName("DD 签发 #732 A（特征测试，在 main 上已绿，须保持）：account_role_grants 列不增不减、顺序不变，离职关闭用的列都在")
    void accountRoleGrantsStaysUnchanged() throws SQLException {
        assertThat(column("""
                select column_name from information_schema.columns where table_schema = 'platform_identity' and table_name = 'account_role_grants'
                 order by ordinal_position""")).containsExactly("account_role_grant_id", "user_id", "role_id", "effective_from_at", "effective_to_at", "granted_by",
                "grant_channel_kind", "grant_authorization_request_id", "migration_batch_id", "grant_audit_entry_id", "revoked_by", "revocation_cause_kind",
                "revocation_authorization_request_id", "revocation_separation_command_id", "revocation_audit_entry_id", "created_at", "updated_at");
        assertThat(column("select conname from pg_constraint where conrelid = 'platform_identity.account_role_grants'::regclass and conname = 'ck_account_role_grants_revocation'"))
                .hasSize(1);
    }
}
