package com.forcitis.one.platform.integration.infrastructure.seeyon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.integration.guard.ExternalReadRegister;
import com.forcitis.one.platform.integration.guard.ExternalReadRegister.Row;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Keeps the external read register, the code constants of SeeyonViewSql and the DBA view script in line with each other (ADR-0037 item 6.1). */
class ExternalReadRegisterTest {

    static final Path DBA_SCRIPT = Path.of("../docs/data/legacy-views/seeyon-views.sql");
    static final Path CLASSIFICATION = Path.of("architecture/data-classification.txt");

    /** Registered view column to the own column it lands in; both must carry the same sensitivity. */
    static final Map<String, String> OWN_COLUMN = Map.of(
            "forcitis_one.v_org_member.NAME", "employees.display_name",
            "forcitis_one.v_org_member.CODE", "employees.employee_code",
            "forcitis_one.v_org_member.WE_COM_ID", "employees.wecom_user_id",
            "forcitis_one.v_org_member.ENTERPRISE_EMAIL", "employees.email",
            "forcitis_one.v_org_member.GENDER", "employees.gender");

    static List<String> allProblems(List<Row> rows, Set<String> databases) {
        List<String> problems = new ArrayList<>();
        rows.forEach(r -> problems.addAll(ExternalReadRegister.problems(r, databases)));
        return problems;
    }

    /** a8_db run-time rows against the code: views column by column in order, base tables column by column. */
    static List<String> constantMismatches(List<Row> rows, Map<String, List<String>> viewColumns, Map<String, List<String>> baseColumns) {
        Map<String, List<String>> views = new LinkedHashMap<>();
        Map<String, List<String>> tables = new TreeMap<>();
        for (Row row : rows) {
            if (!row.database().equals(ExternalReadRegister.A8) || !row.purpose().runtime()) {
                continue;
            }
            (row.isView() ? views : tables).computeIfAbsent(row.objectName(), k -> new ArrayList<>()).add(row.column());
        }
        List<String> problems = new ArrayList<>();
        if (!views.equals(viewColumns)) {
            problems.add("registered views " + views + " differ from SeeyonViewSql.VIEW_COLUMNS " + viewColumns);
        }
        Map<String, Set<String>> wantTables = new TreeMap<>();
        baseColumns.forEach((t, c) -> wantTables.put(t, Set.copyOf(c)));
        Map<String, Set<String>> haveTables = new TreeMap<>();
        tables.forEach((t, c) -> haveTables.put(t, Set.copyOf(c)));
        if (tables.values().stream().anyMatch(c -> c.size() != Set.copyOf(c).size())) {
            problems.add("a base-table column is registered twice: " + tables);
        }
        if (!haveTables.equals(wantTables)) {
            problems.add("registered base tables " + haveTables + " differ from SeeyonViewSql.BASE_TABLE_COLUMNS " + wantTables);
        }
        return problems;
    }

