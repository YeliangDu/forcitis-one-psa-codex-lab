package com.forcitis.one.platform.integration.guard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/** The external read register (ADR-0037 item 6.1), read by the guards; it is not on the run-time class path. */
public final class ExternalReadRegister {

    public static final Path FILE = Path.of("architecture/external-read-registry.yaml");

    public static final List<String> FIELDS = List.of("database", "object", "column", "source", "category", "sensitivity", "purpose");
    public static final Set<String> CATEGORIES = Set.of("ORG_UNIT", "POSITION", "EMPLOYEE", "ASSIGNMENT", "EXTERNAL_IDENTITY", "TODO");
    public static final Set<String> SENSITIVITIES = Set.of("PUBLIC", "INTERNAL", "SENSITIVE", "RESTRICTED");
    public static final String A8 = "a8_db";
    public static final String VIEW_SCHEMA = "forcitis_one";
    public static final String BASE_SCHEMA = "dbo";

    /** The sensitive-looking column words of external sources, on the lower-case column name: the former DdlNamingTest list (email and gender included) plus the two identity words of the external sources (login, we_com); kept as its own list, independent of ClassificationRegister. */
    public static final Pattern SENSITIVE_NAME = Pattern.compile(
            "(^|_)(password|secret|totp|bank|salary|wage|daily_rate|id_card|identity_number|mobile|phone|email|birth|gender|address|attribute_bytes|login|we_com)(_|$)");

    public enum ReadPurpose {
        RUNTIME_SYNC, RUNTIME_TODO, MIGRATION, MIGRATION_TIE_OUT;

        public boolean runtime() {
            return this == RUNTIME_SYNC || this == RUNTIME_TODO;
        }
    }

    public record Row(int line, Map<String, Object> fields) {

        public String get(String field) {
            Object value = fields.get(field);
            return value == null ? null : value.toString();
        }

        public String database() {
            return get("database");
        }

        public String object() {
            return get("object");
        }

        public String column() {
            return get("column");
        }

        public ReadPurpose purpose() {
            return ReadPurpose.valueOf(get("purpose"));
        }

        public String schema() {
            return object().substring(0, object().indexOf('.'));
        }

        public String objectName() {
            return object().substring(object().indexOf('.') + 1);
        }

        public boolean isView() {
            return schema().equals(VIEW_SCHEMA);
        }
    }

    private ExternalReadRegister() {
    }

    public static List<Row> rows() throws IOException {
        return rows(Files.readAllLines(FILE));
    }

    /** The databases the register names, from its databases list (the old system's database name is spelt only in the register). */
    public static Set<String> databases() throws IOException {
        return databases(Files.readAllLines(FILE));
    }

    public static Set<String> databases(List<String> lines) {
        Map<String, Object> yaml = new Yaml().load(String.join("\n", lines));
        if (yaml == null || !(yaml.get("databases") instanceof List<?> listed) || listed.isEmpty()) {
            throw new IllegalStateException(FILE + " has no databases list");
        }
        Set<String> databases = new TreeSet<>();
        listed.forEach(d -> databases.add(String.valueOf(d)));
        return databases;
    }

    /** Every row on its own line as a flow mapping; a row spread over lines would hide table and column from the credential guard. */
    @SuppressWarnings("unchecked")
    public static List<Row> rows(List<String> lines) {
        Map<String, Object> yaml = new Yaml().load(String.join("\n", lines));
        if (yaml == null || !(yaml.get("columns") instanceof List<?> listed)) {
            throw new IllegalStateException(FILE + " has no columns list");
        }
        List<Integer> rowLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("- {")) {
                if (!trimmed.endsWith("}")) {
                    throw new IllegalStateException(FILE + ":" + (i + 1) + " a row must be one flow mapping on one line");
                }
                rowLines.add(i + 1);
            }
        }
        if (rowLines.size() != listed.size()) {
            throw new IllegalStateException(FILE + ": " + listed.size() + " rows but " + rowLines.size() + " one-line flow mappings");
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < listed.size(); i++) {
            rows.add(new Row(rowLines.get(i), (Map<String, Object>) listed.get(i)));
        }
        return rows;
    }

    /** Problems of one row: missing or unknown fields, illegal values, a view outside a8_db, a sensitive-looking column below SENSITIVE. */
    public static List<String> problems(Row row, Set<String> databases) {
        List<String> problems = new ArrayList<>();
        String at = FILE + ":" + row.line() + " ";
        for (String field : FIELDS) {
            String value = row.get(field);
            if (value == null || value.isBlank()) {
                problems.add(at + "lacks " + field);
            }
        }
        Set<String> unknown = new TreeSet<>(row.fields().keySet());
        unknown.removeAll(FIELDS);
        if (!unknown.isEmpty()) {
            problems.add(at + "has unknown fields " + unknown);
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        if (!databases.contains(row.database())) {
            problems.add(at + "database " + row.database() + " is not one of " + databases);
        }
        if (!row.object().matches("(" + VIEW_SCHEMA + "\\.v_\\w+|" + BASE_SCHEMA + "\\.\\w+)")) {
            problems.add(at + "object " + row.object() + " is neither " + VIEW_SCHEMA + ".v_* nor " + BASE_SCHEMA + ".<name>");
        }
        if (row.object().startsWith(VIEW_SCHEMA + ".") && !row.database().equals(A8)) {
            problems.add(at + "object " + row.object() + " is a view of " + A8 + ", not of " + row.database());
        }
        if (!row.column().matches("\\w+")) {
            problems.add(at + "column " + row.column() + " is not a plain column name");
        }
        if (!row.get("source").matches(BASE_SCHEMA + "\\.\\w+\\.\\w+(（经 " + BASE_SCHEMA + "\\.\\w+\\.\\w+）)?")) {
            problems.add(at + "source " + row.get("source") + " is not dbo.<table>.<column>");
        }
        if (!CATEGORIES.contains(row.get("category"))) {
            problems.add(at + "category " + row.get("category") + " is not one of " + CATEGORIES);
        }
        if (!SENSITIVITIES.contains(row.get("sensitivity"))) {
            problems.add(at + "sensitivity " + row.get("sensitivity") + " is not one of " + SENSITIVITIES);
        }
        try {
            row.purpose();
        } catch (IllegalArgumentException illegal) {
            problems.add(at + "purpose " + row.get("purpose") + " is not one of " + List.of(ReadPurpose.values()));
        }
        if (looksSensitive(row.column()) && !Set.of("SENSITIVE", "RESTRICTED").contains(row.get("sensitivity"))) {
            problems.add(at + "column " + row.column() + " looks sensitive: register it SENSITIVE or RESTRICTED");
        }
        return problems;
    }

    static boolean looksSensitive(String column) {
        return SENSITIVE_NAME.matcher(column.toLowerCase(Locale.ROOT)).find();
    }

    /** Names of registered dbo objects, and of the tables named in sources. */
    public static Set<String> baseObjectNames(List<Row> rows) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Pattern table = Pattern.compile(BASE_SCHEMA + "\\.(\\w+)\\.\\w+");
        for (Row row : rows) {
            if (!row.isView()) {
                names.add(row.objectName());
            }
            Matcher m = table.matcher(row.get("source"));
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    public static Set<String> objects(List<Row> rows) {
        Set<String> objects = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        rows.forEach(r -> objects.add(r.object()));
        return objects;
    }
}
