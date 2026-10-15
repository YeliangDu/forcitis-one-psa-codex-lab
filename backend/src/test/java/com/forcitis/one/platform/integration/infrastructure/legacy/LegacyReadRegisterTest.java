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

/** R-1b-0 C-5: the old system's two views as registered equal, column by column, the statements the integration layer sends (ADR-0037 item 6.1). */
class LegacyReadRegisterTest {

    static final String TODO_VIEW = "dbo.v_psa_one_legacy_todo";
    static final String SHORT_NAME_VIEW = "dbo.v_one_legacy_org_short_name";
    static final Pattern STATEMENT = Pattern.compile("(?is)^\\s*SELECT\\s+(.+?)\\s+FROM\\s+(\\S+)(?:\\s+WHERE\\s+(\\w+)\\s*=.*)?$");

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

    /** The object a statement reads and every column it names, in the select list or the filter. */
    static Map<String, TreeSet<String>> sent(String... statements) {
        Map<String, TreeSet<String>> byObject = new TreeMap<>();
        for (String statement : statements) {
            Matcher m = STATEMENT.matcher(statement);
            assertThat(m.matches()).as("statement shape: %s", statement).isTrue();
            TreeSet<String> columns = byObject.computeIfAbsent(m.group(2), k -> new TreeSet<>());
            Arrays.stream(m.group(1).split(",")).map(String::trim).forEach(columns::add);
            if (m.group(3) != null) {
                columns.add(m.group(3));
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
    @DisplayName("R-1b-0 C-5 老系统两个视图的登记列与集成层 SQL 常量逐列相等（待办含过滤列 assignee_user_id）")
    void registeredLegacyColumnsEqualTheStatements() throws IOException {
        List<Row> rows = ExternalReadRegister.rows();

        assertThat(registered(rows)).as("the register holds exactly the two old-system views").containsOnlyKeys(TODO_VIEW, SHORT_NAME_VIEW);
        assertThat(mismatches(rows, JdbcLegacyInbox.todoSql(TODO_VIEW), LegacyOrgNames.shortNameSql(SHORT_NAME_VIEW))).isEmpty();
    }

    @Test
    @DisplayName("R-1b-0 C-5 自检：SQL 多读一列、少读一列、登记多一列、读了未登记的视图都被报出")
    void injectedDriftIsFound() throws IOException {
        List<Row> rows = ExternalReadRegister.rows();
        String todo = JdbcLegacyInbox.todoSql(TODO_VIEW);
        String names = LegacyOrgNames.shortNameSql(SHORT_NAME_VIEW);

        assertThat(mismatches(rows, todo.replace("created_at FROM", "created_at, assignee_name FROM"), names)).hasSize(1);
        assertThat(mismatches(rows, todo.replace("process_title, ", ""), names)).hasSize(1);
        assertThat(mismatches(rows, todo, "SELECT id FROM " + SHORT_NAME_VIEW)).hasSize(1);
        assertThat(mismatches(rows, todo, names, "SELECT secret_value FROM dbo.other_table")).hasSize(1);
        List<Row> extra = new ArrayList<>(rows);
        extra.add(new Row(0, Map.<String, Object>of("database", rows.stream().filter(r -> !r.database().equals(ExternalReadRegister.A8)).findFirst().orElseThrow().database(), "object", SHORT_NAME_VIEW, "column", "extra_column",
                "source", "dbo.other_table.extra_column", "category", "ORG_UNIT", "sensitivity", "INTERNAL", "purpose", "RUNTIME_SYNC")));
        assertThat(mismatches(extra, todo, names)).hasSize(1);
    }
}
