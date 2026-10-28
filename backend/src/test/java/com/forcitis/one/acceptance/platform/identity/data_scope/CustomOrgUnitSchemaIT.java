package com.forcitis.one.acceptance.platform.identity.data_scope;

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
 * F-1 card (#728) section 7.1 and constraint table rows 1, 2, 3 and 8: the two new child tables are exactly what the card declares
 * (columns, uuid primary keys, constraint, index and trigger names, RESTRICT foreign keys to the parents only, no database default on
 * any column so that every moment comes from the injected clock, a comment on the table and on every column) and the guard function
 * exists. Read from the migrated database, not from the SQL text. Written like AuthorizationHistorySchemaIT of ADR-0034-IMPL PR-1.
 */
class CustomOrgUnitSchemaIT {

    private static final String DETAIL = "platform_identity.role_version_custom_org_units";
    private static final String REQUEST_DETAIL = "platform_identity.authorization_request_custom_org_units";

    private CustomOrgUnitRows db;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new CustomOrgUnitRows(getClass());
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
    @DisplayName("F-1 7.1 role_version_custom_org_units 的列、类型、非空与卡逐列一致（含 is_descendants_included boolean NOT NULL），无任何列默认值（时刻由注入时钟写入）")
    void roleVersionDetailColumnsAreTheCardsWithoutDefaults() throws SQLException {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("role_version_custom_org_unit_id", "uuid NOT NULL");
        expected.put("role_version_id", "uuid NOT NULL");
        expected.put("org_unit_id", "uuid NOT NULL");
        expected.put("is_descendants_included", "boolean NOT NULL");
        expected.put("created_at", "timestamp with time zone NOT NULL");
        expected.put("updated_at", "timestamp with time zone NOT NULL");

        assertThat(columns(DETAIL)).containsExactlyEntriesOf(expected);
    }

    @Test
    @DisplayName("F-1 7.1 / 不变量 8 authorization_request_custom_org_units 的列与卡逐列一致，没有「含下级」列（申请中的部门都是仅本部门），无任何列默认值")
    void requestDetailColumnsAreTheCardsAndHaveNoDescendantsColumn() throws SQLException {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("authorization_request_custom_org_unit_id", "uuid NOT NULL");
        expected.put("request_id", "uuid NOT NULL");
        expected.put("org_unit_id", "uuid NOT NULL");
        expected.put("created_at", "timestamp with time zone NOT NULL");
        expected.put("updated_at", "timestamp with time zone NOT NULL");

        assertThat(columns(REQUEST_DETAIL)).containsExactlyEntriesOf(expected);
    }

    @Test
    @DisplayName("F-1 7.1 / 不变量 1 两表的约束名集合与卡一致（pk_、fk_、uk_），主键单列 uuid；不变量 1 的唯一约束是（父 ID，部门 ID）")
    void constraintsAreTheCards() throws SQLException {
        assertThat(names(CONSTRAINTS, DETAIL)).containsExactly("fk_role_version_custom_org_units_role_version_id f", "pk_role_version_custom_org_units p",
                "uk_role_version_custom_org_units_role_version_id_org_unit_id u");
        assertThat(names(CONSTRAINTS, REQUEST_DETAIL)).containsExactly("fk_authorization_request_custom_org_units_request_id f",
                "pk_authorization_request_custom_org_units p", "uk_authorization_request_custom_org_units_request_org_unit u");
        assertThat(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'pk_role_version_custom_org_units'"))
                .containsExactly("PRIMARY KEY (role_version_custom_org_unit_id)");
        assertThat(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'pk_authorization_request_custom_org_units'"))
                .containsExactly("PRIMARY KEY (authorization_request_custom_org_unit_id)");
        assertThat(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'uk_role_version_custom_org_units_role_version_id_org_unit_id'"))
                .containsExactly("UNIQUE (role_version_id, org_unit_id)");
        assertThat(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'uk_authorization_request_custom_org_units_request_org_unit'"))
                .containsExactly("UNIQUE (request_id, org_unit_id)");
    }

    @Test
    @DisplayName("F-1 7.1 / 引用一节 外键只指向父表且 ON DELETE RESTRICT；org_unit_id 是跨 schema 引用，两表都不对它建外键")
    void foreignKeysPointOnlyAtTheParentsAndAreRestrict() throws SQLException {
        assertThat(names(FOREIGN_KEYS, DETAIL)).containsExactly("fk_role_version_custom_org_units_role_version_id -> platform_identity.role_versions r");
        assertThat(names(FOREIGN_KEYS, REQUEST_DETAIL)).containsExactly(
                "fk_authorization_request_custom_org_units_request_id -> platform_identity.authorization_requests r");
    }

    @Test
    @DisplayName("F-1 7.1 两表的索引名集合与卡一致：明细表按部门查（RULE-387 找引用某部门的在用版本）有 ix_ 索引，申请子表只有约束自带索引")
    void indexesAreTheCards() throws SQLException {
        assertThat(names(INDEXES, DETAIL)).containsExactly("ix_role_version_custom_org_units_org_unit_id", "pk_role_version_custom_org_units",
                "uk_role_version_custom_org_units_role_version_id_org_unit_id");
        assertThat(names(INDEXES, REQUEST_DETAIL)).containsExactly("pk_authorization_request_custom_org_units",
                "uk_authorization_request_custom_org_units_request_org_unit");
        assertThat(db.column("select pg_get_indexdef('platform_identity.ix_role_version_custom_org_units_org_unit_id'::regclass)")).containsExactly(
                "CREATE INDEX ix_role_version_custom_org_units_org_unit_id ON platform_identity.role_version_custom_org_units USING btree (org_unit_id)");
    }

    @Test
    @DisplayName("F-1 7.1 / 不变量 2、3、8 触发器与卡一致：每表一个行级 BEFORE INSERT OR DELETE OR UPDATE 与一个语句级 BEFORE TRUNCATE，都执行 guard_custom_org_unit_rows；该函数存在且返回 trigger")
    void triggersAreTheCards() throws SQLException {
        assertThat(names(TRIGGERS, DETAIL)).containsExactly(
                "CREATE TRIGGER tg_role_version_custom_org_units_guard BEFORE INSERT OR DELETE OR UPDATE ON platform_identity.role_version_custom_org_units"
                        + " FOR EACH ROW EXECUTE FUNCTION platform_identity.guard_custom_org_unit_rows()",
                "CREATE TRIGGER tg_role_version_custom_org_units_guard_truncate BEFORE TRUNCATE ON platform_identity.role_version_custom_org_units"
                        + " FOR EACH STATEMENT EXECUTE FUNCTION platform_identity.guard_custom_org_unit_rows()");
        assertThat(names(TRIGGERS, REQUEST_DETAIL)).containsExactly(
                "CREATE TRIGGER tg_authorization_request_custom_org_units_guard BEFORE INSERT OR DELETE OR UPDATE ON platform_identity.authorization_request_custom_org_units"
                        + " FOR EACH ROW EXECUTE FUNCTION platform_identity.guard_custom_org_unit_rows()",
                "CREATE TRIGGER tg_authorization_request_custom_org_units_guard_truncate BEFORE TRUNCATE ON platform_identity.authorization_request_custom_org_units"
                        + " FOR EACH STATEMENT EXECUTE FUNCTION platform_identity.guard_custom_org_unit_rows()");
        assertThat(db.column("""
                select format_type(p.prorettype, null) from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                 where n.nspname = 'platform_identity' and p.proname = 'guard_custom_org_unit_rows'""")).containsExactly("trigger");
    }

    @Test
    @DisplayName("F-1 7.1 两表与表上每一列都有非空白的中文注释（DdlNamingTest 同口径），守卫函数也有注释")
    void theTablesAndEveryColumnAreCommented() throws SQLException {
        for (String table : List.of(DETAIL, REQUEST_DETAIL)) {
            assertThat(db.column("select obj_description(?::regclass, 'pg_class')", table)).as("comment on table " + table).singleElement().asString().isNotBlank();
            assertThat(db.column("""
                    select a.attname from pg_attribute a where a.attrelid = ?::regclass and a.attnum > 0 and not a.attisdropped
                       and coalesce(btrim(col_description(a.attrelid, a.attnum)), '') = ''""", table)).as("columns of " + table + " without a comment").isEmpty();
        }
        assertThat(db.column("""
                select obj_description(p.oid, 'pg_proc') from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                 where n.nspname = 'platform_identity' and p.proname = 'guard_custom_org_unit_rows'""")).singleElement().asString().isNotBlank();
    }
}
