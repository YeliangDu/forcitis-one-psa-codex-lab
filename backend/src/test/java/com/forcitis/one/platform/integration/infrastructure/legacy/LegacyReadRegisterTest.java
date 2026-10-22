package com.forcitis.one.platform.integration.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.guard.ExternalReadRegister;
import com.forcitis.one.platform.integration.guard.ExternalReadRegister.Row;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R-1b-0 C-5: the old system's short-name view as registered equal, column by column, the statements the integration layer sends (ADR-0037 item 6.1). */
class LegacyReadRegisterTest {

    static final String SHORT_NAME_VIEW = "dbo.v_one_legacy_org_short_name";
    static final Pattern STATEMENT = Pattern.compile("(?is)^\\s*SELECT\\s+(.+?)\\s+FROM\\s+(\\S+)(?:\\s+WHERE\\s+(.+?))?(?:\\s+ORDER\\s+BY\\s+(.+?))?\\s*$");
    static final Pattern FILTER_COLUMN = Pattern.compile("(?i)(\\w+)\\s*(?:=|<>|!=|>=|<=|>|<|\\bIN\\b|\\bLIKE\\b|\\bIS\\b)");

    /** Registered columns per old-system object whose purpose is run-time, sorted. */
    static Map<String, TreeSet<String>> registered(List<Row> rows) {
        Map<String, TreeSet<String>> byObject = new TreeMap<>();
        for (Row row : rows) {
            if (!row.database().equals(ExternalReadRegister.A8) && row.purpose().runtime()) {
                byObject.computeIfAbsent(row.object(), k -> new TreeSet<>()).add(row.column());
            }
        }
        return byObject;
    }

    /** The object a statement reads and every column it names: in the select list, in every filter comparison and in the ordering. */
    static Map<String, TreeSet<String>> sent(String... statements) {
        Map<String, TreeSet<String>> byObject = new TreeMap<>();
        for (String statement : statements) {
            Matcher m = STATEMENT.matcher(statement);
            assertThat(m.matches()).as("statement shape: %s", statement).isTrue();
            TreeSet<String> columns = byObject.computeIfAbsent(m.group(2), k -> new TreeSet<>());
            Arrays.stream(m.group(1).split(",")).map(String::trim).forEach(columns::add);
            if (m.group(3) != null) {
                Matcher filter = FILTER_COLUMN.matcher(m.group(3));
                while (filter.find()) {
                    columns.add(filter.group(1));
                }
            }
            if (m.group(4) != null) {
                Arrays.stream(m.group(4).split(",")).map(order -> order.trim().split("\\s+")[0]).forEach(columns::add);
            }
        }
        return byObject;
    }

    static List<String> mismatches(List<Row> rows, String... statements) {
        List<String> problems = new ArrayList<>();
        Map<String, TreeSet<String>> have = registered(rows);
        Map<String, TreeSet<String>> want = sent(statements);
        if (!have.equals(want)) {
            problems.add("registered old-system columns " + have + " differ from the statements' columns " + want);
        }
        return problems;
    }

    @Test
    @DisplayName("R-1b-0 C-5 老系统部门简称视图的登记列与集成层 SQL 常量逐列相等")
    void registeredLegacyColumnsEqualTheStatements() throws IOException {
        List<Row> rows = ExternalReadRegister.rows();

        assertThat(registered(rows)).as("the register holds exactly the short-name view").containsOnlyKeys(SHORT_NAME_VIEW);
        assertThat(mismatches(rows, LegacyOrgNames.shortNameSql(SHORT_NAME_VIEW))).isEmpty();
    }

    @Test
    @DisplayName("R-1b-0 C-5 自检：SQL 多读一列、少读一列、登记多一列、读了未登记的视图都被报出")
    void injectedDriftIsFound() throws IOException {
        List<Row> rows = ExternalReadRegister.rows();
        String names = LegacyOrgNames.shortNameSql(SHORT_NAME_VIEW);

        assertThat(mismatches(rows, names.replace("short_name FROM", "short_name, extra_name FROM"))).as("one more column read").hasSize(1);
        assertThat(mismatches(rows, "SELECT id FROM " + SHORT_NAME_VIEW)).as("one column less read").hasSize(1);
        assertThat(mismatches(rows, names, "SELECT secret_value FROM dbo.other_table")).as("a view nobody registered").hasSize(1);
        assertThat(mismatches(rows, names + " WHERE status = 1")).as("a filter column nobody registered").hasSize(1);
        for (String operator : List.of("> 1", "< 1", "IN (1)", "LIKE 'x'", "IS NULL", "<> 1")) {
            assertThat(mismatches(rows, names + " WHERE status " + operator)).as("a filter column compared with " + operator).hasSize(1);
        }
        assertThat(mismatches(rows, names + " WHERE status = 1 AND other_flag IS NULL")).as("two filter columns").hasSize(1);
        assertThat(mismatches(rows, names + " ORDER BY sort_key DESC")).as("an ordering column nobody registered").hasSize(1);
        List<Row> extra = new ArrayList<>(rows);
        extra.add(new Row(0, Map.<String, Object>of("database", rows.stream().filter(r -> !r.database().equals(ExternalReadRegister.A8)).findFirst().orElseThrow().database(), "object", SHORT_NAME_VIEW, "column", "extra_column",
                "source", "dbo.other_table.extra_column", "category", "ORG_UNIT", "sensitivity", "INTERNAL", "purpose", "RUNTIME_SYNC")));
        assertThat(mismatches(extra, names)).hasSize(1);
    }
}
