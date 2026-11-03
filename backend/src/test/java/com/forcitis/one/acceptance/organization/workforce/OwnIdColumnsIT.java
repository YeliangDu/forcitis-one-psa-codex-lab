package com.forcitis.one.acceptance.organization.workforce;

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
 * R-1b P3b (ADR-0033 section 1; DDL-01, DDL-06; card section 3.1 and C10) on the catalogue of the migrated database, written like
 * UserIdColumnsIT: every column that holds an employee, org unit or position id is a uuid in every schema; the eight workforce tables
 * have a one-column uuid primary key under the name they always had; the version and attribution period tables keep their natural key
 * as a unique constraint; the new foreign key indexes exist; the held separations keep their two-column key until split b.
 */
class OwnIdColumnsIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(OwnIdColumnsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String WORKFORCE = "organization_workforce";

    /** Proposed names (card section 11, one-glossary 2026-10-15): the surrogate keys of the version and attribution period tables. */
    static final String ORG_UNIT_VERSION_ID = "org_unit_version_id";
    static final String ASSIGNMENT_VERSION_ID = "assignment_version_id";
    static final String ORG_UNIT_LEGAL_ENTITY_ID = "org_unit_legal_entity_id";
    static final String EMPLOYEE_LEGAL_ENTITY_ID = "employee_legal_entity_id";

    /** schema, table, column: card section 3.1 and section 7 "bigint → uuid". */
    static final List<String[]> OWN_ID_COLUMNS = List.of(
            new String[] {WORKFORCE, "positions", "id"},
            new String[] {WORKFORCE, "org_units", "id"},
            new String[] {WORKFORCE, "org_units", "parent_org_unit_id"},
            new String[] {WORKFORCE, "org_units", "head_employee_id"},
            new String[] {WORKFORCE, "org_units", "regional_receivables_manager_id"},
            new String[] {WORKFORCE, "employees", "id"},
            new String[] {WORKFORCE, "assignments", "employee_id"},
            new String[] {WORKFORCE, "assignments", "org_unit_id"},
            new String[] {WORKFORCE, "assignments", "position_id"},
            new String[] {WORKFORCE, "assignments", "reports_to_employee_id"},
            new String[] {WORKFORCE, "org_unit_versions", "org_unit_id"},
            new String[] {WORKFORCE, "org_unit_versions", "parent_org_unit_id"},
            new String[] {WORKFORCE, "org_unit_versions", "head_employee_id"},
            new String[] {WORKFORCE, "org_unit_versions", "regional_receivables_manager_id"},
            new String[] {WORKFORCE, "assignment_versions", "employee_id"},
            new String[] {WORKFORCE, "assignment_versions", "org_unit_id"},
            new String[] {WORKFORCE, "assignment_versions", "position_id"},
            new String[] {WORKFORCE, "assignment_versions", "reports_to_employee_id"},
            new String[] {WORKFORCE, "org_unit_legal_entities", "org_unit_id"},
            new String[] {WORKFORCE, "employee_legal_entities", "employee_id"},
            new String[] {WORKFORCE, "legal_entity_attributions", "org_unit_id"},
            new String[] {WORKFORCE, "legal_entity_attributions", "employee_id"},
            new String[] {WORKFORCE, "legal_entity_attribution_corrections", "org_unit_id"},
            new String[] {WORKFORCE, "legal_entity_attribution_corrections", "employee_id"},
            new String[] {"platform_identity", "user_accounts", "employee_id"},
            new String[] {"cost_timesheet", "timesheets", "employee_id"},
            new String[] {"cost_timesheet", "timesheets", "project_manager_id"},
            new String[] {"cost_timesheet", "timesheets", "org_unit_id"},
            new String[] {"platform_approval", "approval_requests", "initiator_id"},
            new String[] {"platform_approval", "start_rounds", "initiator_id"});

    /** table, primary key name, its one column (C10: the names stay, also the default ones). */
    static final List<String[]> PRIMARY_KEYS = List.of(
            new String[] {"positions", "positions_pkey", "id"},
            new String[] {"org_units", "org_units_pkey", "id"},
            new String[] {"employees", "employees_pkey", "id"},
            new String[] {"assignments", "assignments_pkey", "employee_id"},
            new String[] {"org_unit_versions", "pk_org_unit_versions", ORG_UNIT_VERSION_ID},
            new String[] {"assignment_versions", "pk_assignment_versions", ASSIGNMENT_VERSION_ID},
            new String[] {"org_unit_legal_entities", "pk_org_unit_legal_entities", ORG_UNIT_LEGAL_ENTITY_ID},
            new String[] {"employee_legal_entities", "pk_employee_legal_entities", EMPLOYEE_LEGAL_ENTITY_ID});

    /** table, unique constraint, its columns in order (DDL-01: the natural key stays unique). */
    static final List<String[]> NATURAL_KEYS = List.of(
            new String[] {"org_unit_versions", "uk_org_unit_versions_revision", "org_unit_id,effective_from_date,revision_number"},
            new String[] {"assignment_versions", "uk_assignment_versions_revision", "employee_id,effective_from_date,revision_number"});

    /**
     * table, partial unique index, its columns in order: natural keys of attribution period tables that took the W2 common set (W2 design
     * section 3.6, #892, #904): no revision number, so the key is unique among the rows not superseded.
     */
    static final List<String[]> CURRENT_ROW_NATURAL_KEYS = List.of(
            new String[] {"employee_legal_entities", "uk_employee_legal_entities_effective_from", "employee_id,effective_from_date"},
            new String[] {"org_unit_legal_entities", "uk_org_unit_legal_entities_effective_from", "org_unit_id,effective_from_date"});

    /** table, index, leading column (DDL-06). */
    static final List<String[]> FOREIGN_KEY_INDEXES = List.of(
            new String[] {"org_units", "ix_org_units_regional_receivables_manager_id", "regional_receivables_manager_id"},
            new String[] {"assignments", "ix_assignments_position_id", "position_id"},
            new String[] {"org_unit_legal_entities", "ix_org_unit_legal_entities_legal_entity_id", "legal_entity_id"},
            new String[] {"employee_legal_entities", "ix_employee_legal_entities_legal_entity_id", "legal_entity_id"},
            new String[] {"assignment_versions", "ix_assignment_versions_org_unit_id", "org_unit_id"});

    static String typeOf(Connection catalogue, String schema, String table, String column) throws SQLException {
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

    /** The columns of a constraint of a workforce table in key order, or null when there is no such constraint of that kind. */
    static String constraintColumns(Connection catalogue, String table, String constraint, String kind) throws SQLException {
        try (PreparedStatement select = catalogue.prepareStatement("""
                select string_agg(a.attname, ',' order by k.ordinality)
                  from pg_constraint c
                  join pg_class t on t.oid = c.conrelid
                  join pg_namespace n on n.oid = t.relnamespace
                  cross join lateral unnest(c.conkey) with ordinality as k(attnum, ordinality)
                  join pg_attribute a on a.attrelid = t.oid and a.attnum = k.attnum
                 where n.nspname = ? and t.relname = ? and c.conname = ? and c.contype = ?
                 group by c.oid""")) {
            select.setString(1, WORKFORCE);
            select.setString(2, table);
            select.setString(3, constraint);
            select.setString(4, kind);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 1 项：存员工、部门、岗位 ID 的列在 workforce、identity、timesheet、approval 中一律为 uuid")
    void everyOwnIdColumnIsUuid() throws Exception {
        List<String> wrong = new ArrayList<>();
        try (Connection catalogue = schemaConnection("public")) {
            for (String[] column : OWN_ID_COLUMNS) {
                String type = typeOf(catalogue, column[0], column[1], column[2]);
                if (!"uuid".equals(type)) {
                    wrong.add(column[0] + "." + column[1] + "." + column[2] + " is " + (type == null ? "missing" : type));
                }
            }
        }

        assertThat(wrong).as("employee, org unit and position id columns that are not uuid").isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 1 项 / C10：workforce 8 张表的主键是单列 uuid，约束名不变（行内默认名与 pk_ 名都照旧）")
    void theEightTablesHaveAOneColumnUuidKeyUnderTheirOldName() throws Exception {
        List<String> wrong = new ArrayList<>();
        try (Connection catalogue = schemaConnection("public")) {
            for (String[] key : PRIMARY_KEYS) {
                String columns = constraintColumns(catalogue, key[0], key[1], "p");
                String type = typeOf(catalogue, WORKFORCE, key[0], key[2]);
                if (!key[2].equals(columns) || !"uuid".equals(type)) {
                    wrong.add(key[0] + ": primary key " + key[1] + " = " + columns + ", " + key[2] + " is " + type);
                }
            }
        }

        assertThat(wrong).as("primary keys that are not the one uuid column under the old name").isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b DDL-01：版本表与法人归属期间表的原主键列改为自然键唯一约束（uk_*_revision、uk_*_effective_from）")
    void theNaturalKeysStayUnique() throws Exception {
        List<String> wrong = new ArrayList<>();
        try (Connection catalogue = schemaConnection("public")) {
            for (String[] key : NATURAL_KEYS) {
                String columns = constraintColumns(catalogue, key[0], key[1], "u");
                if (!key[2].equals(columns)) {
                    wrong.add(key[0] + ": " + key[1] + " = " + columns);
                }
            }
        }

        assertThat(wrong).as("natural keys that are not unique constraints over the old key columns").isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b DDL-01 与 W2 第 3.6 节（#892、#904）：员工与组织单元法人归属期间表的自然键改为只管未作废行的部分唯一索引 uk_*_effective_from (主体, effective_from_date) WHERE superseded_at IS NULL")
    void theNaturalKeysOfVersionedPeriodsStayUniqueAmongCurrentRows() throws Exception {
        List<String> wrong = new ArrayList<>();
        try (Connection catalogue = schemaConnection("public")) {
            for (String[] key : CURRENT_ROW_NATURAL_KEYS) {
                try (PreparedStatement select = catalogue.prepareStatement("""
                        select string_agg(a.attname, ',' order by k.ordinality), bool_and(i.indisunique), max(pg_get_expr(i.indpred, i.indrelid))
                          from pg_index i
                          join pg_class x on x.oid = i.indexrelid
                          join pg_class t on t.oid = i.indrelid
                          join pg_namespace n on n.oid = t.relnamespace
                          cross join lateral unnest(i.indkey) with ordinality as k(attnum, ordinality)
                          join pg_attribute a on a.attrelid = t.oid and a.attnum = k.attnum
                         where n.nspname = ? and t.relname = ? and x.relname = ?""")) {
                    select.setString(1, WORKFORCE);
                    select.setString(2, key[0]);
                    select.setString(3, key[1]);
                    try (ResultSet rs = select.executeQuery()) {
                        rs.next();
                        String columns = rs.getString(1);
                        boolean unique = rs.getBoolean(2);
                        String predicate = rs.getString(3);
                        if (!key[2].equals(columns) || !unique || predicate == null || !predicate.replaceAll("[()\\s]", "").equals("superseded_atISNULL")) {
                            wrong.add(key[0] + ": " + key[1] + " = " + columns + " unique=" + unique + " where " + predicate);
                        }
                    }
                }
            }
        }

        assertThat(wrong).as("natural keys of versioned period tables that are not unique among the rows not superseded").isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b DDL-06：新增 5 个外键索引（区域应收负责人、主岗岗位、两张归属期间表的法人、任职版本的部门）")
    void theForeignKeyIndexesExist() throws Exception {
        List<String> missing = new ArrayList<>();
        try (Connection catalogue = schemaConnection("public")) {
            for (String[] index : FOREIGN_KEY_INDEXES) {
                try (PreparedStatement select = catalogue.prepareStatement("""
                        select a.attname from pg_index i
                          join pg_class x on x.oid = i.indexrelid
                          join pg_class t on t.oid = i.indrelid
                          join pg_namespace n on n.oid = t.relnamespace
                          join pg_attribute a on a.attrelid = t.oid and a.attnum = i.indkey[0]
                         where n.nspname = ? and t.relname = ? and x.relname = ?""")) {
                    select.setString(1, WORKFORCE);
                    select.setString(2, index[0]);
                    select.setString(3, index[1]);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next() || !index[2].equals(rs.getString(1))) {
                            missing.add(index[0] + "." + index[1] + " on " + index[2]);
                        }
                    }
                }
            }
        }

        assertThat(missing).as("foreign key indexes of DDL-06").isEmpty();
    }
}