    @Test
    @DisplayName("R-1b-0 外部读取登记每行字段齐全、取值合法，名字像敏感信息的列登记为 SENSITIVE / RESTRICTED")
    void everyRowIsCompleteAndLegal() throws IOException {
        List<Row> rows = ExternalReadRegister.rows();

        assertThat(rows).isNotEmpty();
        assertThat(ExternalReadRegister.databases()).as("two external databases are read").hasSize(2).contains(ExternalReadRegister.A8);
        assertThat(allProblems(rows, ExternalReadRegister.databases())).isEmpty();
        assertThat(rows).extracting(Row::database).containsAll(ExternalReadRegister.databases());
        assertThat(rows).anyMatch(r -> r.get("source").equals("dbo.ORG_PRINCIPAL.LOGIN_NAME"));
        assertThat(rows).as("one row per object and column").extracting(r -> r.database() + "|" + r.object() + "|" + r.column()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("R-1b-0 外部读取登记守护自检：缺字段、未知字段、非法用途（含 RECONCILIATION）、非法库与对象、敏感列登记为 INTERNAL、个人信息列登记为 PUBLIC、跨行的行都被报出")
    void injectedBadRowsAreFound() throws IOException {
        List<String> lines = new ArrayList<>(List.of("databases: [a8_db]", "columns:",
                "  - { database: a8_db, object: dbo.ORG_UNIT, column: TYPE, source: dbo.ORG_UNIT.TYPE, category: ORG_UNIT, sensitivity: INTERNAL, purpose: RUNTIME_SYNC }",
                "  - { database: a8_db, object: dbo.ORG_UNIT, column: CODE, source: dbo.ORG_UNIT.CODE, category: ORG_UNIT, sensitivity: INTERNAL }",
                "  - { database: a8_db, object: dbo.ORG_UNIT, column: NAME, source: dbo.ORG_UNIT.NAME, category: ORG_UNIT, sensitivity: INTERNAL, purpose: RECONCILIATION }",
                "  - { database: a8_db, object: dbo.ORG_MEMBER, column: EMAIL, source: dbo.ORG_MEMBER.EMAIL, category: EMPLOYEE, sensitivity: INTERNAL, purpose: MIGRATION }",
                "  - { database: a8_db, object: dbo.ORG_MEMBER, column: MOBILE, source: dbo.ORG_MEMBER.MOBILE, category: EMPLOYEE, sensitivity: INTERNAL, purpose: MIGRATION }",
                "  - { database: a8_db, object: dbo.ORG_MEMBER, column: GENDER, source: dbo.ORG_MEMBER.GENDER, category: EMPLOYEE, sensitivity: PUBLIC, purpose: MIGRATION }",
                "  - { database: other_db, object: ORG_UNIT, column: ID, source: ORG_UNIT.ID, category: 财务, sensitivity: SECRET, purpose: MIGRATION_TIE_OUT, note: x }"));

        List<Row> rows = ExternalReadRegister.rows(lines);
        Set<String> databases = ExternalReadRegister.databases(lines);
        List<String> problems = allProblems(rows, databases);

        assertThat(databases).containsExactly("a8_db");
        assertThat(ExternalReadRegister.problems(rows.get(0), databases)).isEmpty();
        assertThat(problems).anyMatch(p -> p.contains(":4 lacks purpose"));
        assertThat(problems).anyMatch(p -> p.contains(":5 purpose RECONCILIATION"));
        assertThat(problems).noneMatch(p -> p.contains(":6 "));
        assertThat(problems).anyMatch(p -> p.contains(":7 column MOBILE looks sensitive"));
        assertThat(problems).anyMatch(p -> p.contains(":8 column GENDER looks personal"));
        assertThat(problems).anyMatch(p -> p.contains(":9 has unknown fields [note]"));
        List<String> sixth = ExternalReadRegister.problems(new Row(9, Map.<String, Object>of("database", "other_db", "object", "ORG_UNIT", "column", "ID",
                "source", "ORG_UNIT.ID", "category", "财务", "sensitivity", "SECRET", "purpose", "MIGRATION_TIE_OUT")), databases);
        assertThatThrownBy(() -> ExternalReadRegister.databases(List.of("columns: []"))).hasMessageContaining("no databases list");
        assertThat(sixth).anyMatch(p -> p.contains("database other_db")).anyMatch(p -> p.contains("object ORG_UNIT"))
                .anyMatch(p -> p.contains("source ORG_UNIT.ID")).anyMatch(p -> p.contains("category 财务")).anyMatch(p -> p.contains("sensitivity SECRET"))
                .noneMatch(p -> p.contains("purpose"));

        List<String> split = List.of("columns:", "  - { database: a8_db, object: dbo.ORG_UNIT, column: TYPE,",
                "      source: dbo.ORG_UNIT.TYPE, category: ORG_UNIT, sensitivity: INTERNAL, purpose: RUNTIME_SYNC }");
        assertThatThrownBy(() -> ExternalReadRegister.rows(split)).hasMessageContaining("one flow mapping on one line");
    }

    @Test
    @DisplayName("R-1b-0 外部读取登记守护自检：forcitis_one.v_* 视图只属于 a8_db，登在老系统库下被报出")
    void viewsBelongToA8Only() throws IOException {
        Set<String> databases = ExternalReadRegister.databases();
        String other = databases.stream().filter(d -> !d.equals(ExternalReadRegister.A8)).findFirst().orElseThrow();
        Map<String, Object> view = Map.of("database", ExternalReadRegister.A8, "object", "forcitis_one.v_org_unit", "column", "ID",
                "source", "dbo.ORG_UNIT.ID", "category", "ORG_UNIT", "sensitivity", "INTERNAL", "purpose", "RUNTIME_SYNC");
        Map<String, Object> misplaced = new LinkedHashMap<>(view);
        misplaced.put("database", other);

        assertThat(ExternalReadRegister.problems(new Row(1, view), databases)).isEmpty();
        assertThat(ExternalReadRegister.problems(new Row(2, misplaced), databases)).singleElement().asString()
                .contains(":2 object forcitis_one.v_org_unit is a view of a8_db, not of " + other);
    }

    @Test
    @DisplayName("R-1b-0 代码常量与登记中 a8_db 运行时用途的行逐列相等：视图列（含顺序）等于 VIEW_COLUMNS，基表列等于 BASE_TABLE_COLUMNS")
    void runtimeRowsEqualTheCodeConstants() throws IOException {
        assertThat(constantMismatches(ExternalReadRegister.rows(), SeeyonViewSql.VIEW_COLUMNS, SeeyonViewSql.BASE_TABLE_COLUMNS)).isEmpty();
    }

    @Test
    @DisplayName("R-1b-0 常量一致性守护自检：多登记一列、少登记一列、基表列不同都被报出；迁入用途的行不参与")
    void injectedConstantDriftIsFound() throws IOException {
        List<Row> rows = ExternalReadRegister.rows();
        Map<String, List<String>> views = new LinkedHashMap<>(SeeyonViewSql.VIEW_COLUMNS);
        List<String> unitColumns = new ArrayList<>(views.get(SeeyonViewSql.ORG_UNIT_VIEW));
        unitColumns.remove("CATEGORY");
        views.put(SeeyonViewSql.ORG_UNIT_VIEW, unitColumns);

        assertThat(constantMismatches(rows, views, SeeyonViewSql.BASE_TABLE_COLUMNS)).singleElement().asString().contains("registered views");
        assertThat(constantMismatches(rows, SeeyonViewSql.VIEW_COLUMNS, Map.of("ORG_UNIT", List.of("ID", "TYPE", "PATH"))))
                .singleElement().asString().contains("registered base tables");

        List<Row> withMigration = new ArrayList<>(rows);
        withMigration.add(new Row(0, Map.<String, Object>of("database", "a8_db", "object", "dbo.ORG_MEMBER", "column", "STATE", "source", "dbo.ORG_MEMBER.STATE",
                "category", "组织", "sensitivity", "INTERNAL", "purpose", "MIGRATION")));
        assertThat(constantMismatches(withMigration, SeeyonViewSql.VIEW_COLUMNS, SeeyonViewSql.BASE_TABLE_COLUMNS)).isEmpty();
        List<Row> withRuntime = new ArrayList<>(rows);
        withRuntime.add(new Row(0, Map.<String, Object>of("database", "a8_db", "object", "dbo.ORG_MEMBER", "column", "STATE", "source", "dbo.ORG_MEMBER.STATE",
                "category", "组织", "sensitivity", "INTERNAL", "purpose", "RUNTIME_SYNC")));
        assertThat(constantMismatches(withRuntime, SeeyonViewSql.VIEW_COLUMNS, SeeyonViewSql.BASE_TABLE_COLUMNS)).isNotEmpty();
    }

    @Test
    @DisplayName("R-1b-0 登记的个人信息列与落到的自有列访问级别一致、且自有列带个人信息标记（data-classification.txt，ADR-0043 两维）")
    void sensitivityMatchesTheOwnColumns() throws IOException {
        Map<String, List<String>> levels = new TreeMap<>();
        Map<String, String> marks = new TreeMap<>();
        for (String line : Files.readAllLines(CLASSIFICATION)) {
            String[] f = line.split("\\|");
            if (!line.startsWith("#") && f.length >= 2) {
                String column = f[0].trim();
                levels.computeIfAbsent(column.substring(column.indexOf('.') + 1), k -> new ArrayList<>()).add(f[1].trim());
                if (f.length >= 3) {
                    marks.put(column.substring(column.indexOf('.') + 1), f[2].trim());
                }
            }
        }
        Map<String, String> registered = new TreeMap<>();
        ExternalReadRegister.rows().forEach(r -> registered.put(r.object() + "." + r.column(), r.get("sensitivity")));

        OWN_COLUMN.forEach((read, own) -> {
            assertThat(levels.get(own)).as("own column %s is classified once", own).hasSize(1);
            assertThat(registered).as("%s is registered", read).containsKey(read);
            assertThat(registered.get(read)).as("%s lands in %s", read, own).isEqualTo(levels.get(own).getFirst());
            assertThat(marks.get(own)).as("%s lands in %s, which must carry the personal-information mark", read, own).isIn("PI", "SPI");
        });
    }

    @Test
    @DisplayName("§9a the DBA proposal creates exactly the registered views, grants SELECT on the view schema, grants no write and holds no real password")
    void dbaScriptMatchesTheRegister() throws IOException {
        String sql = Files.readString(DBA_SCRIPT);

        for (Map.Entry<String, List<String>> view : SeeyonViewSql.VIEW_COLUMNS.entrySet()) {
            assertThat(sql).contains("CREATE VIEW " + SeeyonViewSql.SCHEMA + "." + view.getKey() + " AS");
        }
        assertThat(sql).contains("GRANT SELECT ON SCHEMA::" + SeeyonViewSql.SCHEMA)
                .contains("DENY INSERT, UPDATE, DELETE, EXECUTE, ALTER, REFERENCES ON SCHEMA::" + SeeyonViewSql.SCHEMA);
        assertThat(sql).doesNotContainIgnoringCase("EXT_ATTR_21").as("no birthday in a view");
        assertThat(sql.lines().filter(l -> l.matches("(?i)\\s*(GRANT|ALTER ROLE|EXEC sp_addrolemember).*"))
                .filter(l -> l.matches("(?i).*(db_datareader|db_owner|db_datawriter|CONTROL|INSERT|UPDATE|DELETE).*") && !l.matches("(?i)\\s*DENY.*"))).isEmpty();
        assertThat(sql).as("no real password or login: placeholders only").doesNotContainPattern("(?i)PASSWORD\\s*=\\s*N'(?!<)")
                .contains("{{READER_LOGIN}}").contains("{{READER_USER}}");
    }
}
