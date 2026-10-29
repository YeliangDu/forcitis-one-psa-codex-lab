package com.forcitis.one.acceptance.platform.identity.authorization_history;

import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.CHECK_VIOLATION;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.EXCLUSION_VIOLATION;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.T0;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.T1;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.T2;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.T3;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * ADR-0034 guard rule 2 (D2 moment variant), ADR-0034-IMPL card row 0034-G2-01 and 7.1. DataArchitectureTest's D2 knows only the date
 * variant today and its rule methods are package-private, so this class checks the rule by behaviour on the two moment-versioned tables:
 * each of the four shapes the guard's injected samples must refuse (no EXCLUDE, no isfinite CHECK, nullable effective_from_at, date
 * instead of timestamptz) is shown absent from the real tables, both in the catalogue and by a row the database refuses. The injected
 * samples themselves belong in DataArchitectureTest (card 3.3, guard commit of the implementation).
 */
class MomentVersionTablesIT {

    static final Path REGISTRY = Path.of("architecture/data-access-registry.yaml");
    static final Path DATABASE_SCRIPT = Path.of("../docs/ops/one-db-roles-schemas.sql");
    static final List<String> MOMENT_TABLES = List.of("platform_identity.role_versions", "platform_identity.account_role_grants");

    private AuthorizationHistoryRows db;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new AuthorizationHistoryRows(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private String columnShape(String table, String column) throws SQLException {
        List<Object> shape = db.column("""
                select format_type(a.atttypid, a.atttypmod) || case when a.attnotnull then ' NOT NULL' else ' NULL' end
                  from pg_attribute a where a.attrelid = ?::regclass and a.attname = ? and not a.attisdropped""", table, column);
        return shape.isEmpty() ? "missing" : (String) shape.getFirst();
    }

    private List<Object> definitions(String table, char kind) throws SQLException {
        return db.column("select pg_get_constraintdef(oid) from pg_constraint where conrelid = ?::regclass and contype = ?::\"char\" order by conname",
                table, String.valueOf(kind));
    }

    @Test
    @DisplayName("0034-G2-01 时刻变体形态：两张表 effective_from_at 为 timestamptz NOT NULL（非 date、非可空），effective_to_at 为可空 timestamptz")
    void bothTablesHaveTimestamptzPeriodColumns() throws SQLException {
        for (String table : MOMENT_TABLES) {
            assertThat(columnShape(table, "effective_from_at")).as(table).isEqualTo("timestamp with time zone NOT NULL");
            assertThat(columnShape(table, "effective_to_at")).as(table).isEqualTo("timestamp with time zone NULL");
            assertThat(columnShape(table, "effective_from_date")).as(table + " has no date variant").isEqualTo("missing");
        }
    }

    @Test
    @DisplayName("0034-G2-01 时刻变体形态：两张表各有 tstzrange 半开区间的 EXCLUDE USING gist、>= 区间 CHECK 与 isfinite CHECK")
    void bothTablesHaveExclusionPeriodAndFiniteChecks() throws SQLException {
        for (String table : MOMENT_TABLES) {
            List<Object> exclusions = definitions(table, 'x');
            assertThat(exclusions).as(table).isNotEmpty()
                    .allSatisfy(d -> assertThat((String) d).startsWith("EXCLUDE USING gist").contains("tstzrange(effective_from_at, effective_to_at, '[)'::text) WITH &&"));
            List<Object> checks = definitions(table, 'c');
            assertThat(checks).as(table + " period check").anyMatch(d -> ((String) d).equals("CHECK (((effective_to_at IS NULL) OR (effective_to_at >= effective_from_at)))"));
            assertThat(checks).as(table + " finite check")
                    .anyMatch(d -> ((String) d).equals("CHECK ((isfinite(effective_from_at) AND ((effective_to_at IS NULL) OR isfinite(effective_to_at))))"));
        }
        assertThat(definitions("platform_identity.account_role_grants", 'x')).hasSize(1).first().asString().contains("user_id WITH =, role_id WITH =");
        assertThat(definitions("platform_identity.role_versions", 'x')).hasSize(2);
    }

    @Test
    @DisplayName("0034-G2-01 行为：角色版本 effective_from_at 为空被 23502 拒；起止为 infinity 被 ck_role_versions_finite（23514）拒；倒挂被 ck_role_versions_period 拒；零长区间通过")
    void roleVersionPeriodsAreFiniteAndNotInverted() throws SQLException {
        UUID role = db.role();
        String insert = AuthorizationHistoryRows.VERSION_INSERT;
        String codes = "{platform.identity:role:read}";

        db.refusedAsNull("effective_from_at", insert, UUID.randomUUID(), role, 1, null, null, AuthorizationHistoryRows.uniqueName(), codes, UUID.randomUUID(), T0, T0);
        db.refusedByConstraint(CHECK_VIOLATION, "ck_role_versions_finite", insert, UUID.randomUUID(), role, 1, "-infinity", null,
                AuthorizationHistoryRows.uniqueName(), codes, UUID.randomUUID(), T0, T0);
        db.refusedByConstraint(CHECK_VIOLATION, "ck_role_versions_finite", insert, UUID.randomUUID(), role, 1, T0, "infinity",
                AuthorizationHistoryRows.uniqueName(), codes, UUID.randomUUID(), T0, T0);
        db.refusedByConstraint(CHECK_VIOLATION, "ck_role_versions_period", insert, UUID.randomUUID(), role, 1, T1, T0,
                AuthorizationHistoryRows.uniqueName(), codes, UUID.randomUUID(), T0, T0);
        db.accepted(insert, UUID.randomUUID(), role, 1, T1, T1, AuthorizationHistoryRows.uniqueName(), codes, UUID.randomUUID(), T1, T1);
    }

    @Test
    @DisplayName("0034-G2-01 行为：同一角色区间重叠的第二个版本被 ex_role_versions_role_id_period（23P01）拒；首尾相接与零长区间通过")
    void roleVersionsOfOneRoleDoNotOverlap() throws SQLException {
        UUID role = db.role();
        db.replacedVersion(role, 1, T0, T2);
        String insert = AuthorizationHistoryRows.VERSION_INSERT;
        String codes = "{platform.identity:role:read}";

        db.refusedByConstraint(EXCLUSION_VIOLATION, "ex_role_versions_role_id_period", insert, UUID.randomUUID(), role, 2, T1, null,
                AuthorizationHistoryRows.uniqueName(), codes, UUID.randomUUID(), T1, T1);
        db.accepted(insert, UUID.randomUUID(), role, 2, T1, T1, AuthorizationHistoryRows.uniqueName(), codes, UUID.randomUUID(), T1, T1);
        db.openVersion(role, 2, T2);
        db.refusedByConstraint(EXCLUSION_VIOLATION, "ex_role_versions_role_id_period", insert, UUID.randomUUID(), role, 3, T3, null,
                AuthorizationHistoryRows.uniqueName(), codes, UUID.randomUUID(), T3, T3);
    }

    @Test
    @DisplayName("0034-G2-01 行为：两个角色在重叠时段用不区分大小写相同的名称被 ex_role_versions_role_name_period（23P01）拒；时段不重叠时可重名")
    void roleNamesAreUniqueAtEachMoment() throws SQLException {
        UUID first = db.role();
        UUID second = db.role();
        String name = AuthorizationHistoryRows.uniqueName();
        String insert = AuthorizationHistoryRows.VERSION_INSERT;
        String codes = "{platform.identity:role:read}";
        db.setup(insert, UUID.randomUUID(), first, 1, T0, T2, name, codes, UUID.randomUUID(), T0, T2);

        db.refusedByConstraint(EXCLUSION_VIOLATION, "ex_role_versions_role_name_period", insert, UUID.randomUUID(), second, 1, T1, null,
                name.toUpperCase(java.util.Locale.ROOT), codes, UUID.randomUUID(), T1, T1);
        db.accepted(insert, UUID.randomUUID(), second, 1, T2, null, name.toUpperCase(java.util.Locale.ROOT), codes, UUID.randomUUID(), T2, T2);
    }

    @Test
    @DisplayName("0034-G2-01 行为：授予 effective_from_at 为空被 23502 拒；infinity 被 ck_account_role_grants_finite 拒；倒挂被 ck_account_role_grants_period 拒；同人同角色重叠被 ex_account_role_grants_user_id_role_id_period（23P01）拒；首尾相接、零长、另一角色通过")
    void grantPeriodsAreFiniteNotInvertedAndDoNotOverlap() throws SQLException {
        UUID role = db.role();
        db.openVersion(role, 1, T0);
        UUID otherRole = db.role();
        db.openVersion(otherRole, 1, T0);
        UUID account = db.account();
        String insert = AuthorizationHistoryRows.GRANT_INSERT;
        String revoked = "OPERATIONS";

        db.refusedAsNull("effective_from_at", insert, UUID.randomUUID(), account, role, null, null, UUID.randomUUID(), null, null, T0, T0);
        db.refusedByConstraint(CHECK_VIOLATION, "ck_account_role_grants_finite", insert, UUID.randomUUID(), account, role, "-infinity", null,
                UUID.randomUUID(), null, null, T0, T0);
        db.refusedByConstraint(CHECK_VIOLATION, "ck_account_role_grants_finite", insert, UUID.randomUUID(), account, role, T0, "infinity",
                UUID.randomUUID(), revoked, UUID.randomUUID(), T0, T0);
        db.refusedByConstraint(CHECK_VIOLATION, "ck_account_role_grants_period", insert, UUID.randomUUID(), account, role, T1, T0,
                UUID.randomUUID(), revoked, UUID.randomUUID(), T1, T1);

        db.revokedGrant(account, role, T0, T2);
        db.refusedByConstraint(EXCLUSION_VIOLATION, "ex_account_role_grants_user_id_role_id_period", insert, UUID.randomUUID(), account, role, T1, null,
                UUID.randomUUID(), null, null, T1, T1);
        db.accepted(insert, UUID.randomUUID(), account, role, T1, T1, UUID.randomUUID(), revoked, UUID.randomUUID(), T1, T1);
        db.accepted(insert, UUID.randomUUID(), account, otherRole, T1, null, UUID.randomUUID(), null, null, T1, T1);
        db.openGrant(account, role, T2);
        db.refusedByConstraint(EXCLUSION_VIOLATION, "ex_account_role_grants_user_id_role_id_period", insert, UUID.randomUUID(), account, role, T3, null,
                UUID.randomUUID(), null, null, T3, T3);
        assertThat(db.count("select count(*) from platform_identity.account_role_grants where user_id = ? and role_id = ? and effective_to_at is null", account, role))
                .as("at most one current grant per person and role").isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("0034-G2-01 两张表在数据访问注册表登记为 DIMENSION_VERSIONED / APPEND_ONLY；§4b 撤销清单含这两张表，且与注册表全部 DIMENSION_VERSIONED 表一致")
    void registryAndRevokeListAgree() throws IOException {
        Map<String, Map<String, Object>> tables;
        try (InputStream in = Files.newInputStream(REGISTRY)) {
            tables = (Map<String, Map<String, Object>>) new Yaml().<Map<String, Object>>load(in).get("tables");
        }
        Set<String> versioned = new TreeSet<>();
        tables.forEach((table, entry) -> {
            if ("DIMENSION_VERSIONED".equals(String.valueOf(entry.get("role")))) {
                versioned.add(table);
            }
        });
        for (String table : MOMENT_TABLES) {
            assertThat(tables).as("registry entry " + table).containsKey(table);
            assertThat(tables.get(table)).as(table).containsEntry("role", "DIMENSION_VERSIONED").containsEntry("mutability", "APPEND_ONLY");
        }

        String script = Files.readString(DATABASE_SCRIPT);
        int section = script.indexOf("-- §4b");
        assertThat(section).as("one_db_roles_schemas.sql has section 4b").isPositive();
        int revoke = script.indexOf("REVOKE DELETE, TRUNCATE ON", section);
        assertThat(revoke).as("section 4b revokes DELETE and TRUNCATE").isPositive();
        String list = script.substring(script.lastIndexOf("FOREACH t IN ARRAY ARRAY[", revoke), revoke);
        Set<String> revoked = new TreeSet<>();
        Matcher name = Pattern.compile("'([a-z_]+\\.[a-z_]+)'").matcher(list);
        while (name.find()) {
            revoked.add(name.group(1));
        }

        assertThat(revoked).as("section 4b revoke list").containsAll(MOMENT_TABLES).isEqualTo(versioned);
    }
}
