package com.forcitis.one;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DATA_DESIGN_CHECKLIST section 14 items 2 and 4 (review H-4): what a persistence adapter may write and where. Statements are read from the
 * SQL string literals of the adapters; the table structure comes from the migrations.
 */
final class RepositoryWritePolicy {
    record Write(String verb, String table, String method, int line, boolean versionConditional) {
    }

    static final Set<String> AGGREGATE_METHOD_PREFIXES = Set.of("save", "insert", "update", "upsert", "delete");

    private static final Set<String> CONTROL_FLOW = Set.of("if", "for", "while", "switch", "catch", "try", "synchronized");
    private static final Pattern STRING = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);
    private static final Pattern STATEMENT = Pattern.compile(
            "^\\s*(insert\\s+into|update|delete\\s+from)\\s+([a-z0-9_]+\\.[a-z0-9_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_CONDITION = Pattern.compile("\\bversion\\s*=\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE_DECLARATION = Pattern.compile("^\\s*(?:(?:private|protected|static|final)\\s+)*(?:record|class|enum|interface)\\s");
    private static final Pattern INSERT_COLUMNS = Pattern.compile("^\\s*insert\\s+into\\s+[a-z0-9_.]+\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern METHOD = Pattern.compile(
            "(?m)^ {4}(?:(?:public|private|protected|static|final|default|synchronized)\\s+)*[\\w<>\\[\\],.? ]+?\\s+(\\w+)\\s*\\([^;{]*\\)\\s*(?:throws [\\w., ]+)?\\s*\\{");

    private RepositoryWritePolicy() {
    }

    static List<Write> writes(String source) {
        String code = StateWritePolicy.withoutComments(source);
        List<int[]> methods = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Matcher method = METHOD.matcher(code);
        while (method.find()) {
            if (CONTROL_FLOW.contains(method.group(1)) || TYPE_DECLARATION.matcher(method.group()).find()) {
                continue;
            }
            methods.add(new int[] {method.start()});
            names.add(method.group(1));
        }
        List<Write> writes = new ArrayList<>();
        Matcher string = STRING.matcher(code);
        while (string.find()) {
            String sql = string.group(1) != null ? string.group(1) : string.group(2);
            Matcher statement = STATEMENT.matcher(sql);
            if (!statement.find()) {
                continue;
            }
            String verb = statement.group(1).toLowerCase(Locale.ROOT).startsWith("insert") ? "insert"
                    : statement.group(1).toLowerCase(Locale.ROOT).startsWith("update") ? "update" : "delete";
            writes.add(new Write(verb, statement.group(2).toLowerCase(Locale.ROOT), enclosing(methods, names, string.start()), line(code, string.start()),
                    VERSION_CONDITION.matcher(sql).find()));
        }
        return writes;
    }

    /** Insert statements that leave out a status column the table has, so that the database default would decide the status. */
    static List<String> insertsWithoutStatus(String file, String source, java.util.function.Function<String, Set<String>> statusColumnsOf) {
        List<String> problems = new ArrayList<>();
        String code = StateWritePolicy.withoutComments(source);
        Matcher string = STRING.matcher(code);
        while (string.find()) {
            String sql = string.group(1) != null ? string.group(1) : string.group(2);
            Matcher statement = STATEMENT.matcher(sql);
            if (!statement.find() || !statement.group(1).toLowerCase(Locale.ROOT).startsWith("insert")) {
                continue;
            }
            Matcher columns = INSERT_COLUMNS.matcher(sql);
            if (!columns.find()) {
                continue;
            }
            Set<String> listed = new java.util.HashSet<>();
            for (String column : columns.group(1).split(",")) {
                listed.add(column.trim().toLowerCase(Locale.ROOT));
            }
            for (String status : statusColumnsOf.apply(statement.group(2).toLowerCase(Locale.ROOT))) {
                if (!listed.contains(status)) {
                    problems.add(file + ":" + line(code, string.start()) + ": INSERT into " + statement.group(2) + " leaves out " + status
                            + "; the aggregate writes its status, the database default does not decide it");
                }
            }
        }
        return problems;
    }

    static boolean aggregateMethod(String method) {
        return AGGREGATE_METHOD_PREFIXES.stream().anyMatch(method::startsWith);
    }

    private static String enclosing(List<int[]> methods, List<String> names, int offset) {
        String found = "<none>";
        for (int i = 0; i < methods.size(); i++) {
            if (methods.get(i)[0] <= offset) {
                found = names.get(i);
            }
        }
        return found;
    }

    private static int line(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
