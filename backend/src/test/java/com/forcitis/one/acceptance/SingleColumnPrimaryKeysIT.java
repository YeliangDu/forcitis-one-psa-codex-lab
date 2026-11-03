package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
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
 * R-1b P5 RULING-P5-D12 (ADR-0033 section 1 item 2, "same for the old tables that are still there when R-1b merges"): role_versions
 * (which took over from the retired role_permissions in ADR-0034-IMPL PR-2a) gets a single uuid primary key, and the columns of its former key become a named
 * unique constraint. sync_run_held_separations was dropped with the old sync by #934 (ADR-0068 section 2.5 D2) and is no longer listed; account_roles, the third
 * table of the ruling, was dropped by #732, and the grant store account_role_grants is checked in its own method. Read from the catalogue of the migrated database.
 */
class SingleColumnPrimaryKeysIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(SingleColumnPrimaryKeysIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private List<String> columnsOf(Connection catalogue, String schema, String table, boolean primary) throws SQLException {
        List<List<String>> constraints = new ArrayList<>();
        try (PreparedStatement select = catalogue.prepareStatement("""
                select k.oid, k.contype, array(select a.attname::text from unnest(k.conkey) with ordinality u(attnum, position)
                                         join pg_attribute a on a.attrelid = k.conrelid and a.attnum = u.attnum order by u.position) as columns
                  from pg_constraint k join pg_class c on c.oid = k.conrelid join pg_namespace n on n.oid = c.relnamespace
                 where n.nspname = ? and c.relname = ? and k.contype in ('p', 'u')""")) {
            select.setString(1, schema);
            select.setString(2, table);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    boolean isPrimary = "p".equals(rs.getString("contype"));
                    if (isPrimary == primary) {
                        constraints.add(List.of((String[]) rs.getArray("columns").getArray()));
                    }
                }
            }
        }
        return constraints.isEmpty() ? List.of() : constraints.stream().map(columns -> String.join(",", columns)).toList();
    }

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
    @DisplayName("R-1b P5 RULING-P5-D12：role_versions（取代已退役的 role_permissions，ADR-0034-IMPL 卡 7.2）的主键是单列 uuid（account_roles 已随 #732 删除，见下一条）；原键的列成为命名唯一约束")
    void theRekeyedTableHasASingleUuidKeyAndItsOldKeyIsUnique() throws Exception {
        List<String> wrong = new ArrayList<>();
        try (Connection catalogue = schemaConnection("public")) {
            for (String[] table : P5Names.REKEYED_TABLES) {
                String name = table[0] + "." + table[1];
                List<String> keys = columnsOf(catalogue, table[0], table[1], true);
                if (keys.size() != 1 || keys.getFirst().contains(",")) {
                    wrong.add(name + " primary key is " + keys);
                } else if (!"uuid".equals(typeOf(catalogue, table[0], table[1], keys.getFirst()))) {
                    wrong.add(name + " primary key column " + keys.getFirst() + " is not uuid");
                } else if (List.of(table[2].split(",")).contains(keys.getFirst())) {
                    wrong.add(name + " primary key is still a column of the old key");
                }
                if (!columnsOf(catalogue, table[0], table[1], false).contains(table[2])) {
                    wrong.add(name + " has no unique constraint on (" + table[2] + ")");
                }
            }
        }

        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("#732（接替 account_roles 一行）：account_roles 已删除；唯一授予表 account_role_grants 的主键是单列 uuid")
    void theGrantStoreHasASingleUuidKeyAndAccountRolesIsGone() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            assertThat(typeOf(catalogue, "platform_identity", "account_roles", "user_id")).as("account_roles no longer exists").isNull();
            List<String> keys = columnsOf(catalogue, "platform_identity", "account_role_grants", true);
            assertThat(keys).hasSize(1);
            assertThat(keys.getFirst()).doesNotContain(",");
            assertThat(typeOf(catalogue, "platform_identity", "account_role_grants", keys.getFirst())).isEqualTo("uuid");
        }
    }
}
