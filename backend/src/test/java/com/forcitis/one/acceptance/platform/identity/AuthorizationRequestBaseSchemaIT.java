package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #938, old card 7.3 and A-11 (ADR-0034 decision 2 item 5; DD row 21): request_status is one named CHECK that admits VOIDED, and the
 * named CHECK ck_authorization_requests_base pairs the base with the request kind: an account request carries a base account version
 * and only it does, a role change carries exactly one base role version, a new role none. The status comment explains VOIDED.
 */
class AuthorizationRequestBaseSchemaIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(AuthorizationRequestBaseSchemaIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private List<String> checksOf(Connection identity) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement read = identity.createStatement();
             ResultSet rs = read.executeQuery("select conname from pg_constraint where contype = 'c'"
                     + " and conrelid = 'platform_identity.authorization_requests'::regclass")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    /** Inserts one request row in a transaction that is rolled back; answers the name of the constraint that refused it, or null. */
    private String refusalOf(String kind, Long baseAccountVersion, int baseRoleVersions, String status) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity")) {
            identity.setAutoCommit(false);
            try (PreparedStatement insert = identity.prepareStatement("""
                    insert into authorization_requests (request_id, request_kind, requested_by, requested_at, subject_user_id, role_id,
                        new_role_code, base_account_version_number, base_role_version_ids, request_status, decided_by, decided_at)
                    values (?, ?, ?, now(), ?, ?, ?, ?, ?, ?, ?, case when ? = 'PENDING' then null else now() end)""")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setString(2, kind);
                insert.setObject(3, UUID.randomUUID());
                insert.setObject(4, kind.equals("REPLACE_ROLES") ? UUID.randomUUID() : null);
                insert.setObject(5, kind.equals("UPDATE_ROLE") ? UUID.randomUUID() : null);
                insert.setString(6, kind.equals("CREATE_ROLE") ? "synthetic_schema_role" : null);
                insert.setObject(7, baseAccountVersion);
                UUID[] ids = new UUID[baseRoleVersions];
                for (int i = 0; i < baseRoleVersions; i++) {
                    ids[i] = UUID.randomUUID();
                }
                insert.setArray(8, identity.createArrayOf("uuid", ids));
                insert.setString(9, status);
                insert.setObject(10, status.equals("PENDING") ? null : UUID.randomUUID());
                insert.setString(11, status);
                insert.executeUpdate();
                return null;
            } catch (SQLException refused) {
                String message = String.valueOf(refused.getMessage());
                for (String name : List.of("ck_authorization_requests_request_status", "ck_authorization_requests_base", "ck_authorization_requests_two_people")) {
                    if (message.contains("\"" + name + "\"")) {
                        return name;
                    }
                }
                return message;
            } finally {
                identity.rollback();
            }
        }
    }

    @Test
    @DisplayName("#938 旧卡 7.3 / A-11：request_status 由具名 CHECK ck_authorization_requests_request_status 管，取值恰为 PENDING、CONFIRMED、REJECTED、VOIDED；具名 CHECK ck_authorization_requests_base 存在")
    void theStatusAndBaseChecksAreNamed() throws Exception {
        try (Connection identity = schemaConnection("platform_identity")) {
            assertThat(checksOf(identity)).contains("ck_authorization_requests_request_status", "ck_authorization_requests_base");
            try (Statement read = identity.createStatement();
                 ResultSet rs = read.executeQuery("select pg_get_constraintdef(oid) from pg_constraint where conname = 'ck_authorization_requests_request_status'"
                         + " and conrelid = 'platform_identity.authorization_requests'::regclass")) {
                assertThat(rs.next()).isTrue();
                String definition = rs.getString(1);
                assertThat(definition).contains("'PENDING'", "'CONFIRMED'", "'REJECTED'", "'VOIDED'");
                assertThat(definition.split("'").length).as("exactly four values: " + definition).isEqualTo(9);
            }
        }
        assertThat(refusalOf("REPLACE_ROLES", 3L, 2, "VOIDED")).as("a VOIDED account request is a valid row").isNull();
        assertThat(refusalOf("REPLACE_ROLES", 3L, 2, "CANCELLED")).isEqualTo("ck_authorization_requests_request_status");
    }

    @Test
    @DisplayName("#938 数据设计第 21 行 ck_authorization_requests_base：账号授权申请必须有基准账号版本、其他申请必须没有；角色修改申请恰有一个基准角色版本；新建角色申请没有基准角色版本；合规的三类都收")
    void theBaseIsPairedWithTheKind() throws Exception {
        assertThat(refusalOf("REPLACE_ROLES", 3L, 2, "PENDING")).isNull();
        assertThat(refusalOf("UPDATE_ROLE", null, 1, "PENDING")).isNull();
        assertThat(refusalOf("CREATE_ROLE", null, 0, "PENDING")).isNull();

        assertThat(refusalOf("REPLACE_ROLES", null, 2, "PENDING")).as("an account request without a base account version").isEqualTo("ck_authorization_requests_base");
        assertThat(refusalOf("UPDATE_ROLE", 3L, 1, "PENDING")).as("a role change with a base account version").isEqualTo("ck_authorization_requests_base");
        assertThat(refusalOf("CREATE_ROLE", 3L, 0, "PENDING")).as("a new role with a base account version").isEqualTo("ck_authorization_requests_base");
        assertThat(refusalOf("UPDATE_ROLE", null, 0, "PENDING")).as("a role change without its base role version").isEqualTo("ck_authorization_requests_base");
        assertThat(refusalOf("UPDATE_ROLE", null, 2, "PENDING")).as("a role change with two base role versions").isEqualTo("ck_authorization_requests_base");
        assertThat(refusalOf("CREATE_ROLE", null, 1, "PENDING")).as("a new role with a base role version").isEqualTo("ck_authorization_requests_base");
    }

    @Test
    @DisplayName("#938 旧卡 7.3：request_status 的列注释说明 VOIDED（已作废：确认后执行前发现基准已变）")
    void theStatusCommentExplainsVoided() throws Exception {
        try (Connection identity = schemaConnection("platform_identity");
             Statement read = identity.createStatement();
             ResultSet rs = read.executeQuery("select col_description('platform_identity.authorization_requests'::regclass, attnum) from pg_attribute"
                     + " where attrelid = 'platform_identity.authorization_requests'::regclass and attname = 'request_status'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).contains("VOIDED", "作废");
        }
    }
}
