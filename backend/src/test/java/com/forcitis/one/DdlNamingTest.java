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
    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    static final Path BASELINE = Path.of("architecture/ddl-naming-baseline.txt");
    static final Path CLASSIFICATION = Path.of("architecture/data-classification.txt");
    static final Pattern FILE = Pattern.compile("V(\\d{14})__([a-z]+_[a-z]+)_[a-z0-9_]+\\.sql");
    static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE\\s+([a-z0-9_]+)\\.([a-z0-9_]+)\\s*\\((.*?)\\n\\);", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    static final Pattern SNAKE = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");
    static final Set<String> BANNED = Set.of("psa", "cuvalue", "manhour", "man_hour", "works", "biget", "arbad", "ohproject",
            "flag", "info", "data", "temp", "tmp", "bd", "pm", "u8c", "fst");
    static final Set<String> KEYWORDS = Set.of("primary", "constraint", "unique", "foreign", "check");

    // Column names and types are fixed by the framework that owns the table (Spring Session JDBC, Spring Modulith, ShedLock).
    static final Set<String> FRAMEWORK_TABLES = Set.of("platform_identity.sessions", "platform_identity.sessions_attributes",
            "platform_integration.event_publication", "platform_scheduling.scheduler_locks");

    enum ClassWord {
        ID("_id", "bigint", "uuid", "text"),
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
        BY("_by", "bigint"),
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
    static final Pattern SENSITIVE_NAME = Pattern.compile("(^|_)(password|secret|totp|bank|salary|wage|daily_rate|id_card|identity_number|mobile|phone|email|birth|gender|address|attribute_bytes)(_|$)");
    static final Set<String> LEVELS = Set.of("PUBLIC", "INTERNAL", "SENSITIVE", "RESTRICTED");

    record Column(String name, String type, boolean checked) {}

    record Constraint(String kind, String name, boolean inlineCheck) {}

    static final class Table {
        final Map<String, Column> columns = new LinkedHashMap<>();
        final Map<String, Constraint> constraints = new LinkedHashMap<>();
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
            throw new AssertionError("DDL violates NAMING_STANDARD.md §2.3:\n  " + String.join("\n  ", problems));
        }
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
            throw new AssertionError("DDL violates NAMING_STANDARD.md §2.4 (class words / constraint and index names):\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    @DisplayName("every sensitive-looking column is classified SENSITIVE or RESTRICTED, and every classified column exists (NAMING_STANDARD §2.5)")
    void sensitiveColumnsAreClassified() throws IOException {
        List<String> problems = classificationProblems(model(readMigrations()), readClassification());
        if (!problems.isEmpty()) {
            throw new AssertionError("Data classification (" + CLASSIFICATION + ", NAMING_STANDARD.md §2.5):\n  " + String.join("\n  ", problems));
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
            for (String raw : t.group(3).split(",\\s*\\n")) {
                String line = raw.trim().replaceAll("\\s+", " ");
                String first = line.split(" ")[0].toLowerCase(Locale.ROOT);
                if (first.isEmpty()) {
                    continue;
                }
                if (KEYWORDS.contains(first)) {
                    tableConstraint(table, model, line);
                } else {
                    column(table, model, first, line.substring(first.length()).trim());
                }
            }
        }
        Matcher idx = Pattern.compile("CREATE (UNIQUE )?INDEX\\s+([a-z0-9_]+)\\s+ON\\s+([a-z0-9_]+)\\.([a-z0-9_]+)", Pattern.CASE_INSENSITIVE).matcher(sql);
        while (idx.find()) {
            Schema schema = schemas.computeIfAbsent(idx.group(3), s -> new Schema());
            schema.indexes.put(idx.group(2), idx.group(4));
            schema.indexKinds.put(idx.group(2), idx.group(1) == null ? "index" : "unique");
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
                } else if ((m = Pattern.compile("(?i)ADD (CONSTRAINT .*)").matcher(a)).matches()) {
                    tableConstraint(table, model, m.group(1));
                } else if ((m = Pattern.compile("(?i)RENAME COLUMN ([a-z0-9_]+) TO ([a-z0-9_]+)").matcher(a)).matches()) {
                    Column c = model.columns.remove(m.group(1));
                    if (c != null) {
                        model.columns.put(m.group(2), new Column(m.group(2), c.type(), c.checked()));
                    }
                } else if ((m = Pattern.compile("(?i)RENAME CONSTRAINT ([a-z0-9_]+) TO ([a-z0-9_]+)").matcher(a)).matches()) {
                    Constraint c = model.constraints.remove(m.group(1));
                    if (c != null) {
                        model.constraints.put(m.group(2), new Constraint(c.kind(), m.group(2), c.inlineCheck()));
                    }
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
        Matcher m = Pattern.compile("constraint ([a-z0-9_]+) (primary key|foreign key|unique|check)").matcher(lower);
        String kind;
        String name;
        if (m.find()) {
            name = m.group(1);
            kind = m.group(2);
        } else {
            kind = lower.startsWith("primary key") ? "primary key" : lower.startsWith("foreign key") ? "foreign key" : lower.startsWith("unique") ? "unique" : "check";
            Matcher cols = Pattern.compile("\\(([^)]*)\\)").matcher(lower);
            String joined = cols.find() ? cols.group(1).replaceAll("\\s", "").replace(",", "_") : "";
            name = switch (kind) {
                case "primary key" -> table + "_pkey";
                case "foreign key" -> table + "_" + joined + "_fkey";
                case "unique" -> table + "_" + joined + "_key";
                default -> table + "_check";
            };
        }
        putConstraint(model, switch (kind) {
            case "primary key" -> "pk";
            case "foreign key" -> "fk";
            case "unique" -> "uk";
            default -> "ck";
        }, name, false);
    }

    private static void putConstraint(Table model, String kind, String name, boolean inlineCheck) {
        model.constraints.put(name, new Constraint(kind, name, inlineCheck));
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
            assertThat(parts).as("%s: <violation> | <reason>: %s", BASELINE, line).hasSize(2);
            assertThat(parts[1]).as("%s needs a reason", parts[0]).isNotBlank();
            entries.put(parts[0].trim(), parts[1].trim());
        }
        return entries;
    }

    static Map<String, String> readClassification() throws IOException {
        Map<String, String> entries = new TreeMap<>();
        for (String line : Files.readAllLines(CLASSIFICATION)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s*\\|\\s*", 3);
            assertThat(parts).as("%s: <schema.table.column> | <LEVEL> | <reason>: %s", CLASSIFICATION, line).hasSize(3);
            assertThat(LEVELS).as("level of %s", parts[0]).contains(parts[1].trim());
            entries.put(parts[0].trim(), parts[1].trim());
        }
        return entries;
    }

    static List<String> classificationProblems(Map<String, Schema> schemas, Map<String, String> classified) {
        List<String> problems = new ArrayList<>();
        Set<String> existing = new LinkedHashSet<>();
        schemas.forEach((s, schema) -> schema.tables.forEach((t, model) -> model.columns.keySet().forEach(c -> existing.add(s + "." + t + "." + c))));
        for (String column : existing) {
            String name = column.substring(column.lastIndexOf('.') + 1);
            String level = classified.get(column);
            if (SENSITIVE_NAME.matcher(name).find() && !("SENSITIVE".equals(level) || "RESTRICTED".equals(level))) {
                problems.add(column + " looks sensitive: classify it SENSITIVE or RESTRICTED (or explain INTERNAL/PUBLIC is not possible: rename it)");
            }
        }
        for (String column : classified.keySet()) {
            if (!existing.contains(column)) {
                problems.add(column + " is classified but does not exist: remove the stale line");
            }
        }
        return problems;
    }

    private static void check(Path file, String schema, String sql, List<String> problems) {
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
                if ((column.equals("status") || column.endsWith("_status") || column.endsWith("_policy")) && !type.contains("check")) {
                    problems.add(where + ": " + column + " must be text with a CHECK listing the enum names");
                }
                if (column.endsWith("_amount") && !t.group(3).contains(column.replace("_amount", "_currency"))) {
                    problems.add(where + ": " + column + " needs a sibling " + column.replace("_amount", "_currency") + " (Money = amount + currency)");
                }
                if (!sql.contains("COMMENT ON COLUMN " + tableSchema + "." + table + "." + column + " IS")) {
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
}
