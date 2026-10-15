package com.forcitis.one.platform.integration.infrastructure.seeyon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeeyonViewSqlTest {

    static final List<String> STATEMENTS = List.of(SeeyonViewSql.ORG_UNITS, SeeyonViewSql.RELATIONSHIPS, SeeyonViewSql.POSITIONS,
            SeeyonViewSql.MEMBERS, SeeyonViewSql.MEMBERS_BY_WECOM_USER_ID, SeeyonViewSql.MEMBERS_BY_LOGIN_NAME, SeeyonViewSql.COUNT_RELATIONSHIPS_OF_TYPE,
            SeeyonViewSql.COUNT_DELIVERY_MARKED, SeeyonViewSql.COUNT_FEE_EARNER_MARKED);

    @Test
    @DisplayName("§9a every statement is a plain SELECT: no write, no EXEC, no cross-database or linked-server access")
    void onlySelects() {
        for (String sql : STATEMENTS) {
            assertThat(sql).startsWith("SELECT ");
            assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContainPattern("\\b(INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE|DROP|ALTER|CREATE|TRUNCATE|GRANT)\\b")
                    .doesNotContain("OPENQUERY").doesNotContain("OPENROWSET").doesNotContain(";");
        }
    }

    static final Pattern OBJECT = Pattern.compile("\\b(?:FROM|JOIN)\\s+(\\w+)\\.(\\w+)(?:\\s+(?!WHERE\\b|LEFT\\b|INNER\\b|JOIN\\b|ON\\b)([A-Z])\\b)?");
    static final Pattern QUALIFIED_COLUMN = Pattern.compile("\\b([A-Z])\\.([A-Z][A-Z_0-9]*)");

    /** Alias (empty for a single unaliased object) to the registered columns of the object it stands for. */
    static Map<String, List<String>> readObjects(String sql) {
        Map<String, List<String>> byAlias = new LinkedHashMap<>();
        int objects = 0;
        Matcher m = OBJECT.matcher(sql);
        while (m.find()) {
            objects++;
            String schema = m.group(1);
            String name = m.group(2);
            List<String> columns = schema.equals(SeeyonViewSql.SCHEMA) ? SeeyonViewSql.VIEW_COLUMNS.get(name)
                    : schema.equals(SeeyonViewSql.BASE_SCHEMA) ? SeeyonViewSql.BASE_TABLE_COLUMNS.get(name) : null;
            assertThat(columns).as("%s.%s in %s is a registered view or dbo table", schema, name, sql).isNotNull();
            byAlias.put(m.group(3) == null ? "" : m.group(3), columns);
        }
        assertThat(objects).as("objects of %s", sql).isPositive();
        assertThat(sql.split("\\b(?:FROM|JOIN)\\b", -1).length - 1).as("every FROM and JOIN names schema.object: %s", sql).isEqualTo(objects);
        assertThat(objects == 1 || (byAlias.size() == objects && !byAlias.containsKey(""))).as("a statement over several objects aliases every one: %s", sql)
                .isTrue();
        return byAlias;
    }

    static final Set<String> KEYWORDS = Set.of("FROM", "WHERE", "AND", "OR", "NOT", "LEFT", "INNER", "JOIN", "ON", "IS", "NULL", "IN", "LIKE");

    /** Fails unless the statement reads only registered objects and columns; over several objects every column is aliased. */
    static void checkStatement(String sql) {
        Map<String, List<String>> objects = readObjects(sql);
        String selected = sql.substring("SELECT ".length(), sql.indexOf(" FROM "));
        if (!selected.equals("COUNT(*)")) {
            for (String item : selected.split(",\\s*")) {
                Matcher q = Pattern.compile("(?:([A-Z])\\.)?(\\w+)").matcher(item.trim());
                assertThat(q.matches()).as("plain column %s in %s", item, sql).isTrue();
                String alias = q.group(1) == null ? "" : q.group(1);
                assertThat(objects).as("alias of %s in %s", item, sql).containsKey(alias);
                assertThat(objects.get(alias)).as("column %s in %s", item, sql).contains(q.group(2));
            }
        }
        String rest = sql.substring(sql.indexOf(" FROM "));
        if (objects.containsKey("")) {
            Matcher conditions = Pattern.compile("\\b([A-Z][A-Z_]+)\\s*(?:=|<>|\\bIN\\b|\\bLIKE\\b|\\bIS\\b)").matcher(rest);
            while (conditions.find()) {
                String column = conditions.group(1);
                if (!List.of("FROM", "WHERE", "AND", "OR", "NOT").contains(column)) {
                    assertThat(objects.get("")).as("filter column %s in %s", column, sql).contains(column);
                }
            }
            return;
        }
        Matcher qualified = QUALIFIED_COLUMN.matcher(rest);
        while (qualified.find()) {
            assertThat(objects).as("alias %s in %s", qualified.group(1), sql).containsKey(qualified.group(1));
            assertThat(objects.get(qualified.group(1))).as("column %s in %s", qualified.group(), sql).contains(qualified.group(2));
        }
        String unqualified = QUALIFIED_COLUMN.matcher(OBJECT.matcher(rest.replaceAll("'[^']*'|:\\w+", " ")).replaceAll(" ")).replaceAll(" ");
        Matcher bare = Pattern.compile("\\b[A-Za-z_]\\w*\\b").matcher(unqualified);
        while (bare.find()) {
            assertThat(KEYWORDS).as("unaliased column %s in a statement over several objects: %s", bare.group(), sql).contains(bare.group());
        }
        // A base table is joined by its key only: a filter on it in SQL would drop a bad row instead of failing the run on it.
        for (String alias : objects.keySet()) {
            if (sql.matches(".*\\bJOIN\\s+" + SeeyonViewSql.BASE_SCHEMA + "\\.\\w+\\s+" + alias + "\\b.*")) {
                String withoutKey = rest.replaceFirst("\\bON " + alias + "\\.ID = [A-Z]\\.ID\\b", " ");
                assertThat(withoutKey).as("base table %s is joined by ID only and never filtered: %s", alias, sql).doesNotContainPattern("\\b" + alias + "\\.");
            }
        }
    }

    @Test
    @DisplayName("R-1b-0 statements read only objects of the external read register (the four views, dbo base tables) and only their registered columns")
    void onlyRegisteredObjectsAndColumns() {
        STATEMENTS.forEach(SeeyonViewSqlTest::checkStatement);
        assertThat(SeeyonViewSql.VIEW_COLUMNS).containsOnlyKeys("v_org_unit", "v_org_relationship", "v_org_post", "v_org_member");
        assertThat(SeeyonViewSql.BASE_TABLE_COLUMNS).containsOnlyKeys("ORG_UNIT");
    }

    @Test
    @DisplayName("R-1b-0 the statement check rejects an unaliased column over several objects and a filter on the joined base table")
    void statementCheckIsNotVacuous() {
        String unit = SeeyonViewSql.ORG_UNITS;
        String where = " WHERE V.PATH LIKE :rootPathPrefix";
        assertThatThrownBy(() -> checkStatement(unit + " AND IS_DELETED = 0")).hasMessageContaining("unaliased column IS_DELETED");
        assertThatThrownBy(() -> checkStatement(unit.replace(where, " WHERE PATH LIKE :rootPathPrefix"))).hasMessageContaining("unaliased column PATH");
        assertThatThrownBy(() -> checkStatement(unit + " AND B.TYPE = 'Department'")).hasMessageContaining("never filtered");
        assertThatThrownBy(() -> checkStatement(unit.replace(where, " AND B.TYPE = 'Department'" + where))).hasMessageContaining("never filtered");
        assertThatThrownBy(() -> checkStatement(unit.replace("ON B.ID = V.ID", "ON B.CODE = V.CODE"))).hasMessageContaining("CODE");
    }

    @Test
    @DisplayName("R-1b-0 the org unit statement keeps the view part of the pure view statement and only adds TYPE from dbo.ORG_UNIT by primary key")
    void orgUnitsJoinTheBaseTableByPrimaryKey() {
        assertThat(SeeyonViewSql.ORG_UNITS).contains("FROM forcitis_one.v_org_unit V LEFT JOIN dbo.ORG_UNIT B ON B.ID = V.ID")
                .endsWith(" WHERE V.PATH LIKE :rootPathPrefix");
        assertThat(SeeyonViewSql.ORG_UNITS.substring(0, SeeyonViewSql.ORG_UNITS.indexOf(" FROM "))).isEqualTo("SELECT "
                + String.join(", ", SeeyonViewSql.VIEW_COLUMNS.get("v_org_unit").stream().map(c -> "V." + c).toList()) + ", B.TYPE");
        assertThat(SeeyonViewSql.ORG_UNITS).as("TYPE is read, never compared in SQL: the mapping fails the run on a bad value").isEqualTo(
                "SELECT V.ID, V.CODE, V.NAME, V.SORT_ID, V.PATH, V.IS_DELETED, V.IS_INTERNAL, V.IS_ENABLE, V.CATEGORY, V.DELIVERY_FLAG_ENUM_ID, B.TYPE "
                        + "FROM forcitis_one.v_org_unit V LEFT JOIN dbo.ORG_UNIT B ON B.ID = V.ID WHERE V.PATH LIKE :rootPathPrefix");
        assertThat(SeeyonViewSql.MEMBERS).as("the person statement is unchanged in R-1b-0a").doesNotContain("dbo.");
    }

    @Test
    @DisplayName("R-1b-0 the object check rejects an unregistered table, an unqualified one and an unaliased join")
    void objectCheckIsNotVacuous() {
        assertThatThrownBy(() -> readObjects("SELECT V.ID FROM forcitis_one.v_org_unit V JOIN dbo.ORG_SECRET S ON S.ID = V.ID")).hasMessageContaining("ORG_SECRET");
        assertThatThrownBy(() -> readObjects("SELECT V.ID FROM forcitis_one.v_org_unit V JOIN ORG_UNIT B ON B.ID = V.ID")).hasMessageContaining("every FROM and JOIN");
        assertThatThrownBy(() -> readObjects("SELECT ID FROM forcitis_one.v_org_unit JOIN dbo.ORG_UNIT ON ORG_UNIT.ID = ID")).hasMessageContaining("aliases every one");
    }

    @Test
    @DisplayName("§9a the catalog is read only through INFORMATION_SCHEMA.COLUMNS of the view schema")
    void catalogRead() {
        assertThat(SeeyonViewSql.VIEW_COLUMNS_IN_SCHEMA).isEqualTo(
                "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = :schema");
    }

    @Test
    @DisplayName("RULE-361 bulk sync and single lookup share the scope: internal (a NULL flag counts as internal, D-1a-06), with code, org unit below the root PATH prefix")
    void bulkAndSingleLookupShareTheScope() {
        assertThat(SeeyonViewSql.MEMBERS).contains("(IS_INTERNAL = 1 OR IS_INTERNAL IS NULL)").contains("CODE IS NOT NULL").contains("CODE <> ''")
                .contains("ORG_UNIT_ID IS NOT NULL").contains("ORG_UNIT_PATH LIKE :rootPathPrefix");
        assertThat(SeeyonViewSql.MEMBERS_BY_WECOM_USER_ID).startsWith(SeeyonViewSql.MEMBERS).endsWith("AND WE_COM_ID = :wecomUserId");
        assertThat(SeeyonViewSql.MEMBERS_BY_LOGIN_NAME).startsWith(SeeyonViewSql.MEMBERS).endsWith("AND LOGIN_NAME = :loginName");
        assertThat(SeeyonViewSql.ORG_UNITS).contains("PATH LIKE :rootPathPrefix");
    }

    @Test
    @DisplayName("RULE-356 relationship type ids and enum ids are parameters or configuration, never literals in SQL")
    void noInstanceIdsInSql() {
        for (String sql : STATEMENTS) {
            assertThat(sql).doesNotContainPattern("-?\\d{9,}");
        }
        assertThat(SeeyonViewSql.RELATIONSHIPS).contains(":leaderTypeId").contains(":managerTypeId");
    }

    @Test
    @DisplayName("§9a no birthday, bank card, salary or money column is in any view")
    void noSensitiveColumns() {
        List<String> all = new ArrayList<>();
        for (Map.Entry<String, List<String>> view : SeeyonViewSql.VIEW_COLUMNS.entrySet()) {
            all.addAll(view.getValue());
        }
        assertThat(all).noneMatch(c -> c.matches("(?i).*(BIRTH|BANK|CARD|SALARY|WAGE|AMOUNT|PRICE|COST|MONEY|ID_NUMBER|PASSPORT|PASSWORD|MOBILE|PHONE).*"));
        assertThat(all).doesNotContain("EXT_ATTR_21");
    }
}
