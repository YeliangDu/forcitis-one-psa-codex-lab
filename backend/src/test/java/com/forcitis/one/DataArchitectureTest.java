package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.yaml.snakeyaml.Yaml;

/**
 * Data scorecard guards D1..D10 and the data access registry (K15). The structure is read from the database the migrations produce, not
 * parsed from SQL text; the registry (architecture/data-access-registry.yaml) is checked against it and against the SQL in src/main/java.
 */
class DataArchitectureTest {
    static final Path REGISTRY = Path.of("architecture/data-access-registry.yaml");
    static final Path MAIN = Path.of("src/main/java");
    static final Pattern GRAIN = Pattern.compile(".*粒度：每.+(一行|一条).*", Pattern.DOTALL);
    static final Pattern DURATION = Pattern.compile("\\d+[dy]");
    static final Pattern DELIMITED = Pattern.compile("逗号|竖线|分号|分隔|拼接");
    static final Pattern SENSITIVE_PERSON = Pattern.compile("(^|_)(display_name|login_name|wecom|recipient|account_name|principal|employee_code)(_|$)");
    static final Set<String> ROLES = Set.of("AGGREGATE_ROOT", "AGGREGATE_CHILD", "BUSINESS_DOCUMENT", "DIMENSION_VERSIONED", "SNAPSHOT", "CONFIG",
            "CONFIG_VERSIONED", "LOG_APPEND_ONLY", "QUEUE", "ONE_TIME", "LINEAGE", "COUNTER", "IDEMPOTENCY", "FRAMEWORK", "PLACEHOLDER");
    static final Set<String> MUTABILITY = Set.of("MUTABLE", "CONDITIONAL_UPDATE", "REPLACE_SET", "APPEND_ONLY", "INSERT_DELETE", "MIGRATION_ONLY", "FRAMEWORK");
    static final Set<String> TIMELESS = Set.of("PERMANENT", "BUSINESS_RECORD", "CURRENT_STATE", "FRAMEWORK");
    static final Set<String> SOURCES = Set.of("NEW", "A8", "MIGRATION", "FRAMEWORK");
    static final Set<String> TRACES = Set.of("legacy_numbers", "ONE_OFF_IMPORT");

    record Column(String name, String type, boolean notNull, String comment) {
    }

    record Constraint(String name, char kind, List<String> columns, String target, boolean deferrable) {
    }

    static final class Table {
        final String name;
        String comment;
        final Map<String, Column> columns = new LinkedHashMap<>();
        final List<Constraint> constraints = new ArrayList<>();
        final Set<String> triggers = new TreeSet<>();

        Table(String name) {
            this.name = name;
        }

        String schema() {
            return name.substring(0, name.indexOf('.'));
        }

        String bare() {
            return name.substring(name.indexOf('.') + 1);
        }
    }

    record Gap(String rule, String column, String owner, String ref, String note) {
    }

    record Entry(String table, Map<String, Object> raw, List<Gap> gaps) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map(String key) {
            Object value = raw.get(key);
            return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        }

        String text(String key) {
            Object value = raw.get(key);
            return value == null ? null : String.valueOf(value);
        }

