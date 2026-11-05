package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
        static final Set<String> ROLES = Set.of("AGGREGATE_ROOT", "AGGREGATE_CHILD", "BUSINESS_DOCUMENT", "DIMENSION_VERSIONED", "SNAPSHOT", "CONFIG",
            "CONFIG_VERSIONED", "LOG_APPEND_ONLY", "QUEUE", "ONE_TIME", "LINEAGE", "COUNTER", "IDEMPOTENCY", "FRAMEWORK", "PLACEHOLDER");
    static final Set<String> MUTABILITY = Set.of("MUTABLE", "CONDITIONAL_UPDATE", "REPLACE_SET", "APPEND_ONLY", "INSERT_DELETE", "MIGRATION_ONLY", "FRAMEWORK");
    static final Set<String> TIMELESS = Set.of("PERMANENT", "BUSINESS_RECORD", "CURRENT_STATE", "FRAMEWORK");
    static final Set<String> SOURCES = Set.of("NEW", "ORG_SOURCE", "MIGRATION", "FRAMEWORK");
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
        final List<Trigger> triggers = new ArrayList<>();
        /** pg_get_constraintdef of the CHECK constraints. */
        final List<String> checks = new ArrayList<>();
        boolean truncateRefused;

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

    /** type is pg_trigger.tgtype: 1 row, 2 before, 4 insert, 8 delete, 16 update, 32 truncate. */
    record Trigger(String name, int type, boolean enabled, String body) {
        boolean refuses(int operation, boolean row) {
            return enabled && (type & 2) != 0 && (type & 1) == (row ? 1 : 0) && (type & operation) != 0 && body.toLowerCase(Locale.ROOT).contains("raise exception");
        }
    }

    record Gap(String rule, String column, String owner, String ref, String note, LocalDate due) {
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
        postgres = TestPostgres.container();
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
                     where r.relkind in ('r', 'p') and not r.relispartition and n.nspname not like 'pg\\_%' and n.nspname not in ('information_schema', 'public', 'one_migration')
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
                    select n.nspname || '.' || r.relname, pg_get_constraintdef(k.oid)
                      from pg_constraint k join pg_class r on r.oid = k.conrelid join pg_namespace n on n.oid = r.relnamespace
                     where k.contype = 'c'
                    """)) {
                while (rs.next()) {
                    Table t = found.get(rs.getString(1));
                    if (t != null) {
                        t.checks.add(rs.getString(2));
                    }
                }
            }
            try (ResultSet rs = c.createStatement().executeQuery("""
                    select n.nspname || '.' || r.relname, g.tgname, g.tgtype, g.tgenabled = 'O', p.prosrc
                      from pg_trigger g join pg_class r on r.oid = g.tgrelid join pg_namespace n on n.oid = r.relnamespace join pg_proc p on p.oid = g.tgfoid
                     where not g.tgisinternal
                    """)) {
                while (rs.next()) {
                    Table t = found.get(rs.getString(1));
                    if (t != null) {
                        t.triggers.add(new Trigger(rs.getString(2), rs.getInt(3), rs.getBoolean(4), rs.getString(5)));
                    }
                }
            }
            for (Table t : found.values()) {
                t.truncateRefused = truncateIsRefused(c, t.name);
            }
        }
        return found;
    }

    /**
     * Behaviour, not names: TRUNCATE inside a transaction that is rolled back; refused means the restrict_violation of a trigger, not a foreign
     * key. A table another table references cannot be truncated alone (0A000, before any trigger fires), so it is tried again with CASCADE,
     * which fires the table's own BEFORE TRUNCATE trigger.
     */
    static boolean truncateIsRefused(Connection c, String table) throws SQLException {
        String refusal = truncateRefusal(c, "truncate table " + table);
        if ("0A000".equals(refusal)) {
            refusal = truncateRefusal(c, "truncate table " + table + " cascade");
        }
        return "23001".equals(refusal);
    }

    private static String truncateRefusal(Connection c, String sql) throws SQLException {
        c.setAutoCommit(false);
        try (var statement = c.createStatement()) {
            statement.execute(sql);
            return null;
        } catch (SQLException refused) {
            return refused.getSQLState();
        } finally {
            c.rollback();
            c.setAutoCommit(true);
        }
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
                        String.valueOf(g.get("owner")), String.valueOf(g.get("ref")), String.valueOf(g.get("note")),
                        g.get("due") == null ? null : LocalDate.parse(String.valueOf(g.get("due")))));
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

    static List<Violation> violations(Map<String, Table> tables, Map<String, Entry> registry, Map<String, ClassificationRegister.Row> classification, Set<String> lockNames) {
        List<Violation> v = new ArrayList<>();
        for (Table t : tables.values()) {
            Entry e = entryOf(registry, t.name);
            if (e == null) {
                continue;
            }
            String role = e.text("role");
            String mutability = e.text("mutability");
            d1(v, t, e, role, mutability);
            d2(v, t, e, role);
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
            v.add(new Violation("D1", t.name, "condition", "a CONDITIONAL_UPDATE table names the condition that makes the update safe"));
        }
        if ("APPEND_ONLY".equals(mutability) && !refusesRewrite(t)) {
            v.add(new Violation("D1", t.name, "trigger", "an APPEND_ONLY table (role " + role + ") has triggers that refuse DELETE and rewriting UPDATE per row and refuse TRUNCATE (checked by running it)"));
        }
    }

    static boolean refusesRewrite(Table t) {
        // The table's own statement-level TRUNCATE trigger as well: with CASCADE, another table's trigger could otherwise do the refusing.
        return t.triggers.stream().anyMatch(g -> g.refuses(16, true)) && t.triggers.stream().anyMatch(g -> g.refuses(8, true))
                && t.triggers.stream().anyMatch(g -> g.refuses(32, false)) && t.truncateRefused;
    }

    private static void d2(List<Violation> v, Table t, Entry e, String role) {
        if (!"DIMENSION_VERSIONED".equals(role)) {
            return;
        }
        if (!"APPEND_ONLY".equals(e.text("mutability"))) {
            v.add(new Violation("D2", t.name, "mutability", "a versioned dimension is APPEND_ONLY: history is closed and appended to, never rewritten"));
        }
        if (t.columns.containsKey("effective_from_at") || t.columns.containsKey("effective_to_at")) {
            d2Moment(v, t);
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
            v.add(new Violation("D2", t.name, "exclude", "a versioned dimension has an EXCLUDE USING gist constraint against overlapping periods"));
        }
    }

    /** The moment variant of D2 (ADR-0034 guard 2): the period is a half-open timestamptz range, so the bounds must be finite moments and the exclusion constraint must be on that range. */
    private static void d2Moment(List<Violation> v, Table t) {
        Column from = t.columns.get("effective_from_at");
        Column to = t.columns.get("effective_to_at");
        if (from == null || !from.type().equals("timestamp with time zone") || !from.notNull()) {
            v.add(new Violation("D2", t.name, "effective_from_at", "a moment-versioned dimension has effective_from_at timestamptz NOT NULL"));
        }
        if (to == null || !to.type().equals("timestamp with time zone") || to.notNull()) {
            v.add(new Violation("D2", t.name, "effective_to_at", "a moment-versioned dimension has a nullable effective_to_at timestamptz (empty = still valid)"));
        }
        if (t.constraints.stream().noneMatch(c -> c.kind() == 'x')) {
            v.add(new Violation("D2", t.name, "exclude", "a moment-versioned dimension has an EXCLUDE USING gist constraint against overlapping periods"));
        }
        if (t.checks.stream().noneMatch(c -> c.contains("isfinite(effective_from_at)") && c.contains("isfinite(effective_to_at)"))) {
            v.add(new Violation("D2", t.name, "isfinite", "a moment-versioned dimension has a CHECK that both bounds are finite (isfinite), so infinity cannot hide an open period"));
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

    private static void d5(List<Violation> v, Table t, Map<String, ClassificationRegister.Row> classification) {
        for (String column : t.columns.keySet()) {
            ClassificationRegister.Row row = classification.get(t.name + "." + column);
            if (ClassificationRegister.PERSON_NAME.matcher(column).find() && !column.endsWith("_count") && (row == null || !row.personal() && !row.exempt())) {
                v.add(new Violation("D5", t.name, column, "a personal-data column is registered in architecture/data-classification.txt with the mark PI or SPI (or an exemption row with its reason)"));
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
            v.add(new Violation("D8", t.name, "lineage", "lineage.source is one of " + SOURCES));
            return;
        }
        Object migrated = lineage.get("migrated_from");
        if (migrated instanceof List<?> l && !l.isEmpty() && !TRACES.contains(String.valueOf(lineage.get("via")))) {
            v.add(new Violation("D8", t.name, "lineage", "migrated_from needs via: " + TRACES));
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
            v.add(new Violation("D10", t.name, "versions_table", "a versioned configuration has an append-only versions table (versions_table)"));
            return;
        }
        for (String column : List.of("effective_from_date", "confirmed_by", "confirmed_at")) {
            if (!versions.columns.containsKey(column)) {
                v.add(new Violation("D10", t.name, column, "the versions table " + name + " carries " + column));
            }
        }
        if (!refusesRewrite(versions)) {
            v.add(new Violation("D10", t.name, "versions_table", "the versions table " + name + " refuses rewriting UPDATE, DELETE and TRUNCATE"));
        }
    }

    /** Violations not covered by a gap, plus gaps that cover nothing any more. */
    static List<String> problems(List<Violation> violations, Map<String, Entry> registry, LocalDate today) {
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
            if (blank(g.owner()) || blank(g.ref()) || blank(g.note()) || g.due() == null) {
                problems.add("gap " + g.rule() + " " + entry.table() + " needs owner, ref, note and due");
            } else if (g.due().isBefore(today)) {
                problems.add("gap " + g.rule() + " " + entry.table() + (g.column() == null ? "" : "." + g.column()) + " was due on " + g.due() + " (owner " + g.owner() + "): fix it");
            }
            if (g.column() == null && !Set.of("D3", "D4").contains(g.rule())) {
                problems.add("gap " + g.rule() + " " + entry.table() + " names no column: only D3 and D4 may cover a whole table");
            }
            if (!covered.contains(entry.table() + "|" + g.rule() + "|" + g.column())) {
                problems.add("stale gap " + g.rule() + " " + entry.table() + (g.column() == null ? "" : "." + g.column()) + ": no longer violated, remove it");
            }
        }));
        return problems;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank() || text.equals("null");
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
            if (Boolean.TRUE.equals(entry.raw().get("optional"))) {
                Object due = entry.raw().get("due");
                if (due == null || LocalDate.parse(String.valueOf(due)).isBefore(LocalDate.now())) {
                    problems.add(name + ": an optional entry has a due date (due) that has not passed: it is removed or made required at the integration");
                }
            }
            if (!wildcard && !tables.containsKey(name) && !Boolean.TRUE.equals(entry.raw().get("optional"))) {
                problems.add("registry entry " + name + " has no table");
            }
            if (!ROLES.contains(entry.text("role"))) {
                problems.add(name + ": role " + entry.text("role") + " is not one of " + ROLES);
            }
            if (!MUTABILITY.contains(entry.text("mutability"))) {
                problems.add(name + ": mutability " + entry.text("mutability") + " is not one of " + MUTABILITY);
            }
        });
        assertThat(problems).as(GuardMessage.of("CS-3.6 / K15", "the data access registry and the migrated database disagree", "register each new table in backend/architecture/data-access-registry.yaml with a valid role, mutability, retention and lineage, and delete the entry of a table that is gone")).isEmpty();
    }

    @Test
    @DisplayName("D1..D10 the tables follow the data scorecard rules; the only tolerated violations are the gaps listed (with an owner) in the registry, and a fixed gap must be removed")
    void scorecardRulesHold() throws IOException {
        List<String> problems = problems(violations(tables, registry, DdlNamingTest.readClassification(), lockNames()), registry, LocalDate.now());
        assertThat(problems).as(GuardMessage.of("CS-3.3 / scorecard D1..D10 (DATA_SCORECARD section 4)", "a table breaks a data scorecard rule that is not a listed gap", "change the table as the named rule says in a new migration, or record a gap with owner, ref, note and due in backend/architecture/data-access-registry.yaml; remove a gap that is already fixed")).isEmpty();
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
        registry.values().stream().filter(e -> !seen.contains(e.table()) && !Boolean.TRUE.equals(e.raw().get("optional"))).forEach(e -> {
            if (!e.list("writers").isEmpty() || !e.list("readers").isEmpty()) {
                problems.add(e.table() + ": the registry lists readers or writers but no SQL touches the table");
            }
        });
        assertThat(problems).as(GuardMessage.of("CS-3.6 / K15 / ADR-0026 item 5", "the tables the SQL in src/main/java touches differ from the registry, or a module reaches into another schema", "make writers and readers in backend/architecture/data-access-registry.yaml equal what the SQL does, move SQL into the owning module, or list the module under cross_module with a reason")).isEmpty();
    }

    private static void assertSame(List<String> problems, String table, String what, Set<String> registered, Set<String> found) {
        if (!registered.equals(found)) {
            problems.add(table + ": registry " + what + " " + registered + " differ from the SQL " + found);
        }
    }

    static final List<String> SCHEMAS = List.of("platform_idempotency", "cost_timesheet", "organization_attendance", "organization_leave", "accounting_period", "organization_payroll", "organization_workforce", "platform_approval",
            "platform_audit", "platform_file", "platform_identity", "platform_integration", "platform_numbering", "platform_scheduling");
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
        Entry entry = new Entry(bad.name, raw, List.of(new Gap("D5", "display_name", "X", "ref", "note", LocalDate.of(2026, 12, 31))));
        Entry ownersEntry = new Entry(owners.name, new LinkedHashMap<>(Map.of("role", "DIMENSION_VERSIONED", "mutability", "APPEND_ONLY",
                "retention", Map.of("policy", "PERMANENT"), "lineage", Map.of("source", "NEW"))), List.of(new Gap("D2", "effective_to_date", "X", "ref", "note", LocalDate.of(2026, 12, 31))));
        Map<String, Entry> entries = new LinkedHashMap<>(Map.of(bad.name, entry, owners.name, ownersEntry));
        {
            List<Violation> found = violations(model, entries, Map.of(), Set.of());
            Set<String> rules = new TreeSet<>();
            found.forEach(x -> rules.add(x.rule()));
            assertThat(rules).containsExactlyInAnyOrder("D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "D9");
            List<String> problems = problems(found, entries, LocalDate.of(2026, 10, 15));
            assertThat(problems).noneMatch(p -> p.startsWith("D5 platform_audit.demo_rows.display_name"));
            assertThat(problems).anyMatch(p -> p.startsWith("D1 ")).anyMatch(p -> p.startsWith("D6 ")).anyMatch(p -> p.startsWith("D9 "));
            assertThat(problems).anyMatch(p -> p.startsWith("D2 platform_audit.owners.effective_from_date")).noneMatch(p -> p.startsWith("D2 platform_audit.owners.effective_to_date"));
            assertThat(problems).noneMatch(p -> p.startsWith("stale gap"));

            Entry stale = new Entry(owners.name, ownersEntry.raw(), List.of(new Gap("D7", null, "X", "ref", null, null)));
            assertThat(problems(List.of(), Map.of(owners.name, stale), LocalDate.of(2026, 10, 15))).anyMatch(p -> p.contains("stale gap D7"))
                    .anyMatch(p -> p.contains("names no column")).anyMatch(p -> p.contains("needs owner, ref, note and due"));

            Entry overdue = new Entry(bad.name, entry.raw(), List.of(new Gap("D5", "display_name", "X", "ref", "note", LocalDate.of(2026, 10, 1))));
            assertThat(problems(found, Map.of(bad.name, overdue, owners.name, ownersEntry), LocalDate.of(2026, 10, 15))).anyMatch(p -> p.contains("was due on 2026-10-01"));
        }
    }

    @Test
    @DisplayName("injection: the moment variant of D2 fails for a table without EXCLUDE, without the isfinite CHECK, with a nullable effective_from_at or with a date period, and passes for a table that has all of them")
    void injectedMomentVersionTablesAreCaught() {
        Map<String, Table> model = new LinkedHashMap<>();
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String variant : List.of("good", "no_exclude", "no_isfinite", "nullable_from", "date_period")) {
            Table table = new Table("platform_audit.moment_" + variant);
            table.comment = "moment sample";
            boolean date = variant.equals("date_period");
            table.columns.put("effective_from_at", new Column("effective_from_at", date ? "date" : "timestamp with time zone", !variant.equals("nullable_from"), "from"));
            table.columns.put("effective_to_at", new Column("effective_to_at", date ? "date" : "timestamp with time zone", false, "to"));
            if (!variant.equals("no_exclude")) {
                table.constraints.add(new Constraint("ex_moment_" + variant, 'x', List.of("effective_from_at", "effective_to_at"), "", false));
            }
            if (!variant.equals("no_isfinite")) {
                table.checks.add("CHECK ((isfinite(effective_from_at) AND ((effective_to_at IS NULL) OR isfinite(effective_to_at))))");
            }
            model.put(table.name, table);
            entries.put(table.name, new Entry(table.name, new LinkedHashMap<>(Map.of("role", "DIMENSION_VERSIONED", "mutability", "APPEND_ONLY",
                    "retention", Map.of("policy", "PERMANENT"), "lineage", Map.of("source", "NEW"))), List.of()));
        }
        Set<String> found = new TreeSet<>();
        violations(model, entries, Map.of(), Set.of()).stream().filter(x -> x.rule().equals("D2")).forEach(x -> found.add(x.table() + "." + x.column()));

        assertThat(found).containsExactlyInAnyOrder("platform_audit.moment_no_exclude.exclude", "platform_audit.moment_no_isfinite.isfinite",
                "platform_audit.moment_nullable_from.effective_from_at", "platform_audit.moment_date_period.effective_from_at",
                "platform_audit.moment_date_period.effective_to_at");
    }

    static final Pattern DML = Pattern.compile("^\\s*(insert\\s+into|update|delete\\s+from|truncate\\s+table|truncate)\\s+([a-z0-9_.\"]+)", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("M6 every SQL literal that writes names its schema, so the access check sees it and no search_path decides where it goes")
    void writingSqlNamesItsSchema() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : javaFiles()) {
            Matcher string = STRING.matcher(Files.readString(file));
            while (string.find()) {
                String sql = string.group(1) != null ? string.group(1) : string.group(2);
                Matcher dml = DML.matcher(sql);
                if (dml.find() && !dml.group(2).contains(".")) {
                    problems.add(file + ": " + dml.group(1).trim() + " " + dml.group(2) + " has no schema prefix");
                }
            }
        }
        assertThat(problems).as(GuardMessage.of("CS-3.6 / M6", "a SQL literal that writes names a table without its schema", "write the table as <schema>.<table> in the SQL text so the access check sees it")).isEmpty();
    }

    record RoutineRow(String signature, String schema, boolean securityDefiner, boolean publicMayExecute, String searchPath, List<String> otherCreators) {}

    static final String FUNCTIONS = """
            SELECT p.oid::regprocedure::text, n.nspname, p.prosecdef, has_function_privilege('public', p.oid, 'EXECUTE'),
                   (SELECT substring(c FROM '^search_path=(.*)$') FROM unnest(p.proconfig) c WHERE c LIKE 'search_path=%'),
                   (SELECT coalesce(array_agg(CASE WHEN a.grantee = 0 THEN 'PUBLIC' ELSE pg_get_userbyid(a.grantee) END), '{}')
                      FROM aclexplode(n.nspacl) a WHERE a.privilege_type = 'CREATE' AND a.grantee <> n.nspowner)
              FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
             WHERE n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast') AND p.prokind IN ('f', 'p')
               AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid = 'pg_proc'::regclass AND d.objid = p.oid AND d.deptype = 'e')
            """;

    static List<RoutineRow> readFunctions(Connection c) throws SQLException {
        List<RoutineRow> functions = new ArrayList<>();
        try (var st = c.createStatement(); ResultSet rs = st.executeQuery(FUNCTIONS)) {
            while (rs.next()) {
                Object[] creators = rs.getArray(6) == null ? new Object[0] : (Object[]) rs.getArray(6).getArray();
                functions.add(new RoutineRow(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4), rs.getString(5),
                        Stream.of(creators).map(String::valueOf).toList()));
            }
        }
        return functions;
    }

    /** R-SP-1 and R-SP-3 (FUNCTION_SEARCH_PATH_PINNING.md): pg_catalog first, pg_temp last, only the own schema in between and only if nobody else may create there. */
    static List<String> searchPathProblems(List<RoutineRow> functions) {
        List<String> problems = new ArrayList<>();
        for (RoutineRow f : functions) {
            if (f.searchPath() == null) {
                problems.add(f.signature() + " has no SET search_path");
            } else {
                List<String> items = new ArrayList<>();
                Matcher item = Pattern.compile("\"([^\"]*)\"|([^,\\s][^,]*)").matcher(f.searchPath());
                while (item.find()) {
                    items.add(item.group(1) != null ? item.group(1) : item.group(2).strip());
                }
                boolean ownSchema = items.size() == 3 && items.get(1).equals(f.schema()) && !f.schema().equals("public");
                if (!(items.size() == 2 || ownSchema) || !items.get(0).equals("pg_catalog") || !items.get(items.size() - 1).equals("pg_temp")) {
                    problems.add(f.signature() + " search_path is [" + f.searchPath() + "], expected pg_catalog, [own schema,] pg_temp");
                } else if (ownSchema && !f.otherCreators().isEmpty()) {
                    problems.add(f.signature() + " puts its own schema in the search_path but " + f.otherCreators() + " may create objects there");
                }
            }
            if (f.securityDefiner() && f.publicMayExecute()) {
                problems.add(f.signature() + " is SECURITY DEFINER and PUBLIC may execute it");
            }
        }
        return problems;
    }

    @Test
    @DisplayName("R-SP-1 every function in the database pins search_path to pg_catalog, [its own schema,] pg_temp, and a SECURITY DEFINER function is not executable by PUBLIC")
    void everyFunctionPinsItsSearchPath() throws SQLException {
        try (Connection c = connect()) {
            List<RoutineRow> functions = readFunctions(c);
            assertThat(functions).as("the migrated database has functions to check").hasSizeGreaterThan(30);
            assertThat(searchPathProblems(functions)).as(GuardMessage.of("R-SP-1 (FUNCTION_SEARCH_PATH_PINNING.md)", "a function does not pin its search_path, or a SECURITY DEFINER function is executable by PUBLIC", "add SET search_path = pg_catalog, [<own schema>,] pg_temp to the function in a new migration, and REVOKE EXECUTE ON the SECURITY DEFINER function FROM PUBLIC")).isEmpty();
        }
    }

    @Test
    @DisplayName("injection (R-SP-1): a function without SET search_path, with a wrong order, a foreign schema or a quoted list is caught, in any schema; a pinned function passes")
    void injectedUnpinnedFunctionsAreCaught() throws SQLException {
        assertThat(searchPathProblems(List.of(
                new RoutineRow("s.a()", "s", false, true, null, List.of()),
                new RoutineRow("s.b()", "s", false, true, "pg_temp, pg_catalog", List.of()),
                new RoutineRow("s.c()", "s", false, true, "pg_catalog, other, pg_temp", List.of()),
                new RoutineRow("s.d()", "s", false, true, "\"pg_catalog, pg_temp\"", List.of()),
                new RoutineRow("s.e()", "s", false, true, "pg_catalog, s, pg_temp", List.of("PUBLIC")),
                new RoutineRow("s.f()", "s", true, true, "pg_catalog, pg_temp", List.of()),
                new RoutineRow("public.i()", "public", false, true, "pg_catalog, public, pg_temp", List.of())))).hasSize(7);
        assertThat(searchPathProblems(List.of(
                new RoutineRow("s.g()", "s", false, true, "pg_catalog, pg_temp", List.of()),
                new RoutineRow("s.h()", "s", true, false, "pg_catalog, s, pg_temp", List.of())))).isEmpty();
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (var st = c.createStatement()) {
                st.execute("CREATE FUNCTION public.unpinned_probe() RETURNS int LANGUAGE sql AS 'SELECT 1'");
                assertThat(searchPathProblems(readFunctions(c))).singleElement().asString().contains("unpinned_probe()");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("V11 the deployment check for pinned search_path passes on the migrated database and fails for an unpinned function, a function whose own schema anyone may create in, and a PUBLIC-executable SECURITY DEFINER function")
    void deploymentCheckV11AgreesWithTheGuard() throws Exception {
        List<String> breakers = List.of(
                "CREATE FUNCTION public.unpinned_probe() RETURNS int LANGUAGE sql AS 'SELECT 1'",
                "CREATE SCHEMA probe; GRANT CREATE ON SCHEMA probe TO PUBLIC;"
                        + " CREATE FUNCTION probe.unpinned_probe() RETURNS int LANGUAGE sql SET search_path = pg_catalog, probe, pg_temp AS 'SELECT 1'",
                "CREATE FUNCTION public.unpinned_probe() RETURNS int LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS 'SELECT 1'");
        for (String script : List.of("dev-db-verify.sql", "prod-db-verify.sql")) {
            String text = Files.readString(Path.of("../docs/ops/runbooks/sql", script));
            int from = text.indexOf("DO $$", text.indexOf("-- V11"));
            String v11 = text.substring(from, text.indexOf("\n-- V", from));
            try (Connection c = connect(); var st = c.createStatement()) {
                st.execute(v11);
                c.setAutoCommit(false);
                for (String breaker : breakers) {
                    try {
                        st.execute(breaker);
                        assertThatThrownBy(() -> st.execute(v11)).hasMessageContaining("V11 failed").hasMessageContaining("unpinned_probe()");
                    } finally {
                        c.rollback();
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("M5 the runtime role loses DELETE and TRUNCATE on every versioned dimension: the database script revokes them for exactly the registered tables")
    void databaseScriptRevokesWritesOnVersionTables() throws IOException {
        String script = Files.readString(Path.of("../docs/ops/one-db-roles-schemas.sql"));
        int at = script.indexOf("REVOKE DELETE, TRUNCATE ON");
        assertThat(at).as("one_db_roles_schemas.sql has the REVOKE for version tables").isGreaterThan(0);
        int list = script.lastIndexOf("FOREACH t IN ARRAY ARRAY[", at);
        String revoke = script.substring(list, at);
        Set<String> revoked = new TreeSet<>();
        Matcher name = Pattern.compile("'([a-z_]+\\.[a-z_]+)'").matcher(revoke);
        while (name.find()) {
            revoked.add(name.group(1));
        }
        Set<String> registered = new TreeSet<>();
        registry.forEach((table, e) -> {
            if ("DIMENSION_VERSIONED".equals(e.text("role"))) {
                registered.add(table);
            }
        });
        assertThat(revoked).as(GuardMessage.of("CS-3.9 / M5", "the REVOKE DELETE, TRUNCATE list in docs/ops/one-db-roles-schemas.sql differs from the registered DIMENSION_VERSIONED tables", "make the table list of that REVOKE in docs/ops/one-db-roles-schemas.sql equal the DIMENSION_VERSIONED tables of backend/architecture/data-access-registry.yaml")).isEqualTo(registered);
    }

    @Test
    @DisplayName("N9 every column of audit_entries is named in archive-columns.txt as archived or excluded, and no RESTRICTED column is archived")
    void auditArchiveColumnsAreDeliberate() throws IOException {
        Map<String, String> decided = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of("architecture/archive-columns.txt"))) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] parts = line.split("\\s*\\|\\s*", 4);
                assertThat(parts).as(GuardMessage.of("N9", "a line of archive-columns.txt does not have four columns: " + line, "write it as <column> | ARCHIVE or EXCLUDE | <classification> | <reason> in backend/architecture/archive-columns.txt")).hasSize(4);
                assertThat(parts[1]).as(GuardMessage.of("N9", "the second column of an archive-columns.txt line is neither ARCHIVE nor EXCLUDE: " + line, "write ARCHIVE or EXCLUDE as the second column in backend/architecture/archive-columns.txt")).isIn("ARCHIVE", "EXCLUDE");
                assertThat(parts[3]).as(GuardMessage.of("N9", "a line of archive-columns.txt has no reason: " + line, "write the reason as the fourth column in backend/architecture/archive-columns.txt")).isNotBlank();
                decided.put(parts[0], parts[1]);
            }
        }
        Map<String, ClassificationRegister.Row> classification = DdlNamingTest.readClassification();
        List<String> problems = new ArrayList<>();
        for (String column : tables.get("platform_audit.audit_entries").columns.keySet()) {
            if (!decided.containsKey(column)) {
                problems.add("audit_entries." + column + " is not in architecture/archive-columns.txt: decide whether the archive carries it");
            } else if ("ARCHIVE".equals(decided.get(column)) && classification.containsKey("platform_audit.audit_entries." + column) && "RESTRICTED".equals(classification.get("platform_audit.audit_entries." + column).level())) {
                problems.add("audit_entries." + column + " is RESTRICTED and cannot be archived");
            }
        }
        decided.keySet().stream().filter(c -> !tables.get("platform_audit.audit_entries").columns.containsKey(c))
                .forEach(c -> problems.add("archive-columns.txt names " + c + ", which audit_entries does not have"));
        assertThat(problems).as(GuardMessage.of("N9", "the audit archive columns are not decided exactly for the columns of audit_entries", "add or remove the line <column> | ARCHIVE or EXCLUDE | <reason> in backend/architecture/archive-columns.txt; a RESTRICTED column must be EXCLUDE")).isEmpty();
    }

    // ---------- R-1b P3b, ADR-0033 guard 7: the employee code is an attribute, never a key ----------

    static final String EMPLOYEE_CODE = "employee_code";
    static final Pattern BY_EMPLOYEE_CODE = Pattern.compile("\\b\\w*(?:By|For|With)EmployeeCode\\w*\\s*\\(");

    /** Primary keys, unique constraints, foreign keys, exclusion constraints and unique indexes of the schemas that name the column. */
    static List<String> keysOver(Connection c, String column) throws SQLException {
        List<String> keys = new ArrayList<>();
        try (var select = c.prepareStatement("""
                select n.nspname || '.' || t.relname || ' ' || x.conname || ' (' || x.contype::text || ')'
                  from pg_constraint x
                  join pg_class t on t.oid = x.conrelid
                  join pg_namespace n on n.oid = t.relnamespace
                  join pg_attribute a on a.attrelid = t.oid and a.attnum = any (x.conkey)
                 where x.contype in ('p', 'u', 'f', 'x') and a.attname = ? and n.nspname not like 'pg\\_%' and n.nspname <> 'information_schema'
                union
                select n.nspname || '.' || t.relname || ' ' || i.relname || ' (unique index)'
                  from pg_index x
                  join pg_class i on i.oid = x.indexrelid
                  join pg_class t on t.oid = x.indrelid
                  join pg_namespace n on n.oid = t.relnamespace
                  join pg_attribute a on a.attrelid = t.oid and a.attnum = any (x.indkey)
                 where x.indisunique and a.attname = ? and n.nspname not like 'pg\\_%' and n.nspname <> 'information_schema'
                   and not exists (select 1 from pg_constraint k where k.conindid = x.indexrelid and k.contype in ('p', 'u', 'x'))
                """)) {
            select.setString(1, column);
            select.setString(2, column);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString(1));
                }
            }
        }
        return keys;
    }

    static final String EMPLOYEE_CODE_KEY = "organization_workforce.employees uk_employees_employee_code (u)";
    static final Path EMPLOYEE_CODE_COLUMNS = Path.of("architecture/employee-code-columns.txt");

    /** The definition of the one key over the code: a unique constraint of one column, which has no condition and no expression. */
    static String employeeCodeKeyDefinition(Connection c) throws SQLException {
        try (var select = c.prepareStatement("""
                select pg_get_constraintdef(x.oid) from pg_constraint x
                 where x.conrelid = 'organization_workforce.employees'::regclass and x.conname = 'uk_employees_employee_code'""");
             ResultSet rs = select.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Tables with a column named employee_code or registered as the employee code in the data classification. */
    static Set<String> tablesCarryingTheEmployeeCode(Connection c, Map<String, ?> classification) throws SQLException {
        Set<String> tables = new TreeSet<>();
        try (var select = c.prepareStatement("""
                select table_schema || '.' || table_name from information_schema.columns
                 where column_name = ? and table_schema not like 'pg\\_%' and table_schema <> 'information_schema'""")) {
            select.setString(1, EMPLOYEE_CODE);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
        }
        for (String column : classification.keySet()) {
            if (column.endsWith("." + EMPLOYEE_CODE) || column.endsWith(".original_code")) {
                tables.add(column.substring(0, column.lastIndexOf('.')));
            }
        }
        return tables;
    }

    static Set<String> employeeCodeTables() throws IOException {
        Set<String> tables = new TreeSet<>();
        for (String line : Files.readAllLines(EMPLOYEE_CODE_COLUMNS)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                int hash = line.indexOf('#');
                assertThat(hash).as("a reason after # on: " + line).isPositive();
                assertThat(line.substring(hash + 1)).as("reason of " + line).isNotBlank();
                tables.add(line.substring(0, hash).strip());
            }
        }
        return tables;
    }

    static List<String> lookupsByEmployeeCode(Map<String, String> sources) {
        List<String> found = new ArrayList<>();
        sources.forEach((file, text) -> {
            Matcher method = BY_EMPLOYEE_CODE.matcher(JavaCode.withoutComments(text));
            while (method.find()) {
                found.add(file + ": " + method.group());
            }
        });
        return found;
    }

    @Test
    @DisplayName("ADR-0078 守护 1 EmployeeCodeKeyScopeTest：uk_employees_employee_code 存在且是单列无条件的唯一约束，employee_code 不在任何其他键中，工号列只在允许集的表上，主代码没有按员工编号取单个对象的方法")
    void theEmployeeCodeKeyIsTheOnlyOne() throws Exception {
        try (Connection c = connect()) {
            assertThat(keysOver(c, EMPLOYEE_CODE)).as(GuardMessage.of("ADR-0078 guard 1", "employee_code is part of a key other than " + EMPLOYEE_CODE_KEY, "the employee code is an attribute, never a key: drop it from that key or foreign key in a new migration")).containsExactly(EMPLOYEE_CODE_KEY);
            assertThat(employeeCodeKeyDefinition(c)).as(GuardMessage.of("ADR-0078 guard 1", "the employee code key is not one column without a condition", "define " + EMPLOYEE_CODE_KEY + " as UNIQUE (employee_code) in a new migration")).isEqualTo("UNIQUE (employee_code)");
            Set<String> carrying = tablesCarryingTheEmployeeCode(c, DdlNamingTest.readClassification());
            assertThat(carrying).as(GuardMessage.of("ADR-0078 guard 1", "the tables carrying an employee code column differ from backend/architecture/employee-code-columns.txt", "keep the employee code column only on the allowed tables, or list the table in backend/architecture/employee-code-columns.txt with a reason")).isEqualTo(employeeCodeTables());
        }
        Map<String, String> sources = new TreeMap<>();
        for (Path file : javaFiles()) {
            sources.put(MAIN.relativize(file).toString(), Files.readString(file));
        }
        assertThat(lookupsByEmployeeCode(sources)).as(GuardMessage.of("ADR-0078 guard 1", "a method fetches one object by the employee code", "fetch by the object id and use the employee code only to show or search")).isEmpty();
    }

    @Test
    @DisplayName("ADR-0078 守护 1（CS-5.9 注入）：部分唯一索引、改名的唯一约束、外键、未登记表上的工号列、findByEmployeeCode 被拦下；普通索引与读属性 employeeCode() 不算")
    void injectedEmployeeCodeKeysAreCaught() throws Exception {
        try (Connection c = connect(); var statement = c.createStatement()) {
            statement.execute("create schema guard_sample");
            try {
                statement.execute("create table guard_sample.people (id uuid primary key, employee_code text not null)");
                statement.execute("create index ix_people_employee_code on guard_sample.people (employee_code)");
                assertThat(keysOver(c, EMPLOYEE_CODE)).as("a plain index is no key").containsExactly(EMPLOYEE_CODE_KEY);
                assertThat(tablesCarryingTheEmployeeCode(c, Map.of())).as("a table that is not in the allowed set").contains("guard_sample.people")
                        .isNotEqualTo(employeeCodeTables());
                statement.execute("create unique index uk_people_employee_code on guard_sample.people (employee_code) where employee_code <> ''");
                assertThat(keysOver(c, EMPLOYEE_CODE)).as("a partial unique index").contains("guard_sample.people uk_people_employee_code (unique index)");
                statement.execute("drop index guard_sample.uk_people_employee_code");
                statement.execute("alter table guard_sample.people add constraint uk_people_other_name unique (employee_code)");
                assertThat(keysOver(c, EMPLOYEE_CODE)).as("a renamed unique constraint").contains("guard_sample.people uk_people_other_name (u)");
                statement.execute("create table guard_sample.refs (id uuid primary key, employee_code text references organization_workforce.employees (employee_code))");
                assertThat(keysOver(c, EMPLOYEE_CODE)).as("a foreign key to the code").contains("guard_sample.refs refs_employee_code_fkey (f)");
            } finally {
                statement.execute("drop schema guard_sample cascade");
            }
        }
        assertThat(lookupsByEmployeeCode(Map.of("Sample.java", """
                interface Sample {
                    Optional<Employee> findByEmployeeCode(String code);
                    default String code(Employee employee) { return employee.employeeCode(); }
                    // employeeForEmployeeCode(String code) in a comment is no method
                }
                """))).containsExactly("Sample.java: findByEmployeeCode(");
    }
}
