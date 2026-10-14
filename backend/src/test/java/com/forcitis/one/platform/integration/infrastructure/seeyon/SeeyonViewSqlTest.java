package com.forcitis.one.platform.integration.infrastructure.seeyon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Test
    @DisplayName("§9a statements read only the four whitelisted views and only whitelisted columns of them")
    void onlyWhitelistedViewsAndColumns() {
        Pattern from = Pattern.compile("FROM\\s+" + SeeyonViewSql.SCHEMA + "\\.(\\w+)");
        for (String sql : STATEMENTS) {
            Matcher m = from.matcher(sql);
            assertThat(m.find()).as(sql).isTrue();
            String view = m.group(1);
            assertThat(SeeyonViewSql.VIEW_COLUMNS).containsKey(view);
            assertThat(m.find()).as("one view per statement: " + sql).isFalse();
            List<String> whitelist = SeeyonViewSql.VIEW_COLUMNS.get(view);
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
        assertThat(SeeyonViewSql.VIEW_COLUMNS).containsOnlyKeys("v_org_unit", "v_org_relationship", "v_org_post", "v_org_member");
    }

    @Test
    @DisplayName("§9a the catalog is read only through INFORMATION_SCHEMA.COLUMNS of the view schema")
    void catalogRead() {
        assertThat(SeeyonViewSql.VIEW_COLUMNS_IN_SCHEMA).isEqualTo(
                "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = :schema");
    }

    @Test
    @DisplayName("RULE-361 bulk sync and single lookup share the scope: internal, with code, org unit below the root PATH prefix")
    void bulkAndSingleLookupShareTheScope() {
        assertThat(SeeyonViewSql.MEMBERS).contains("IS_INTERNAL = 1").contains("CODE IS NOT NULL").contains("CODE <> ''")
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