        @SuppressWarnings("unchecked")
        List<String> list(String key) {
            Object value = raw.get(key);
            return value instanceof List<?> l ? (List<String>) l : List.of();
        }
    }

    static PostgreSQLContainer postgres;
    static Map<String, Table> tables;
    static Map<String, Entry> registry;

    @BeforeAll
    static void migrate() throws Exception {
        postgres = new PostgreSQLContainer("postgres:18");
        postgres.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                .defaultSchema("one_migration").createSchemas(true).load().migrate();
        tables = readTables();
        registry = readRegistry();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    // ---------- the database ----------

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    static Map<String, Table> readTables() throws SQLException {
        Map<String, Table> found = new TreeMap<>();
        try (Connection c = connect()) {
            try (ResultSet rs = c.createStatement().executeQuery("""
                    select n.nspname || '.' || r.relname, obj_description(r.oid, 'pg_class')
                      from pg_class r join pg_namespace n on n.oid = r.relnamespace
                     where r.relkind in ('r', 'p') and n.nspname not like 'pg\\_%' and n.nspname not in ('information_schema', 'public', 'one_migration')
                    """)) {
                while (rs.next()) {
                    Table t = new Table(rs.getString(1));
                    t.comment = rs.getString(2);
                    found.put(t.name, t);
                }
            }
            try (ResultSet rs = c.createStatement().executeQuery("""
                    select n.nspname || '.' || r.relname, a.attname, format_type(a.atttypid, a.atttypmod), a.attnotnull, col_description(r.oid, a.attnum)
                      from pg_attribute a join pg_class r on r.oid = a.attrelid join pg_namespace n on n.oid = r.relnamespace
                     where a.attnum > 0 and not a.attisdropped and r.relkind in ('r', 'p') order by 1, a.attnum
                    """)) {
                while (rs.next()) {
                    Table t = found.get(rs.getString(1));
                    if (t != null) {
                        t.columns.put(rs.getString(2), new Column(rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getString(5)));
                    }
                }
            }
            try (ResultSet rs = c.createStatement().executeQuery("""
                    select n.nspname || '.' || r.relname, k.conname, k.contype::text,
                           array(select a.attname from pg_attribute a where a.attrelid = k.conrelid and a.attnum = any(k.conkey) order by a.attnum),
                           coalesce(k.confrelid::regclass::text, ''), k.condeferrable
                      from pg_constraint k join pg_class r on r.oid = k.conrelid join pg_namespace n on n.oid = r.relnamespace
                    """)) {
                while (rs.next()) {
                    Table t = found.get(rs.getString(1));
                    if (t != null) {
                        t.constraints.add(new Constraint(rs.getString(2), rs.getString(3).charAt(0), List.of((String[]) rs.getArray(4).getArray()),
                                rs.getString(5), rs.getBoolean(6)));
                    }
                }
            }
            try (ResultSet rs = c.createStatement().executeQuery("""
                    select n.nspname || '.' || r.relname, g.tgname
                      from pg_trigger g join pg_class r on r.oid = g.tgrelid join pg_namespace n on n.oid = r.relnamespace where not g.tgisinternal
                    """)) {
                while (rs.next()) {
                    Table t = found.get(rs.getString(1));
                    if (t != null) {
                        t.triggers.add(rs.getString(2));
                    }
                }
            }
        }
        return found;
    }

    // ---------- the registry ----------

    @SuppressWarnings("unchecked")
    static Map<String, Entry> readRegistry() throws IOException {
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(REGISTRY)) {
            root = new Yaml().load(in);
        }
        Map<String, Entry> entries = new TreeMap<>();
        ((Map<String, Map<String, Object>>) root.get("tables")).forEach((table, raw) -> {
            List<Gap> gaps = new ArrayList<>();
            for (Map<String, Object> g : (List<Map<String, Object>>) raw.getOrDefault("gaps", List.of())) {
                gaps.add(new Gap(String.valueOf(g.get("rule")), g.get("column") == null ? null : String.valueOf(g.get("column")),
                        String.valueOf(g.get("owner")), String.valueOf(g.get("ref")), String.valueOf(g.get("note"))));
            }
            entries.put(table, new Entry(table, raw, gaps));
        });
        return entries;
    }

    static Entry entryOf(Map<String, Entry> registry, String table) {
        Entry exact = registry.get(table);
        if (exact != null) {
            return exact;
        }
        return registry.entrySet().stream().filter(e -> e.getKey().endsWith("*") && table.startsWith(e.getKey().substring(0, e.getKey().length() - 1)))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    // ---------- the guards: a violation is "<rule> <table>[.<column>]: why"; gaps in the registry are the only tolerated ones ----------

    record Violation(String rule, String table, String column, String why) {
        String key() {
            return rule + " " + table + (column == null ? "" : "." + column);
        }
    }

    static List<Violation> violations(Map<String, Table> tables, Map<String, Entry> registry, Map<String, String> classification, Set<String> lockNames) {
        List<Violation> v = new ArrayList<>();
        for (Table t : tables.values()) {
            Entry e = entryOf(registry, t.name);
            if (e == null) {
                continue;
            }
            String role = e.text("role");
            String mutability = e.text("mutability");
            d1(v, t, e, role, mutability);
            d2(v, t, role);
            if (!"FRAMEWORK".equals(role)) {
                d3(v, t);
            }
            d4(v, t, e, lockNames);
            d5(v, t, classification);
            d6(v, t, e, tables);
            d7(v, t, e, role);
            d8(v, t, e);
            d9(v, t);
            d10(v, t, e, role, tables);
        }
        return v;
    }

    private static void d1(List<Violation> v, Table t, Entry e, String role, String mutability) {
        if ("MUTABLE".equals(mutability)) {
            Column version = t.columns.get("version");
            if (version == null || !version.type().equals("bigint") || !version.notNull()) {
                v.add(new Violation("D1", t.name, "version", "a MUTABLE table needs version bigint NOT NULL for optimistic locking"));
            }
        }
        if ("CONDITIONAL_UPDATE".equals(mutability) && (e.text("condition") == null || e.text("condition").isBlank())) {
            v.add(new Violation("D1", t.name, null, "a CONDITIONAL_UPDATE table names the condition that makes the update safe"));
        }
        boolean guarded = t.triggers.stream().anyMatch(n -> n.contains("append_only") || n.contains("reject_update"));
        if ("APPEND_ONLY".equals(mutability) && ("LOG_APPEND_ONLY".equals(role) || "DIMENSION_VERSIONED".equals(role)) && !guarded) {
            v.add(new Violation("D1", t.name, null, "an append-only log or version table needs a trigger (tg_*_append_only) that refuses the rewrite"));
        }
    }

    private static void d2(List<Violation> v, Table t, String role) {
        if (!"DIMENSION_VERSIONED".equals(role)) {
            return;
        }
        Column from = t.columns.get("effective_from_date");
        Column to = t.columns.get("effective_to_date");
        if (from == null || !from.type().equals("date") || !from.notNull()) {
            v.add(new Violation("D2", t.name, "effective_from_date", "a versioned dimension has effective_from_date date NOT NULL"));
        }
        if (to == null || !to.type().equals("date") || to.notNull()) {
            v.add(new Violation("D2", t.name, "effective_to_date", "a versioned dimension has a nullable effective_to_date date (empty = still valid)"));
        }
        if (t.constraints.stream().noneMatch(c -> c.kind() == 'x')) {
            v.add(new Violation("D2", t.name, null, "a versioned dimension has an EXCLUDE USING gist constraint against overlapping periods"));
        }
    }

    private static void d3(List<Violation> v, Table t) {
        if (t.comment == null || !GRAIN.matcher(t.comment).matches()) {
            v.add(new Violation("D3", t.name, null, "the table comment states the grain: \"粒度：每 … 一行\" (or 一条)"));
        }
        t.columns.values().stream().filter(c -> c.comment() == null || c.comment().isBlank())
                .forEach(c -> v.add(new Violation("D3", t.name, c.name(), "every column has a comment")));
    }

    private static void d4(List<Violation> v, Table t, Entry e, Set<String> lockNames) {
        Map<String, Object> retention = e.map("retention");
        String policy = String.valueOf(retention.get("policy"));
        Object job = retention.get("job");
        boolean timeBound = DURATION.matcher(policy).matches() || policy.equals("EXPIRY");
        if (!timeBound && !TIMELESS.contains(policy)) {
            v.add(new Violation("D4", t.name, null, "retention policy " + policy + " is neither PERMANENT, BUSINESS_RECORD, CURRENT_STATE, FRAMEWORK, EXPIRY nor <n>d / <n>y"));
        } else if (timeBound && job == null) {
            v.add(new Violation("D4", t.name, null, "retention " + policy + " has no job: a ShedLock job (@SchedulerLock name) must enforce it"));
        } else if (job != null && !lockNames.contains(String.valueOf(job))) {
            v.add(new Violation("D4", t.name, null, "retention job " + job + " is not the name of any @SchedulerLock in src/main/java"));
        }
    }

    private static void d5(List<Violation> v, Table t, Map<String, String> classification) {
        for (String column : t.columns.keySet()) {
            if (SENSITIVE_PERSON.matcher(column).find() && !column.endsWith("_count") && !classification.containsKey(t.name + "." + column)) {
                v.add(new Violation("D5", t.name, column, "a personal-data column is classified in architecture/data-classification.txt"));
            }
        }
    }

    private static final Pattern WORDS = Pattern.compile("_");

    private static void d6(List<Violation> v, Table t, Entry e, Map<String, Table> tables) {
        Map<String, Object> references = e.map("references");
        Map<String, Object> exceptions = e.map("fk_exceptions");
        for (String column : t.columns.keySet()) {
            if (!column.endsWith("_id") || column.equals("id") || exceptions.containsKey(column) || ownKey(t, column)) {
                continue;
            }
            String target = references.containsKey(column) ? String.valueOf(references.get(column)) : derivedTarget(t, column, tables);
            if (target == null) {
                continue;
            }
            boolean keyed = t.constraints.stream().anyMatch(c -> c.kind() == 'f' && c.columns().contains(column) && c.target().replace("\"", "").equals(target));
            if (!keyed) {
                v.add(new Violation("D6", t.name, column, "points at " + target + " in the same schema and has no foreign key (or an fk_exceptions reason)"));
            }
        }
    }

    private static boolean ownKey(Table t, String column) {
        return t.constraints.stream().anyMatch(c -> c.kind() == 'p' && c.columns().equals(List.of(column)));
    }

    /** The table of the same schema the column names: the longest word suffix that is a table's singular name, else the last word as the end of one. */
    private static String derivedTarget(Table t, String column, Map<String, Table> tables) {
        String[] words = WORDS.split(column.substring(0, column.length() - 3));
        for (int start = 0; start < words.length; start++) {
            String entity = String.join("_", java.util.Arrays.copyOfRange(words, start, words.length));
            for (Table other : tables.values()) {
                if (other.schema().equals(t.schema()) && singular(other.bare()).equals(entity) && (other != t || column.startsWith("parent_"))) {
                    return other.name;
                }
            }
        }
        String last = words[words.length - 1];
        for (Table other : tables.values()) {
            String singular = singular(other.bare());
            if (other.schema().equals(t.schema()) && other != t && singular.endsWith("_" + last) && !t.bare().startsWith(singular)) {
                return other.name;
            }
        }
        return null;
    }

    private static String singular(String table) {
        return table.endsWith("ies") ? table.substring(0, table.length() - 3) + "y" : table.endsWith("s") ? table.substring(0, table.length() - 1) : table;
    }

    private static void d7(List<Violation> v, Table t, Entry e, String role) {
        if (!"BUSINESS_DOCUMENT".equals(role)) {
            return;
        }
        Column legal = t.columns.get("legal_entity_id");
        if (legal == null || !legal.notNull()) {
            v.add(new Violation("D7", t.name, "legal_entity_id", "a business document carries legal_entity_id NOT NULL"));
        }
        String date = e.text("business_date_column");
        if (date == null || !t.columns.containsKey(date) || !t.columns.get(date).type().equals("date")) {
            v.add(new Violation("D7", t.name, date, "a business document names a date column as business_date_column"));
        }
        String number = e.text("document_number_column");
        if (number == null || !t.columns.containsKey(number)) {
            v.add(new Violation("D7", t.name, number, "a business document with a numbering rule carries the document number (document_number_column)"));
        } else if (t.constraints.stream().noneMatch(c -> c.kind() == 'u' && c.columns().equals(List.of(number)))) {
            v.add(new Violation("D7", t.name, number, "the document number is unique"));
        }
    }

    private static void d8(List<Violation> v, Table t, Entry e) {
        Map<String, Object> lineage = e.map("lineage");
        if (lineage.isEmpty() || !SOURCES.contains(String.valueOf(lineage.get("source")))) {
            v.add(new Violation("D8", t.name, null, "lineage.source is one of " + SOURCES));
            return;
        }
        Object migrated = lineage.get("migrated_from");
        if (migrated instanceof List<?> l && !l.isEmpty() && !TRACES.contains(String.valueOf(lineage.get("via")))) {
            v.add(new Violation("D8", t.name, null, "migrated_from needs via: " + TRACES));
        }
    }

    private static void d9(List<Violation> v, Table t) {
        for (Column c : t.columns.values()) {
            if (c.type().equals("text") && c.comment() != null && DELIMITED.matcher(c.comment()).find()) {
                v.add(new Violation("D9", t.name, c.name(), "a text column whose comment says values are joined into one string breaks first normal form: use text[] or a child table"));
            }
            if (c.name().endsWith("_codes") && !c.type().equals("text[]")) {
                v.add(new Violation("D9", t.name, c.name(), "_codes is a text[]"));
            }
        }
    }

    private static void d10(List<Violation> v, Table t, Entry e, String role, Map<String, Table> tables) {
        if (!"CONFIG_VERSIONED".equals(role)) {
            return;
        }
        String name = e.text("versions_table");
        Table versions = name == null ? null : tables.get(name);
        if (versions == null) {
            v.add(new Violation("D10", t.name, null, "a versioned configuration has an append-only versions table (versions_table)"));
            return;
        }
        for (String column : List.of("effective_from_date", "confirmed_by", "confirmed_at")) {
            if (!versions.columns.containsKey(column)) {
                v.add(new Violation("D10", t.name, column, "the versions table " + name + " carries " + column));
            }
        }
        if (versions.triggers.stream().noneMatch(n -> n.contains("append_only"))) {
            v.add(new Violation("D10", t.name, null, "the versions table " + name + " refuses UPDATE and DELETE"));
        }
    }

    /** Violations not covered by a gap, plus gaps that cover nothing any more. */
    static List<String> problems(List<Violation> violations, Map<String, Entry> registry) {
        List<String> problems = new ArrayList<>();
        Set<String> covered = new LinkedHashSet<>();
        for (Violation violation : violations) {
            Entry entry = entryOf(registry, violation.table());
            boolean tolerated = entry.gaps().stream().anyMatch(g -> g.rule().equals(violation.rule())
                    && (g.column() == null || g.column().equals(violation.column())));
            if (tolerated) {
                entry.gaps().stream().filter(g -> g.rule().equals(violation.rule()) && (g.column() == null || g.column().equals(violation.column())))
                        .forEach(g -> covered.add(violation.table() + "|" + g.rule() + "|" + g.column()));
            } else {
                problems.add(violation.key() + ": " + violation.why());
            }
        }
        registry.values().forEach(entry -> entry.gaps().forEach(g -> {
            if (g.owner().equals("null") || g.ref().equals("null") || g.note().equals("null")) {
                problems.add("gap " + g + " needs owner, ref and note");
            }
            if (!covered.contains(entry.table() + "|" + g.rule() + "|" + g.column())) {
                problems.add("stale gap " + g.rule() + " " + entry.table() + (g.column() == null ? "" : "." + g.column()) + ": no longer violated, remove it");
            }
        }));
        return problems;
    }

    static Set<String> lockNames() throws IOException {
        Set<String> names = new TreeSet<>();
        Pattern lock = Pattern.compile("@SchedulerLock\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
        for (Path file : javaFiles()) {
            Matcher m = lock.matcher(Files.readString(file));
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(f -> f.toString().endsWith(".java")).sorted().toList();
        }
    }

    // ---------- tests ----------

    @Test
    @DisplayName("K15 the registry and the database agree: every table is registered with a valid role, mutability, retention and lineage, and nothing registered is gone")
    void registryCoversEveryTable() {
        List<String> problems = new ArrayList<>();
        for (Table t : tables.values()) {
            if (entryOf(registry, t.name) == null) {
                problems.add("table " + t.name + " is not in " + REGISTRY);
            }
        }
        registry.forEach((name, entry) -> {
            boolean wildcard = name.endsWith("*");
            if (!wildcard && !tables.containsKey(name)) {
                problems.add("registry entry " + name + " has no table");
            }
            if (!ROLES.contains(entry.text("role"))) {
                problems.add(name + ": role " + entry.text("role") + " is not one of " + ROLES);
            }
            if (!MUTABILITY.contains(entry.text("mutability"))) {
                problems.add(name + ": mutability " + entry.text("mutability") + " is not one of " + MUTABILITY);
            }
        });
        assertThat(problems).as("data access registry").isEmpty();
    }

    @Test
    @DisplayName("D1..D10 the tables follow the data scorecard rules; the only tolerated violations are the gaps listed (with an owner) in the registry, and a fixed gap must be removed")
    void scorecardRulesHold() throws IOException {
        List<String> problems = problems(violations(tables, registry, DdlNamingTest.readClassification(), lockNames()), registry);
        assertThat(problems).as("data scorecard D1..D10 (DATA_SCORECARD section 4)").isEmpty();
    }

    @Test
    @DisplayName("K15 modules read and write exactly the tables the registry says, and no module reaches into another module's schema")
    void accessMatchesTheRegistry() throws IOException {
        Map<String, Map<String, Set<String>>> observed = observedAccess();
        List<String> problems = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        observed.forEach((table, modules) -> {
            Entry entry = entryOf(registry, table);
            if (entry == null) {
                problems.add("SQL touches " + table + ", which is not in the registry");
                return;
            }
            seen.add(entry.table());
            Set<String> writers = new TreeSet<>();
            Set<String> readers = new TreeSet<>();
            modules.forEach((module, kinds) -> {
                if (kinds.contains("w")) {
                    writers.add(module);
                } else {
                    readers.add(module);
                }
                String owner = table.substring(0, table.indexOf('.')).replaceFirst("_", ".");
                if (!module.equals(owner) && !entry.list("cross_module").contains(module)) {
                    problems.add(module + " reaches into " + table + " of " + owner + " (ADR-0026 item 5): modules touch only their own schema");
                }
            });
            assertSame(problems, table, "writers", new TreeSet<>(entry.list("writers")), writers);
            assertSame(problems, table, "readers", new TreeSet<>(entry.list("readers")), readers);
        });
        registry.values().stream().filter(e -> !seen.contains(e.table())).forEach(e -> {
            if (!e.list("writers").isEmpty() || !e.list("readers").isEmpty()) {
                problems.add(e.table() + ": the registry lists readers or writers but no SQL touches the table");
            }
        });
        assertThat(problems).as("data access registry vs SQL in src/main/java").isEmpty();
    }

    private static void assertSame(List<String> problems, String table, String what, Set<String> registered, Set<String> found) {
        if (!registered.equals(found)) {
            problems.add(table + ": registry " + what + " " + registered + " differ from the SQL " + found);
        }
    }

    static final List<String> SCHEMAS = List.of("cost_timesheet", "delivery_execution", "organization_attendance", "organization_workforce", "platform_approval",
            "platform_audit", "platform_identity", "platform_integration", "platform_numbering", "platform_scheduling");
    static final Pattern STRING = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);
    static final Pattern TABLE_USE = Pattern.compile("\\b(from|join|into|update|delete\\s+from|truncate\\s+table|truncate)\\s+((?:" + String.join("|", SCHEMAS)
            + ")\\.[a-z_0-9]+)", Pattern.CASE_INSENSITIVE);

    /** table -> module -> {"r","w"}; a table named in a string literal after FROM / JOIN / INTO / UPDATE / DELETE FROM. */
    static Map<String, Map<String, Set<String>>> observedAccess() throws IOException {
        Map<String, Map<String, Set<String>>> observed = new TreeMap<>();
        Path root = MAIN.resolve("com/forcitis/one");
        for (Path file : javaFiles()) {
            Path relative = root.relativize(file);
            if (relative.getNameCount() < 3) {
                continue;
            }
            String module = relative.getName(0) + "." + relative.getName(1);
            Matcher string = STRING.matcher(Files.readString(file));
            while (string.find()) {
                String sql = string.group(1) != null ? string.group(1) : string.group(2);
                Matcher use = TABLE_USE.matcher(sql);
                while (use.find()) {
                    String verb = use.group(1).toLowerCase(Locale.ROOT);
                    boolean write = verb.equals("into") || verb.equals("update") || verb.startsWith("delete") || verb.startsWith("truncate");
                    observed.computeIfAbsent(use.group(2).toLowerCase(Locale.ROOT), k -> new TreeMap<>()).computeIfAbsent(module, k -> new TreeSet<>())
                            .add(write ? "w" : "r");
                }
            }
        }
        return observed;
    }

    @Test
    @DisplayName("injection: each rule D1..D10 fails for a table that breaks it, and a gap that no longer applies is reported")
    void injectedViolationsAreCaught() {
        Table bad = new Table("platform_audit.demo_rows");
        bad.comment = "a demo table";
        bad.columns.put("id", new Column("id", "bigint", true, null));
        bad.columns.put("display_name", new Column("display_name", "text", false, "comma separated values"));
        bad.columns.put("owner_id", new Column("owner_id", "bigint", false, "owner"));
        bad.columns.put("tag_codes", new Column("tag_codes", "text", false, "tags 逗号分隔"));
        Table owners = new Table("platform_audit.owners");
        owners.comment = "owners";
        Map<String, Table> model = Map.of(bad.name, bad, owners.name, owners);
        Map<String, Object> raw = new LinkedHashMap<>(Map.of("role", "BUSINESS_DOCUMENT", "mutability", "MUTABLE",
                "retention", Map.of("policy", "90d"), "lineage", Map.of("source", "OLD")));
        Entry entry = new Entry(bad.name, raw, List.of(new Gap("D5", "display_name", "X", "ref", "note")));
        Entry ownersEntry = new Entry(owners.name, new LinkedHashMap<>(Map.of("role", "DIMENSION_VERSIONED", "mutability", "APPEND_ONLY",
                "retention", Map.of("policy", "PERMANENT"), "lineage", Map.of("source", "NEW"))), List.of(new Gap("D2", "effective_to_date", "X", "ref", "note")));
        Map<String, Entry> entries = new LinkedHashMap<>(Map.of(bad.name, entry, owners.name, ownersEntry));
        {
            List<Violation> found = violations(model, entries, Map.of(), Set.of());
            Set<String> rules = new TreeSet<>();
            found.forEach(x -> rules.add(x.rule()));
            assertThat(rules).containsExactlyInAnyOrder("D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "D9");
            List<String> problems = problems(found, entries);
            assertThat(problems).noneMatch(p -> p.startsWith("D5 platform_audit.demo_rows.display_name"));
            assertThat(problems).anyMatch(p -> p.startsWith("D1 ")).anyMatch(p -> p.startsWith("D6 ")).anyMatch(p -> p.startsWith("D9 "));
            assertThat(problems).anyMatch(p -> p.startsWith("D2 platform_audit.owners.effective_from_date")).noneMatch(p -> p.startsWith("D2 platform_audit.owners.effective_to_date"));
            assertThat(problems).noneMatch(p -> p.startsWith("stale gap"));

            Entry stale = new Entry(owners.name, ownersEntry.raw(), List.of(new Gap("D7", null, "X", "ref", "note")));
            assertThat(problems(List.of(), Map.of(owners.name, stale))).singleElement().asString().contains("stale gap D7");
        }
    }
}
