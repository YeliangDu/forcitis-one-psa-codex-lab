package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DdlNamingTest {
    @Test
    @DisplayName("NAM-1017-GLO-09 / N3-L1：存量迁移、最终模型与命名基线的每段标识符最多 63 字节，长度基线为零")
    void migrationAndBaselineIdentifiersFit63Bytes() throws IOException {
        Map<String, String> migrations = readMigrations();
        assertThat(migrations).isNotEmpty();
        assertThat(DdlIdentifierLengths.sourceProblems(migrations)) .as(GuardMessage.of("N3-L1", "an identifier in a migration is longer than 63 bytes in UTF-8", "shorten it with a registered abbreviation in the migration (the database must not truncate it)")).isEmpty();
        assertThat(DdlIdentifierLengths.modelProblems(model(migrations))) .as(GuardMessage.of("N3-L1", "an identifier of the final schema model, with its prefix or suffix, is longer than 63 bytes in UTF-8", "shorten the table, column or constraint name with a registered abbreviation in a new migration")).isEmpty();
        for (String entry : readBaseline().keySet()) {
            for (String part : entry.substring(entry.indexOf(' ') + 1).split("\\.")) {
                assertThat(DdlIdentifierLengths.bytes(part)).as(GuardMessage.of("N3-L1", "the entry " + entry + " of architecture/ddl-naming-baseline.txt has the part " + part + " over 63 bytes", "rename the object in a new migration and remove the baseline line")).isLessThanOrEqualTo(63);
            }
        }
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 / N3-L1：schema、表、列、约束与索引独立计长，前缀计入 63 字节")
    void allIdentifierKindsIncludeTheirPrefixesInTheByteLimit() {
        String exact = "a".repeat(63);
        Schema schema = new Schema();
        Table table = new Table();
        table.columns.put(exact, new Column(exact, "text", false));
        table.constraints.put(exact, new Constraint("ck", exact, false));
        schema.tables.put(exact, table);
        schema.indexes.put(exact, exact);
        assertThat(DdlIdentifierLengths.modelProblems(Map.of(exact, schema))).isEmpty();

        String longName = "a".repeat(64);
        table.columns.put(longName, new Column(longName, "text", false));
        table.constraints.put("ck_" + "a".repeat(61), new Constraint("ck", "ck_" + "a".repeat(61), false));
        schema.tables.put(longName, new Table());
        schema.indexes.put("ix_" + "a".repeat(61), exact);
        Map<String, Integer> problems = DdlIdentifierLengths.modelProblems(Map.of(longName, schema));
        assertThat(problems).hasSize(5);
        assertThat(problems.keySet()).anyMatch(k -> k.startsWith("schema "))
                .anyMatch(k -> k.startsWith("table ")).anyMatch(k -> k.startsWith("column "))
                .anyMatch(k -> k.startsWith("constraint ")).anyMatch(k -> k.startsWith("index "));
        assertThat(problems.values()).containsOnly(64);
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 / N3-L1：UTF-8 字节数、带引号名字及删除/改名的中间名字均受检")
    void sourceLengthCheckCatchesUtf8AndIntermediateNames() {
        String exact = "界".repeat(21);
        String over = exact + "a";
        assertThat(DdlIdentifierLengths.bytes(exact)).isEqualTo(63);
        String sql = "CREATE TABLE platform_sample.\"" + over + "\" (\n    id uuid\n);\n"
                + "ALTER TABLE platform_sample.\"" + over + "\" RENAME TO things;\n"
                + "ALTER TABLE platform_sample.things ADD COLUMN \"" + "a".repeat(64) + "\" text;\n"
                + "ALTER TABLE platform_sample.things DROP COLUMN \"" + "a".repeat(64) + "\";\n";
        assertThat(DdlIdentifierLengths.sourceProblems(Map.of("V1.sql", sql))).hasSize(2);
        assertThat(DdlIdentifierLengths.sourceProblems(Map.of("V1.sql", "CREATE TABLE \"" + exact + "\".\"" + exact + "\" (id uuid);"))).isEmpty();
        assertThat(DdlIdentifierLengths.identifiers("SELECT \"a\"\"b\";")).contains("a\"b");
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 / N3-L1：文字与注释不误报，未闭合 SQL 输入失败")
    void lengthScannerExcludesTextAndRejectsBrokenInput() {
        String longName = "a".repeat(64);
        String sql = "-- " + longName + "\n/* 外层 /* " + longName + " */ 注释 */\n"
                + "SELECT '" + longName + "', E'\\\'" + longName + "', $body$" + longName + "$body$;";
        assertThat(DdlIdentifierLengths.sourceProblems(Map.of("V1.sql", sql))).isEmpty();
        for (String broken : List.of("/* x", "SELECT 'x", "SELECT \"x", "SELECT $body$x")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> DdlIdentifierLengths.identifiers(broken))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 M2：未命名普通与唯一索引采用多列自动名并接受命名检查")
    void unnamedIndexesAreModeledAndFailNamingRules() {
        String sql = "CREATE INDEX ON platform_sample.things (employee_id, entry_code);\n"
                + "CREATE UNIQUE INDEX ON platform_sample.things USING btree (entry_code);\n";
        Schema schema = model(Map.of("V1.sql", sql)).get("platform_sample");
        assertThat(schema.indexes).containsEntry("things_employee_id_entry_code_idx", "things")
                .containsEntry("things_entry_code_idx", "things");
        assertThat(schema.indexKinds).containsEntry("things_entry_code_idx", "unique");
        assertThat(violations(Map.of("platform_sample", schema)).keySet()).contains(
                "index platform_sample.things_employee_id_entry_code_idx", "index platform_sample.things_entry_code_idx");
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 M2：ALTER 的未命名主键、唯一、外键与检查约束均进入模型")
    void unnamedAlterConstraintsAreModeledAndFailNamingRules() {
        String sql = "CREATE TABLE platform_sample.things (\n    id uuid,\n    employee_id uuid,\n    entry_code text\n);\n"
                + "ALTER TABLE platform_sample.things ADD PRIMARY KEY (id), ADD UNIQUE (employee_id, entry_code), "
                + "ADD FOREIGN KEY (employee_id, entry_code) REFERENCES platform_sample.others (id, entry_code), "
                + "ADD CHECK (char_length(entry_code) > 0 AND employee_id IS NOT NULL);\n";
        Schema schema = model(Map.of("V1.sql", sql)).get("platform_sample");
        Table table = schema.tables.get("things");
        assertThat(table.primaryKey).containsExactly("id");
        assertThat(table.constraints.keySet()).containsExactlyInAnyOrder("things_pkey", "things_employee_id_entry_code_key",
                "things_employee_id_entry_code_fkey", "things_check");
        assertThat(violations(Map.of("platform_sample", schema)).keySet()).contains(
                "constraint platform_sample.things.things_pkey",
                "constraint platform_sample.things.things_employee_id_entry_code_key",
                "constraint platform_sample.things.things_employee_id_entry_code_fkey",
                "constraint platform_sample.things.things_check");
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 M2：CREATE TABLE 的未命名检查约束按引用列顺序去重，无列时只加 check")
    void unnamedTableChecksUseReferencedColumns() {
        String sql = "CREATE TABLE platform_sample.things (\n    entry_code text,\n    employee_id uuid,\n"
                + "    CHECK (entry_code <> 'employee_id' AND char_length(entry_code) > 0),\n"
                + "    CHECK (true)\n);\n";
        Table table = model(Map.of("V1.sql", sql)).get("platform_sample").tables.get("things");
        assertThat(table.constraints.keySet()).containsExactlyInAnyOrder("things_entry_code_check", "things_check");
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 M2 / N3-L1：自动名各自恰好 63 字节通过，64 字节失败且命名检查不漏报")
    void automaticNamesHonorThe63ByteBoundaryBeforeDatabaseTruncation() {
        for (String operation : List.of("index", "unique index", "primary key", "unique", "foreign key", "check")) {
            String suffix = switch (operation) {
                case "index", "unique index" -> "idx";
                case "primary key" -> "pkey";
                case "unique" -> "key";
                case "foreign key" -> "fkey";
                default -> "check";
            };
            for (int size : List.of(63, 64)) {
                boolean primary = operation.equals("primary key");
                String table = "a".repeat(primary ? size - suffix.length() - 1 : 40);
                String column = primary ? "id" : "b".repeat(size - table.length() - suffix.length() - 2);
                String autoName = table + (primary ? "" : "_" + column) + "_" + suffix;
                String head = "CREATE TABLE platform_sample." + table + " (\n    " + column + " uuid\n);\n";
                String definition = operation.equals("check") ? "CHECK (" + column + " IS NOT NULL)"
                        : operation.toUpperCase(Locale.ROOT) + " (" + column + ")"
                        + (operation.equals("foreign key") ? " REFERENCES platform_sample.others (id)" : "");
                String sql = head + (operation.endsWith("index")
                        ? "CREATE " + operation.toUpperCase(Locale.ROOT) + " ON platform_sample." + table + " (" + column + ");"
                        : "ALTER TABLE platform_sample." + table + " ADD " + definition + ";");
                Map<String, Schema> schemas = model(Map.of("V1.sql", sql));
                String problem = operation.endsWith("index") ? "index platform_sample." + autoName
                        : "constraint platform_sample." + table + "." + autoName;
                assertThat(DdlIdentifierLengths.bytes(autoName)).as(operation).isEqualTo(size);
                assertThat(violations(schemas)).as(operation).containsKey(problem);
                if (size == 63) {
                    assertThat(DdlIdentifierLengths.modelProblems(schemas)).as(operation).isEmpty();
                } else {
                    assertThat(DdlIdentifierLengths.modelProblems(schemas)).as(operation).containsOnlyKeys(problem)
                            .containsEntry(problem, 64);
                }
            }
        }
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 M3：四种解析失败均带输入文件和起始行号")
    void scannerErrorsIdentifyTheFileAndOpeningLine() {
        for (String broken : List.of("/* x", "SELECT 'x", "SELECT \"x", "SELECT $body$x")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> DdlIdentifierLengths.sourceProblems(
                    Map.of("db/V1.sql", "-- 第一行\n\n" + broken)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("db/V1.sql:3:");
        }
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 M3：DATE 及名字尾部 E 不是转义前缀，只有独立 E 记号启用反斜线转义")
    void escapeStringPrefixMustBeAStandaloneToken() {
        assertThat(DdlIdentifierLengths.sourceProblems(Map.of("V1.sql",
                "SELECT DATE'2026-01-01\\', name'ordinary\\', e'escaped\\\'quote', E'escaped\\\'quote';"))).isEmpty();
        assertThat(DdlIdentifierLengths.identifiers("SELECT DATE'ordinary\\';")).contains("DATE");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DdlIdentifierLengths.sourceProblems(
                Map.of("V1.sql", "SELECT E'unclosed\\';")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("V1.sql:1:");
    }

    @Test
    @DisplayName("NAM-1017-GLO-09 L3：增补平面无引号标识符按完整码点扫描，63 字节通过、64 字节失败")
    void supplementaryUnquotedIdentifiersAreScannedByCodePoint() {
        String exact = "\uD801\uDC00".repeat(15) + "abc";
        String over = exact + "d";
        assertThat(DdlIdentifierLengths.bytes(exact)).isEqualTo(63);
        assertThat(DdlIdentifierLengths.identifiers("CREATE SCHEMA " + exact + ";")).contains(exact);
        assertThat(DdlIdentifierLengths.sourceProblems(Map.of("V1.sql", "CREATE SCHEMA " + exact + ";"))).isEmpty();
        assertThat(DdlIdentifierLengths.sourceProblems(Map.of("V1.sql", "CREATE SCHEMA " + over + ";")))
                .containsEntry("V1.sql identifier " + over, 64);
    }

    // 仅扫描正式 Flyway 迁移；testdata 与 init-schemas/init-dba-schemas、运维 one_db_roles_schemas.sql 不参与最终业务模型，另卡扩展。
    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    static final Path BASELINE = Path.of("architecture/ddl-naming-baseline.txt");
    static final Path CLASSIFICATION = Path.of("architecture/data-classification.txt");
    static final Pattern FILE = Pattern.compile("V(\\d{14})__([a-z]+_[a-z]+)_[a-z0-9_]+\\.sql");
    /** Groups 4 and 5 are the partition method (RANGE, LIST or HASH) and key of a partitioned table (ADR-0048), absent otherwise. */
    static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE\\s+([a-z0-9_]+)\\.([a-z0-9_]+)\\s*\\((.*?)\\n\\)(?:\\s*PARTITION BY\\s+(RANGE|LIST|HASH)\\s*\\(\\s*([a-z0-9_]+)\\s*\\))?\\s*;", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    /** Every table definition in a migration, whatever its shape; a partition (PARTITION OF) is not a definition of its own. */
    static final Pattern ANY_CREATE_TABLE = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[a-z0-9_]+\\.[a-z0-9_]++\\s*+(?!PARTITION\\s+OF\\b)", Pattern.CASE_INSENSITIVE);
    static final Pattern SNAKE = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");
    static final Set<String> BANNED = Set.of("psa", "cuvalue", "manhour", "man_hour", "works", "biget", "arbad", "ohproject",
            "flag", "info", "data", "temp", "tmp", "bd", "pm", "u8c", "fst");
    static final Set<String> KEYWORDS = Set.of("primary", "constraint", "unique", "foreign", "check");
    static final Set<String> COMMON_COLUMNS = Set.of("id", "created_at", "created_by", "updated_at", "updated_by", "version");
    static final Pattern LOCAL_TIMESTAMP = Pattern.compile("\\btimestamp\\b(?!tz)(?!\\s*(\\(\\d+\\))?\\s+with\\s+time\\s+zone)", Pattern.CASE_INSENSITIVE);

    // Column names and types are fixed by the framework that owns the table (Spring Session JDBC, Spring Modulith, ShedLock); the list is architecture/primary-key-exemptions.txt.
    static final Set<String> FRAMEWORK_TABLES = PrimaryKeyExemptions.tables();

    enum ClassWord {
        ID("_id", "bigint", "uuid", "text"),
        IDS("_ids", "uuid[]"),
        KEY("_key", "text"),
        CODE("_code", "text", "varchar"),
        CODES("_codes", "text[]"),
        NAME("_name", "text", "varchar"),
        TEXT("_text", "text"),
        DESCRIPTION("_description", "text"),
        REASON("_reason", "text"),
        NOTE("_note", "text"),
        TITLE("_title", "text"),
        MESSAGE("_message", "text"),
        ADDRESS("_address", "text"),
        CIPHERTEXT("_ciphertext", "text"),
        POLICY("_policy", "text"),
        SYSTEM("_system", "text"),
        STATUS("_status", "text"),
        KIND("_kind", "text"),
        TYPE("_type", "text"),
        AT("_at", "timestamptz"),
        DATE("_date", "date"),
        AMOUNT("_amount", "numeric"),
        CURRENCY("_currency", "text", "char"),
        QTY("_qty", "numeric", "integer", "bigint"),
        HOURS("_hours", "numeric"),
        DAYS("_days", "numeric"),
        RATE("_rate", "numeric"),
        PCT("_pct", "numeric"),
        COUNT("_count", "integer", "bigint"),
        NUMBER("_number", "text", "integer", "bigint"),
        ORDER("_order", "integer"),
        HASH("_hash", "text"),
        URL("_url", "text"),
        PATH("_path", "text"),
        BY("_by", "uuid"),
        VALUE("_value", "numeric", "integer", "bigint"),
        IS("is_", "boolean"),
        HAS("has_", "boolean");

        final String affix;
        final Set<String> types;

        ClassWord(String affix, String... types) {
            this.affix = affix;
            this.types = Set.of(types);
        }

        boolean names(String column) {
            return affix.startsWith("_") ? column.endsWith(affix) || column.equals(affix.substring(1)) : column.startsWith(affix);
        }
    }

    static final Map<String, Set<String>> STANDALONE = Map.of("id", Set.of("bigint", "uuid", "text"), "version", Set.of("bigint", "integer"));
    static final Set<String> CHECKED_ENUMS = Set.of("_status", "_kind", "_type", "_policy");

    record Column(String name, String type, boolean checked) {}

    record Constraint(String kind, String name, boolean inlineCheck) {}

    static final class Table {
        final Map<String, Column> columns = new LinkedHashMap<>();
        final Map<String, Constraint> constraints = new LinkedHashMap<>();
        final List<String> primaryKey = new ArrayList<>();
        String partitionMethod;
        String partitionKey;
    }

    static final class Schema {
        final Map<String, Table> tables = new TreeMap<>();
        final Map<String, String> indexes = new TreeMap<>();
        final Map<String, String> indexKinds = new TreeMap<>();
    }

    @Test
    @DisplayName("each migration follows NAMING_STANDARD §2.3: file name, own schema, snake_case, booleans, enums, money, comments, audit columns")
    void migrationsFollowTheNamingStandard() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : migrationFiles()) {
            String folder = file.getParent().getFileName().toString();
            Matcher name = FILE.matcher(file.getFileName().toString());
            if (!name.matches() || !name.group(2).equals(folder)) {
                problems.add(file + ": name must be V<yyyyMMddHHmmss>__" + folder + "_<description>.sql");
                continue;
            }
            check(file, folder, Files.readString(file), problems);
        }
        if (!problems.isEmpty()) {
            throw new AssertionError(GuardMessage.of("D2 (NAMING_STANDARD.md §2.3)", "a migration breaks the DDL naming rules", "change the migration as each line below says (while the baseline is open) or add a new migration") + "\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    @DisplayName("every CREATE TABLE of a migration is read by the model: a table the parser cannot see would escape the naming, key and glossary guards")
    void everyTableDefinitionIsParsed() throws IOException {
        for (Map.Entry<String, String> migration : readMigrations().entrySet()) {
            assertThat(undeclared(migration.getValue())).as(GuardMessage.of("D2 (NAMING_STANDARD.md §2.3)", migration.getKey() + " has a CREATE TABLE the model cannot read, so its naming, key and glossary checks are skipped", "write the CREATE TABLE in the plain form the DdlNamingTest parser reads, or teach the parser the clause")).isZero();
        }
    }

    @Test
    @DisplayName("the parser guard counts a table the model cannot read (a LIST table is read, a table with an unknown clause after the column list is not)")
    void theParserGuardCountsWhatTheModelMisses() {
        String list = "CREATE TABLE platform_sample.things (\n    id uuid NOT NULL\n)\nPARTITION BY LIST (id);\n";
        assertThat(undeclared(list)).isZero();
        assertThat(model(Map.of("V1__x.sql", list)).get("platform_sample").tables.get("things").partitionMethod).isEqualTo("LIST");
        assertThat(undeclared("CREATE TABLE platform_sample.things (\n    id uuid NOT NULL\n)\nWITH (fillfactor = 70);\n")).isEqualTo(1);
        assertThat(undeclared("CREATE TABLE platform_sample.part PARTITION OF platform_sample.things FOR VALUES IN (1);\n-- CREATE TABLE platform_sample.c (\n")).isZero();
    }

    /** Table definitions in the text that the strict pattern did not match. */
    static long undeclared(String sql) {
        String code = sql.replaceAll("(?m)--.*$", "");
        long any = ANY_CREATE_TABLE.matcher(code).results().count();
        long parsed = CREATE_TABLE.matcher(code).results().count();
        return any - parsed;
    }

    @Test
    @DisplayName("the resulting schema follows NAMING_STANDARD §2.4: class words, constraint and index names; only baseline entries are tolerated and none is stale")
    void resultingSchemaFollowsClassWordsAndConstraintNames() throws IOException {
        Map<String, String> baseline = readBaseline();
        Map<String, String> found = violations(model(readMigrations()));
        List<String> problems = new ArrayList<>();
        found.forEach((v, why) -> {
            if (!baseline.containsKey(v)) {
                problems.add(v + " — " + why);
            }
        });
        for (String b : baseline.keySet()) {
            if (!found.containsKey(b)) {
                problems.add(b + " — fixed or gone: remove it from " + BASELINE);
            }
        }
        if (!problems.isEmpty()) {
            throw new AssertionError(GuardMessage.of("D2 (NAMING_STANDARD.md §2.4)", "a column, constraint or index of the resulting schema breaks the class word or name rules, or a baseline line is stale", "rename or retype it in a new migration as each line says; delete a stale line from " + BASELINE + ", and add a line there only for a gap with a reason") + "\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    @DisplayName("every sensitive-looking column is classified at the level and with the mark its name demands, and every classified column exists (ADR-0043, NAMING_STANDARD §2.5)")
    void sensitiveColumnsAreClassified() throws IOException {
        List<String> problems = classificationProblems(model(readMigrations()), readClassification());
        if (!problems.isEmpty()) {
            throw new AssertionError(GuardMessage.of("D2 (NAMING_STANDARD.md §2.5)", "a sensitive-looking column is not classified, or a classified column does not exist", "add <schema.table.column> | SENSITIVE or RESTRICTED | NONE, PI or SPI | <reason> to " + CLASSIFICATION + ", or delete the line of a column that is gone") + "\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    @DisplayName("injection: wrong class word, wrong type, unnamed or wrongly named constraint and index, unclassified sensitive column all fail")
    void injectedViolationsAreCaught() {
        String bad = """
                CREATE TABLE cost_timesheet.time_entries (
                    id            bigint      PRIMARY KEY,
                    employee_id   bigint      NOT NULL REFERENCES organization_workforce.employees (id),
                    entry_code    text        NOT NULL UNIQUE,
                    worked        numeric(6,2) NOT NULL,
                    approved_at   date,
                    bank_account  text,
                    created_at    timestamptz NOT NULL,
                    updated_at    timestamptz NOT NULL
                );
                CREATE INDEX time_entries_employee_idx ON cost_timesheet.time_entries (employee_id);
                """;
        Map<String, String> v = violations(model(Map.of("V20990101000000__cost_timesheet_x.sql", bad)));
        assertThat(v.keySet()).contains(
                "column cost_timesheet.time_entries.worked",
                "column cost_timesheet.time_entries.approved_at",
                "column cost_timesheet.time_entries.bank_account",
                "constraint cost_timesheet.time_entries.time_entries_pkey",
                "constraint cost_timesheet.time_entries.time_entries_employee_id_fkey",
                "constraint cost_timesheet.time_entries.time_entries_entry_code_key",
                "index cost_timesheet.time_entries_employee_idx");
        assertThat(classificationProblems(model(Map.of("V20990101000000__cost_timesheet_x.sql", bad)), Map.of()))
                .anyMatch(p -> p.contains("cost_timesheet.time_entries.bank_account"));

        String good = """
                CREATE TABLE cost_timesheet.time_entries (
                    id              bigint       NOT NULL,
                    employee_id     bigint       NOT NULL,
                    entry_code      text         NOT NULL,
                    worked_hours    numeric(6,2) NOT NULL,
                    approved_at     timestamptz,
                    entry_status    text         NOT NULL CHECK (entry_status IN ('DRAFT', 'SUBMITTED')),
                    created_at      timestamptz  NOT NULL,
                    updated_at      timestamptz  NOT NULL,
                    CONSTRAINT pk_time_entries PRIMARY KEY (id),
                    CONSTRAINT fk_time_entries_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id),
                    CONSTRAINT uk_time_entries_entry_code UNIQUE (entry_code)
                );
                CREATE INDEX ix_time_entries_employee_id ON cost_timesheet.time_entries (employee_id);
                """;
        assertThat(violations(model(Map.of("V20990101000000__cost_timesheet_x.sql", good)))).isEmpty();
    }

    @Test
    @DisplayName("renames in later migrations are followed: RENAME COLUMN, RENAME CONSTRAINT and ALTER INDEX ... RENAME TO clear the violation")
    void laterRenamesAreFollowed() {
        Map<String, String> files = new TreeMap<>();
        files.put("V20990101000000__cost_timesheet_a.sql", """
                CREATE TABLE cost_timesheet.time_entries (
                    id        bigint  PRIMARY KEY,
                    worked    numeric(6,2) NOT NULL,
                    created_at timestamptz NOT NULL,
                    updated_at timestamptz NOT NULL
                );
                CREATE INDEX time_entries_worked_idx ON cost_timesheet.time_entries (worked);
                """);
        files.put("V20990101000001__cost_timesheet_b.sql", """
                ALTER TABLE cost_timesheet.time_entries RENAME COLUMN worked TO worked_hours;
                ALTER TABLE cost_timesheet.time_entries RENAME CONSTRAINT time_entries_pkey TO pk_time_entries;
                ALTER INDEX cost_timesheet.time_entries_worked_idx RENAME TO ix_time_entries_worked_hours;
                """);
        assertThat(violations(model(files))).isEmpty();
    }

    @Test
    @DisplayName("ADR-0034-IMPL A-3: _ids is a uuid[] class word; an enum column is checked by a named table-level CHECK that lists its values, and is still caught without any CHECK")
    void idsClassWordAndNamedEnumChecks() {
        String head = "CREATE TABLE cost_timesheet.time_entries (\n    id uuid NOT NULL,\n    created_at timestamptz NOT NULL,\n    updated_at timestamptz NOT NULL,\n";
        String good = head + "    base_role_version_ids uuid[] NOT NULL DEFAULT '{}',\n    entry_status text NOT NULL,\n"
                + "    CONSTRAINT pk_time_entries PRIMARY KEY (id),\n"
                + "    CONSTRAINT ck_time_entries_entry_status CHECK (entry_status IN ('DRAFT', 'SUBMITTED'))\n);\n";
        assertThat(violations(model(Map.of("V20990101000000__cost_timesheet_x.sql", good)))).isEmpty();

        String wrongType = head + "    base_role_version_ids text NOT NULL,\n    CONSTRAINT pk_time_entries PRIMARY KEY (id)\n);\n";
        assertThat(violations(model(Map.of("V20990101000000__cost_timesheet_x.sql", wrongType))).keySet()).contains("column cost_timesheet.time_entries.base_role_version_ids");

        String unchecked = head + "    entry_status text NOT NULL,\n    CONSTRAINT pk_time_entries PRIMARY KEY (id),\n"
                + "    CONSTRAINT ck_time_entries_note CHECK (char_length(entry_status) < 20)\n);\n";
        assertThat(violations(model(Map.of("V20990101000000__cost_timesheet_x.sql", unchecked))).keySet()).contains("column cost_timesheet.time_entries.entry_status");
    }

    static final Pattern REFERENCES_AUDIT_ENTRIES = Pattern.compile("REFERENCES\\s+platform_audit\\.audit_entries\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern EVENT_ID_KEY = Pattern.compile("CONSTRAINT\\s+uk_audit_entries_event_id_created_at\\s+UNIQUE\\s*\\(\\s*event_id\\s*,\\s*created_at\\s*\\)", Pattern.CASE_INSENSITIVE);

    /** ADR-0048 guard 3: audit entries are referred to by id alone and no table points at them; the event key is exactly (event_id, created_at). */
    static List<String> auditReferenceProblems(Map<String, String> migrations) {
        List<String> problems = new ArrayList<>();
        migrations.forEach((file, sql) -> {
            if (REFERENCES_AUDIT_ENTRIES.matcher(sql).find()) {
                problems.add(file + ": a foreign key to platform_audit.audit_entries (ADR-0048: refer to an entry by its id alone, without a foreign key)");
            }
        });
        if (migrations.values().stream().noneMatch(sql -> EVENT_ID_KEY.matcher(sql).find())) {
            problems.add("platform_audit.audit_entries: uk_audit_entries_event_id_created_at UNIQUE (event_id, created_at) is missing (ADR-0048 decision 2)");
        }
        return problems;
    }

    @Test
    @DisplayName("ADR-0048 guard 3: no foreign key points at audit_entries, and uk_audit_entries_event_id_created_at is exactly (event_id, created_at)")
    void auditEntriesAreReferencedByIdOnly() throws IOException {
        assertThat(auditReferenceProblems(readMigrations())).as(GuardMessage.of("ADR-0048 guard 3", "a foreign key points at audit_entries, or uk_audit_entries_event_id_created_at is not (event_id, created_at)", "store the audit entry id as a plain uuid column without REFERENCES, and keep the event key exactly (event_id, created_at)")).isEmpty();
    }

    @Test
    @DisplayName("injected: a simple and a composite foreign key to audit_entries, and an event key that is not (event_id, created_at), are caught")
    void auditReferenceGuardCatchesInjectedSamples() {
        String key = "CONSTRAINT uk_audit_entries_event_id_created_at UNIQUE (event_id, created_at)";
        assertThat(auditReferenceProblems(Map.of("V1__x.sql", key))).isEmpty();
        assertThat(auditReferenceProblems(Map.of("V1__x.sql", key + "\nCREATE TABLE platform_sample.things (\n    entry_id uuid REFERENCES platform_audit.audit_entries (id)\n);")))
                .anyMatch(p -> p.contains("foreign key to platform_audit.audit_entries"));
        assertThat(auditReferenceProblems(Map.of("V1__x.sql", key + "\nALTER TABLE platform_sample.things ADD CONSTRAINT fk_things_entry FOREIGN KEY (entry_id, entry_at) REFERENCES platform_audit.audit_entries (id, created_at);")))
                .anyMatch(p -> p.contains("foreign key to platform_audit.audit_entries"));
        assertThat(auditReferenceProblems(Map.of("V1__x.sql", key.replace("(event_id, created_at)", "(event_id)")))).anyMatch(p -> p.contains("is missing"));
        assertThat(auditReferenceProblems(Map.of("V1__x.sql", key.replace("(event_id, created_at)", "(created_at, event_id)")))).anyMatch(p -> p.contains("is missing"));
    }

    static List<Path> migrationFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            return files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".sql")).toList();
        }
    }

    static Map<String, String> readMigrations() throws IOException {
        Map<String, String> byVersion = new TreeMap<>(Comparator.naturalOrder());
        for (Path file : migrationFiles()) {
            byVersion.put(file.getFileName().toString(), Files.readString(file));
        }
        return byVersion;
    }

    static Map<String, Schema> model(Map<String, String> migrations) {
        Map<String, Schema> schemas = new TreeMap<>();
        new TreeMap<>(migrations).values().forEach(sql -> apply(schemas, sql));
        return schemas;
    }

    private static void apply(Map<String, Schema> schemas, String sql) {
        Matcher t = CREATE_TABLE.matcher(sql);
        while (t.find()) {
            Schema schema = schemas.computeIfAbsent(t.group(1), s -> new Schema());
            String table = t.group(2);
            Table model = schema.tables.computeIfAbsent(table, n -> new Table());
            if (t.group(4) != null) {
                model.partitionMethod = t.group(4).toUpperCase(Locale.ROOT);
                model.partitionKey = t.group(5).toLowerCase(Locale.ROOT);
            }
            for (String raw : t.group(3).split(",\\s*\\n")) {
                String line = raw.trim().replaceAll("\\s+", " ");
                String first = line.split(" ")[0].toLowerCase(Locale.ROOT);
                if (first.isEmpty()) continue;
                if (KEYWORDS.contains(first)) {
                    tableConstraint(table, model, line);
                } else {
                    column(table, model, first, line.substring(first.length()).trim());
                }
            }
        }
        Matcher idx = Pattern.compile("CREATE\\s+(UNIQUE\\s+)?INDEX\\s+(?:CONCURRENTLY\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?"
                + "(?:([a-z0-9_]+)\\s+)?ON\\s+(?:ONLY\\s+)?([a-z0-9_]+)\\.([a-z0-9_]+)"
                + "(?:\\s+USING\\s+[a-z0-9_]+)?\\s*\\(([^;]*)", Pattern.CASE_INSENSITIVE).matcher(sql);
        while (idx.find()) {
            Schema schema = schemas.computeIfAbsent(idx.group(3), s -> new Schema());
            String name = idx.group(2);
            if (name == null) {
                String columns = idx.group(5).substring(0, idx.group(5).indexOf(')'));
                String joined = Stream.of(columns.split(",")).map(String::trim)
                        .map(part -> part.split("\\s+")[0]).collect(java.util.stream.Collectors.joining("_"));
                name = automaticName(idx.group(4) + "_" + joined, "idx", schema.indexes.keySet());
            }
            schema.indexes.put(name, idx.group(4));
            schema.indexKinds.put(name, idx.group(1) == null ? "index" : "unique");
        }
        Matcher alter = Pattern.compile("ALTER TABLE\\s+([a-z0-9_]+)\\.([a-z0-9_]+)\\s+(.*?);", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(sql);
        while (alter.find()) {
            Table model = schemas.computeIfAbsent(alter.group(1), s -> new Schema()).tables.computeIfAbsent(alter.group(2), n -> new Table());
            String table = alter.group(2);
            for (String action : alter.group(3).split(",\\s*(?=(ADD|RENAME|DROP|ALTER)\\b)")) {
                String a = action.trim().replaceAll("\\s+", " ");
                Matcher m;
                if ((m = Pattern.compile("(?i)ADD COLUMN (?:IF NOT EXISTS )?([a-z0-9_]+) (.*)").matcher(a)).matches()) {
                    column(table, model, m.group(1), m.group(2));
                } else if ((m = Pattern.compile("(?i)ADD ((?:CONSTRAINT|PRIMARY KEY|FOREIGN KEY|UNIQUE|CHECK|EXCLUDE)\\b.*)").matcher(a)).matches()) {
                    tableConstraint(table, model, m.group(1));
                } else if ((m = Pattern.compile("(?i)RENAME COLUMN ([a-z0-9_]+) TO ([a-z0-9_]+)").matcher(a)).matches()) {
                    Column c = model.columns.remove(m.group(1));
                    if (c != null) model.columns.put(m.group(2), new Column(m.group(2), c.type(), c.checked()));
                } else if ((m = Pattern.compile("(?i)RENAME CONSTRAINT ([a-z0-9_]+) TO ([a-z0-9_]+)").matcher(a)).matches()) {
                    Constraint c = model.constraints.remove(m.group(1));
                    if (c != null) model.constraints.put(m.group(2), new Constraint(c.kind(), m.group(2), c.inlineCheck()));
                } else if ((m = Pattern.compile("(?i)RENAME TO ([a-z0-9_]+)").matcher(a)).matches()) {
                    Schema renamed = schemas.get(alter.group(1)); String newName = m.group(1);
                    renamed.tables.put(newName, renamed.tables.remove(table));
                    renamed.indexes.replaceAll((index, owner) -> owner.equals(table) ? newName : owner);
                } else if ((m = Pattern.compile("(?i)DROP CONSTRAINT (?:IF EXISTS )?([a-z0-9_]+).*").matcher(a)).matches()) {
                    model.constraints.remove(m.group(1));
                } else if ((m = Pattern.compile("(?i)DROP COLUMN (?:IF EXISTS )?([a-z0-9_]+).*").matcher(a)).matches()) {
                    model.columns.remove(m.group(1));
                }
            }
        }
        Matcher renameIndex = Pattern.compile("ALTER INDEX\\s+([a-z0-9_]+)\\.([a-z0-9_]+)\\s+RENAME TO\\s+([a-z0-9_]+)", Pattern.CASE_INSENSITIVE).matcher(sql);
        while (renameIndex.find()) {
            Schema schema = schemas.computeIfAbsent(renameIndex.group(1), s -> new Schema());
            String table = schema.indexes.remove(renameIndex.group(2));
            String kind = schema.indexKinds.remove(renameIndex.group(2));
            if (table != null) {
                schema.indexes.put(renameIndex.group(3), table);
                schema.indexKinds.put(renameIndex.group(3), kind);
            }
        }
        Matcher dropTable = Pattern.compile("DROP TABLE\\s+(?:IF EXISTS\\s+)?([a-z0-9_]+)\\.([a-z0-9_]+)", Pattern.CASE_INSENSITIVE).matcher(sql);
        while (dropTable.find()) {
            Schema schema = schemas.computeIfAbsent(dropTable.group(1), s -> new Schema());
            schema.tables.remove(dropTable.group(2));
            List<String> indexes = schema.indexes.entrySet().stream().filter(e -> e.getValue().equals(dropTable.group(2))).map(Map.Entry::getKey).toList();
            indexes.forEach(i -> {
                schema.indexes.remove(i);
                schema.indexKinds.remove(i);
            });
        }
        Matcher dropIndex = Pattern.compile("DROP INDEX\\s+(?:IF EXISTS\\s+)?([a-z0-9_]+)\\.([a-z0-9_]+)", Pattern.CASE_INSENSITIVE).matcher(sql);
        while (dropIndex.find()) {
            Schema schema = schemas.computeIfAbsent(dropIndex.group(1), s -> new Schema());
            schema.indexes.remove(dropIndex.group(2));
            schema.indexKinds.remove(dropIndex.group(2));
        }
    }

    private static void column(String table, Table model, String name, String definition) {
        String lower = definition.toLowerCase(Locale.ROOT);
        String type = lower.split(" ")[0].replaceAll("\\(.*", "");
        if (lower.startsWith("timestamp with time zone")) {
            type = "timestamptz";
        }
        if (lower.startsWith("character varying")) {
            type = "varchar";
        }
        model.columns.put(name, new Column(name, type, lower.contains("check")));
        // PostgreSQL's default names for inline constraints: <table>_pkey, <table>_<column>_fkey, <table>_<column>_key
        if (lower.contains("primary key")) {
            model.primaryKey.clear();
            model.primaryKey.add(name);
            putConstraint(model, "pk", named(lower, table + "_pkey"), false);
        }
        if (lower.contains("references")) {
            putConstraint(model, "fk", named(lower, table + "_" + name + "_fkey"), false);
        }
        if (lower.matches(".*\\bunique\\b.*")) {
            putConstraint(model, "uk", named(lower, table + "_" + name + "_key"), false);
        }
        if (lower.contains("check")) {
            putConstraint(model, "ck", named(lower, table + "_" + name + "_check"), true);
        }
    }

    private static String named(String definition, String fallback) {
        Matcher m = Pattern.compile("constraint ([a-z0-9_]+)").matcher(definition);
        return m.find() ? m.group(1) : fallback;
    }

    private static void tableConstraint(String table, Table model, String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile("constraint ([a-z0-9_]+) (primary key|foreign key|unique|check|exclude)").matcher(lower);
        String kind;
        String name;
        if (m.find()) {
            name = m.group(1);
            kind = m.group(2);
        } else {
            kind = lower.startsWith("primary key") ? "primary key" : lower.startsWith("foreign key") ? "foreign key" : lower.startsWith("unique") ? "unique" : "check";
            Matcher cols = Pattern.compile("\\(([^)]*)\\)").matcher(lower);
            String joined = cols.find() ? cols.group(1).replaceAll("\\s", "").replace(",", "_") : "";
            if (kind.equals("check")) {
                // PostgreSQL names a CHECK after its column only when the de-duplicated expression refers to exactly one column; otherwise <table>_check.
                List<String> referenced = DdlIdentifierLengths.identifiers(lower).stream().filter(model.columns::containsKey).distinct().toList();
                joined = referenced.size() == 1 ? referenced.get(0) : "";
            }
            String suffix = switch (kind) {
                case "primary key" -> "pkey";
                case "foreign key" -> "fkey";
                case "unique" -> "key";
                default -> "check";
            };
            String base = table + (kind.equals("primary key") || joined.isEmpty() ? "" : "_" + joined);
            name = automaticName(base, suffix, model.constraints.keySet());
        }
        if (kind.equals("check")) {
            // A named table-level CHECK that lists a column's values (NAMING_STANDARD §2.4: ck_<table>_<column>) checks the enum column as well as an inline one.
            for (Column c : List.copyOf(model.columns.values())) {
                if (!c.checked() && Pattern.compile("\\b" + c.name() + "\\s+in\\s*\\(").matcher(lower).find()) {
                    model.columns.put(c.name(), new Column(c.name(), c.type(), true));
                }
            }
        }
        if (kind.equals("primary key")) {
            Matcher keyColumns = Pattern.compile("primary key\\s*\\(([^)]*)\\)").matcher(lower);
            if (keyColumns.find()) {
                model.primaryKey.clear();
                for (String keyColumn : keyColumns.group(1).split(",")) {
                    model.primaryKey.add(keyColumn.trim());
                }
            }
        }
        putConstraint(model, switch (kind) {
            case "primary key" -> "pk";
            case "foreign key" -> "fk";
            case "unique" -> "uk";
            case "exclude" -> "ex";
            default -> "ck";
        }, name, false);
    }

    private static void putConstraint(Table model, String kind, String name, boolean inlineCheck) {
        model.constraints.put(name, new Constraint(kind, name, inlineCheck));
    }

    // 保留截断前候选名供 63 字节检查；依赖数据库截断本身就是违规。
    private static String automaticName(String base, String suffix, Set<String> occupied) {
        String name = base + "_" + suffix;
        for (int serial = 1; occupied.contains(name); serial++) {
            name = base + "_" + suffix + serial;
        }
        return name;
    }

    static Map<String, String> violations(Map<String, Schema> schemas) {
        Map<String, String> v = new TreeMap<>();
        schemas.forEach((schemaName, schema) -> {
            schema.tables.forEach((table, model) -> {
                String q = schemaName + "." + table;
                if (FRAMEWORK_TABLES.contains(q)) {
                    return;
                }
                for (Column c : model.columns.values()) {
                    String problem = classWordProblem(c);
                    if (problem != null) {
                        v.put("column " + q + "." + c.name(), problem);
                    }
                }
                for (Constraint c : model.constraints.values()) {
                    if (c.inlineCheck()) {
                        continue;
                    }
                    String expected = switch (c.kind()) {
                        case "pk" -> "pk_" + table;
                        case "fk" -> "fk_" + table + "_<column>";
                        case "uk" -> "uk_" + table + "_<column>";
                        case "ex" -> "ex_" + table + "_<rule>";
                        default -> "ck_" + table + "_<rule>";
                    };
                    boolean ok = c.kind().equals("pk") ? c.name().equals(expected) : c.name().startsWith(expected.replace("<column>", "").replace("<rule>", ""));
                    if (!ok) {
                        v.put("constraint " + q + "." + c.name(), "name it " + expected + " (CONSTRAINT <name> ...)");
                    }
                }
            });
            schema.indexes.forEach((index, table) -> {
                if (FRAMEWORK_TABLES.contains(schemaName + "." + table)) {
                    return;
                }
                String prefix = ("unique".equals(schema.indexKinds.get(index)) ? "uk_" : "ix_") + table + "_";
                if (!index.startsWith(prefix)) {
                    v.put("index " + schemaName + "." + index, "name it " + prefix + "<column>");
                }
            });
        });
        return v;
    }

    private static String classWordProblem(Column c) {
        if (STANDALONE.containsKey(c.name())) {
            return STANDALONE.get(c.name()).contains(c.type()) ? null : c.name() + " must be one of " + STANDALONE.get(c.name());
        }
        for (ClassWord w : ClassWord.values()) {
            if (w.names(c.name())) {
                if (!w.types.contains(c.type())) {
                    return "class word " + w.affix + " needs type " + w.types + ", found " + c.type();
                }
                if (CHECKED_ENUMS.contains(w.affix) && !c.checked()) {
                    return w.affix + " needs text with a CHECK listing the enum names";
                }
                return null;
            }
        }
        return "must end with a class word (" + String.join(" ", Stream.of(ClassWord.values()).map(w -> w.affix).toList()) + ") — NAMING_STANDARD §2.4";
    }

    static Map<String, String> readBaseline() throws IOException {
        Map<String, String> entries = new TreeMap<>();
        for (String line : Files.readAllLines(BASELINE)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s*\\|\\s*", 2);
            assertThat(parts).as(GuardMessage.of("D2 (NAMING_STANDARD.md §2.4)", "a line of " + BASELINE + " is not <violation> | <reason>: " + line, "write the line as <violation> | <reason>")).hasSize(2);
            assertThat(parts[1]).as(GuardMessage.of("D2 (NAMING_STANDARD.md §2.4)", parts[0] + " in " + BASELINE + " has no reason", "write the reason after the | in " + BASELINE)).isNotBlank();
            entries.put(parts[0].trim(), parts[1].trim());
        }
        return entries;
    }

    static Map<String, ClassificationRegister.Row> readClassification() throws IOException {
        return ClassificationRegister.read();
    }

    static List<String> classificationProblems(Map<String, Schema> schemas, Map<String, ClassificationRegister.Row> classified) {
        Set<String> existing = new LinkedHashSet<>();
        schemas.forEach((s, schema) -> schema.tables.forEach((t, model) -> model.columns.keySet().forEach(c -> existing.add(s + "." + t + "." + c))));
        return ClassificationRegister.problems(existing, classified);
    }

    static List<String> sampleViolations(String schema, String sql) {
        List<String> problems = new ArrayList<>();
        check(Path.of("V0__" + schema + "_sample.sql"), schema, sql, problems);
        return problems;
    }

    private static void check(Path file, String schema, String sql, List<String> problems) {
        if (LOCAL_TIMESTAMP.matcher(sql.replaceAll("(?m)^\\s*--.*$", "").replaceAll("'[^']*'", "''")).find()) {
            problems.add(file.getFileName() + ": timestamp without time zone is forbidden; instants are timestamptz, business days are date");
        }
        Matcher t = CREATE_TABLE.matcher(sql);
        while (t.find()) {
            String tableSchema = t.group(1);
            String table = t.group(2);
            String where = file.getFileName() + " " + tableSchema + "." + table;
            if (!tableSchema.equals(schema)) {
                problems.add(where + ": a module may only create tables in its own schema " + schema);
            }
            name(where, "table", table, problems);
            List<String> columns = new ArrayList<>();
            for (String raw : t.group(3).split(",\\s*\\n")) {
                String line = raw.trim();
                String rawColumn = line.split("\\s+")[0];
                String column = rawColumn.toLowerCase(Locale.ROOT);
                if (!rawColumn.equals(column) && !KEYWORDS.contains(column)) {
                    problems.add(where + ": column " + rawColumn + " must be lower snake_case (PostgreSQL folds case silently)");
                }
                if (column.isEmpty() || KEYWORDS.contains(column)) {
                    continue;
                }
                columns.add(column);
                name(where, "column " + column, column, problems);
                String type = line.substring(column.length()).trim().toLowerCase(Locale.ROOT);
                if (type.startsWith("boolean") && !(column.startsWith("is_") || column.startsWith("has_"))) {
                    problems.add(where + ": boolean column " + column + " must start with is_ or has_");
                }
                boolean listedByNamedCheck = Pattern.compile("(?i)constraint\\s+\\w+\\s+check\\s*\\(\\s*" + column + "\\s+in\\s*\\(").matcher(t.group(3)).find();
                if ((column.equals("status") || column.endsWith("_status") || column.endsWith("_policy")) && !type.contains("check") && !listedByNamedCheck) {
                    problems.add(where + ": " + column + " must be text with a CHECK listing the enum names");
                }
                if (column.endsWith("_amount") && !t.group(3).contains(column.replace("_amount", "_currency"))) {
                    problems.add(where + ": " + column + " needs a sibling " + column.replace("_amount", "_currency") + " (Money = amount + currency)");
                }
                if (!COMMON_COLUMNS.contains(column) && !sql.contains("COMMENT ON COLUMN " + tableSchema + "." + table + "." + column + " IS")) {
                    problems.add(where + ": column " + column + " has no COMMENT (data dictionary)");
                }
            }
            for (String audit : List.of("created_at", "updated_at")) {
                if (!columns.contains(audit)) {
                    problems.add(where + ": missing audit column " + audit);
                }
            }
            if (!sql.contains("COMMENT ON TABLE " + tableSchema + "." + table + " IS")) {
                problems.add(where + ": table has no COMMENT (data dictionary)");
            }
        }
    }

    private static void name(String where, String what, String identifier, List<String> problems) {
        if (!SNAKE.matcher(identifier).matches()) {
            problems.add(where + ": " + what + " must be English snake_case");
        }
        for (String part : identifier.split("_")) {
            if (BANNED.contains(part)) {
                problems.add(where + ": " + what + " uses legacy/vague word '" + part + "' (see GLOSSARY.md)");
            }
        }
    }

    @Test
    @DisplayName("exclusion constraints are named ex_<table>_<rule>, whether they are created with the table or added later")
    void exclusionConstraintsAreNamedEx() {
        String sql = """
                CREATE TABLE cost_timesheet.spans (
                    id          uuid        NOT NULL,
                    owner_id    bigint      NOT NULL,
                    from_date   date        NOT NULL,
                    to_date     date,
                    created_at  timestamptz NOT NULL,
                    updated_at  timestamptz NOT NULL,
                    CONSTRAINT pk_spans PRIMARY KEY (id),
                    CONSTRAINT overlap_spans EXCLUDE USING gist (owner_id WITH =, daterange(from_date, to_date) WITH &&)
                );
                ALTER TABLE cost_timesheet.spans ADD CONSTRAINT spans_other EXCLUDE USING gist (owner_id WITH =, daterange(from_date, to_date) WITH &&);
                """;
        assertThat(violations(model(Map.of("V20990101000000__cost_timesheet_x.sql", sql))).keySet()).contains(
                "constraint cost_timesheet.spans.overlap_spans", "constraint cost_timesheet.spans.spans_other");
        assertThat(violations(model(Map.of("V20990101000000__cost_timesheet_x.sql",
                sql.replace("overlap_spans", "ex_spans_overlap").replace("spans_other", "ex_spans_other"))))).isEmpty();
    }

    static final String GOOD = """
            CREATE TABLE platform_sample.things (
                id         bigint      GENERATED ALWAYS AS IDENTITY,
                sample_name text       NOT NULL,
                created_at timestamptz NOT NULL DEFAULT now(),
                updated_at timestamptz NOT NULL DEFAULT now(),
                version    bigint      NOT NULL DEFAULT 0,
                CONSTRAINT pk_things PRIMARY KEY (id)
            );
            COMMENT ON TABLE platform_sample.things IS 'x';
            COMMENT ON COLUMN platform_sample.things.sample_name IS 'x';
            """;

    @Test
    void commonColumnsNeedNoComment() {
        assertThat(sampleViolations("platform_sample", GOOD)).noneMatch(p -> p.contains("has no COMMENT"));
    }

    @Test
    void otherColumnsStillNeedAComment() {
        assertThat(sampleViolations("platform_sample", GOOD.replace("COMMENT ON COLUMN platform_sample.things.sample_name IS 'x';", "")))
                .anyMatch(p -> p.contains("column sample_name has no COMMENT"));
    }

    @Test
    void timestampWithoutTimeZoneIsRejected() {
        String column = "sample_name text       NOT NULL,";
        assertThat(sampleViolations("platform_sample", GOOD.replace(column, column + "\n                seen_at    timestamp without time zone,")))
                .anyMatch(p -> p.contains("timestamp without time zone"));
        assertThat(sampleViolations("platform_sample", GOOD.replace(column, column + "\n                seen_at    timestamp,")))
                .anyMatch(p -> p.contains("timestamp without time zone"));
        assertThat(sampleViolations("platform_sample", GOOD.replace(column, column + "\n                seen_at    timestamp with time zone,")))
                .noneMatch(p -> p.contains("timestamp without time zone"));
    }

    static final Path TYPED_TEXT_KEY_EXCEPTIONS = Path.of("architecture/typed-text-key-exceptions.txt");
    static final Set<String> TYPED_TEXT_KEY_NAMES = Set.of("subject_key", "deduplication_key");
    static final Pattern TYPED_TEXT_COMMENT = Pattern.compile("\\{(类型|type)\\}\\s*[:：]");
    static final Pattern SENTINEL = Pattern.compile("(?i)coalesce\\s*\\(");

    /** ADR-0033 guard 8 (DDL part): a unique key, unique index or exclusion never hides a null behind a constant, and no column keeps a "{type}:{id}" text. */
    static List<String> sentinelProblems(Map<String, String> migrations, Set<String> excepted) {
        List<String> problems = new ArrayList<>();
        Set<String> found = new LinkedHashSet<>();
        for (String sql : migrations.values()) {
            Matcher index = Pattern.compile("CREATE UNIQUE INDEX[^;]*;", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
            while (index.find()) {
                if (SENTINEL.matcher(index.group()).find()) {
                    problems.add("unique index with a COALESCE sentinel: " + index.group().replaceAll("\\s+", " ").strip());
                }
            }
            Matcher table = CREATE_TABLE.matcher(sql);
            while (table.find()) {
                String qualified = table.group(1) + "." + table.group(2);
                for (String raw : table.group(3).split(",\\s*\\n")) {
                    String line = raw.trim().replaceAll("\\s+", " ");
                    if (line.matches("(?i)(CONSTRAINT [a-z0-9_]+ )?(UNIQUE|EXCLUDE).*") && SENTINEL.matcher(line).find()) {
                        problems.add(qualified + ": unique or exclusion constraint with a COALESCE sentinel: " + line);
                    }
                    String column = line.split(" ")[0].toLowerCase(Locale.ROOT);
                    if (TYPED_TEXT_KEY_NAMES.contains(column)) {
                        found.add(qualified + "." + column);
                    }
                }
            }
            Matcher comment = Pattern.compile("COMMENT ON COLUMN ([a-z0-9_]+\\.[a-z0-9_]+\\.[a-z0-9_]+) IS '((?:[^']|'')*)'", Pattern.CASE_INSENSITIVE).matcher(sql);
            while (comment.find()) {
                if (TYPED_TEXT_COMMENT.matcher(comment.group(2)).find()) {
                    found.add(comment.group(1));
                }
            }
        }
        found.stream().filter(column -> !excepted.contains(column))
                .forEach(column -> problems.add(column + ": a text that holds a kind and an id; use a kind column and an id column (ADR-0033 section 7 item 2)"));
        excepted.stream().filter(column -> !found.contains(column))
                .forEach(column -> problems.add(column + ": listed in " + TYPED_TEXT_KEY_EXCEPTIONS + " but the column is gone; delete the line"));
        return problems;
    }

    static Set<String> typedTextKeyExceptions() throws IOException {
        Set<String> excepted = new LinkedHashSet<>();
        for (String line : Files.readAllLines(TYPED_TEXT_KEY_EXCEPTIONS)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                int hash = line.indexOf('#');
                assertThat(hash).as(GuardMessage.of("ADR-0033 guard 8", "a line of " + TYPED_TEXT_KEY_EXCEPTIONS + " has no # reason: " + line, "write <schema.table.column>  # <reason>")).isPositive();
                assertThat(line.substring(hash + 1)).as(GuardMessage.of("ADR-0033 guard 8", "the reason of a line of " + TYPED_TEXT_KEY_EXCEPTIONS + " is empty: " + line, "write the reason after #")).isNotBlank();
                excepted.add(line.substring(0, hash).strip());
            }
        }
        return excepted;
    }

    @Test
    @DisplayName("ADR-0033 guard 8: no COALESCE sentinel in a unique key, unique index or exclusion, and no '{type}:{id}' text column outside the listed exception")
    void noSentinelsAndNoTypedTextKeys() throws IOException {
        assertThat(sentinelProblems(readMigrations(), typedTextKeyExceptions())).as(GuardMessage.of("ADR-0033 guard 8", "a unique key, unique index or exclusion has a COALESCE sentinel, or a text column holds a kind and an id", "use a partial unique index or separate kind and id columns; list a typed text key in " + TYPED_TEXT_KEY_EXCEPTIONS + " only with a reason")).isEmpty();
    }

    @Test
    @DisplayName("injected: a COALESCE(…, 0) unique index, a COALESCE exclusion, a new subject_key column, a '{type}:' comment and a stale exception are caught")
    void sentinelGuardCatchesInjectedSamples() {
        String table = """
                CREATE TABLE platform_sample.things (
                    id uuid PRIMARY KEY,
                    owner_id uuid,
                    subject_key text NOT NULL,
                    other_name text,
                    CONSTRAINT uk_things_owner UNIQUE (owner_id, (coalesce(other_name, ''))),
                    CONSTRAINT ex_things EXCLUDE USING gist (coalesce(owner_id, '00000000-0000-0000-0000-000000000000') WITH =)
                );
                CREATE UNIQUE INDEX uk_things_owner_id ON platform_sample.things (COALESCE(owner_id, '00000000-0000-0000-0000-000000000000'));
                COMMENT ON COLUMN platform_sample.things.other_name IS '对象键：{类型}:{id}';
                """;
        List<String> problems = sentinelProblems(Map.of("V1__x.sql", table), Set.of("platform_sample.things.gone_key"));

        assertThat(problems).anyMatch(p -> p.startsWith("unique index with a COALESCE sentinel"))
                .anyMatch(p -> p.contains("platform_sample.things: unique or exclusion constraint with a COALESCE sentinel: CONSTRAINT uk_things_owner"))
                .anyMatch(p -> p.contains("platform_sample.things: unique or exclusion constraint with a COALESCE sentinel: CONSTRAINT ex_things"))
                .anyMatch(p -> p.startsWith("platform_sample.things.subject_key: a text that holds a kind and an id"))
                .anyMatch(p -> p.startsWith("platform_sample.things.other_name: a text that holds a kind and an id"))
                .anyMatch(p -> p.startsWith("platform_sample.things.gone_key: listed in"));
        assertThat(sentinelProblems(Map.of("V1__x.sql", table.replace("subject_key", "subject_kind")), Set.of()))
                .noneMatch(p -> p.contains("subject_kind"));
    }
}
