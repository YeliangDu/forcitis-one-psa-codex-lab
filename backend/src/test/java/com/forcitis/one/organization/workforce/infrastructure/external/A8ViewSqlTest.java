package com.forcitis.one.organization.workforce.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The SQL sent to A8 is a set of constants that can be tested on its own: only SELECTs, only the whitelisted views and
 * columns, one shared scope for bulk and single lookup, and no A8-instance ids hard-coded in SQL.
 */
class A8ViewSqlTest {

    static final List<String> STATEMENTS = List.of(A8ViewSql.ORG_UNITS, A8ViewSql.RELATIONSHIPS, A8ViewSql.POSITIONS,
            A8ViewSql.MEMBERS, A8ViewSql.MEMBERS_BY_WECOM_USER_ID, A8ViewSql.COUNT_RELATIONSHIPS_OF_TYPE,
            A8ViewSql.COUNT_DELIVERY_MARKED, A8ViewSql.COUNT_FEE_EARNER_MARKED);

    @Test
    @DisplayName("§9a every statement is a plain SELECT: no write, no EXEC, no cross-database or linked-server access")
    void onlySelects() {
        for (String sql : STATEMENTS) {
            assertThat(sql).startsWith("SELECT ");
            assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContainPattern("\\b(INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE|DROP|ALTER|CREATE|TRUNCATE|GRANT)\\b")
                    .doesNotContain("OPENQUERY").doesNotContain("OPENROWSET").doesNotContain(";");
        }
    }

    @Test
    @DisplayName("§9a statements read only the four whitelisted views and only whitelisted columns of them")
    void onlyWhitelistedViewsAndColumns() {
        Pattern from = Pattern.compile("FROM\\s+" + A8ViewSql.SCHEMA + "\\.(\\w+)");
        for (String sql : STATEMENTS) {
            Matcher m = from.matcher(sql);
            assertThat(m.find()).as(sql).isTrue();
            String view = m.group(1);
            assertThat(A8ViewSql.VIEW_COLUMNS).containsKey(view);
            assertThat(m.find()).as("one view per statement: " + sql).isFalse();
            List<String> whitelist = A8ViewSql.VIEW_COLUMNS.get(view);
            String selected = sql.substring("SELECT ".length(), sql.indexOf(" FROM "));
            if (!selected.equals("COUNT(*)")) {
                for (String column : selected.split(",\\s*")) {
                    assertThat(whitelist).as("column %s of %s", column, view).contains(column.trim());
                }
            }
            Matcher conditions = Pattern.compile("\\b([A-Z][A-Z_]+)\\s*(?:=|<>|\\bIN\\b|\\bLIKE\\b|\\bIS\\b)").matcher(sql.substring(sql.indexOf(" FROM ")));
            while (conditions.find()) {
                String column = conditions.group(1);
                if (!List.of("FROM", "WHERE", "AND", "OR", "NOT").contains(column)) {
                    assertThat(whitelist).as("filter column %s in %s", column, sql).contains(column);
                }
            }
        }
        assertThat(A8ViewSql.VIEW_COLUMNS).containsOnlyKeys("v_org_unit", "v_org_relationship", "v_org_post", "v_org_member");
    }

    @Test
    @DisplayName("§9a the catalog is read only through INFORMATION_SCHEMA.COLUMNS of the view schema")
    void catalogRead() {
        assertThat(A8ViewSql.VIEW_COLUMNS_IN_SCHEMA).isEqualTo(
                "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = :schema");
    }

    @Test
    @DisplayName("RULE-361 bulk sync and single lookup share the scope: internal, with code, org unit below the root PATH prefix")
    void bulkAndSingleLookupShareTheScope() {
        assertThat(A8ViewSql.MEMBERS).contains("IS_INTERNAL = 1").contains("CODE IS NOT NULL").contains("CODE <> ''")
                .contains("ORG_UNIT_ID IS NOT NULL").contains("ORG_UNIT_PATH LIKE '00000001%'");
        assertThat(A8ViewSql.MEMBERS_BY_WECOM_USER_ID).startsWith(A8ViewSql.MEMBERS).endsWith("AND WE_COM_ID = :wecomUserId");
        assertThat(A8ViewSql.ORG_UNITS).contains("PATH LIKE '00000001%'");
        assertThat(A8ViewSql.ROOT_PATH_PREFIX).isEqualTo("00000001%");
    }

    @Test
    @DisplayName("RULE-356 relationship type ids and enum ids are parameters or configuration, never literals in SQL")
    void noInstanceIdsInSql() {
        for (String sql : STATEMENTS) {
            assertThat(sql).doesNotContainPattern("-?\\d{9,}");
        }
        assertThat(A8ViewSql.RELATIONSHIPS).contains(":leaderTypeId").contains(":managerTypeId");
    }

    @Test
    @DisplayName("§9a no birthday, bank card, salary or money column is in any view")
    void noSensitiveColumns() {
        List<String> all = new ArrayList<>();
        for (Map.Entry<String, List<String>> view : A8ViewSql.VIEW_COLUMNS.entrySet()) {
            all.addAll(view.getValue());
        }
        assertThat(all).noneMatch(c -> c.matches("(?i).*(BIRTH|BANK|CARD|SALARY|WAGE|AMOUNT|PRICE|COST|MONEY|ID_NUMBER|PASSPORT|PASSWORD|MOBILE|PHONE).*"));
        assertThat(all).doesNotContain("EXT_ATTR_21");
    }
}
