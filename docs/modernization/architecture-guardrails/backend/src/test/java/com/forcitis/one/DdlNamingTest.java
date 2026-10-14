package com.forcitis.one;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Data-dictionary guard: every Flyway script must follow NAMING_STANDARD.md §2.3. Failures say exactly what to rename.
 */
class DdlNamingTest {

    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    static final Pattern FILE = Pattern.compile("V\\d{14}__([a-z]+_[a-z]+)_[a-z0-9_]+\\.sql");
    static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE\\s+([a-z0-9_]+)\\.([a-z0-9_]+)\\s*\\((.*?)\\n\\);", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    static final Pattern SNAKE = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");
    static final Set<String> BANNED = Set.of("psa", "cuvalue", "manhour", "man_hour", "works", "biget", "arbad", "ohproject",
            "flag", "info", "data", "temp", "tmp", "bd", "pm", "u8c", "fst");
    static final Set<String> KEYWORDS = Set.of("primary", "constraint", "unique", "foreign", "check");

    @Test
    void migrationsFollowTheNamingStandard() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String folder = file.getParent().getFileName().toString();
                Matcher name = FILE.matcher(file.getFileName().toString());
                if (!name.matches() || !name.group(1).equals(folder)) {
                    problems.add(file + ": name must be V<yyyyMMddHHmmss>__" + folder + "_<description>.sql");
                    continue;
                }
                check(file, folder, Files.readString(file), problems);
            }
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("DDL violates NAMING_STANDARD.md §2.3:\n  " + String.join("\n  ", problems));
        }
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
