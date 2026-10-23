package com.forcitis.one;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Status changes belong to the aggregate (DATA_DESIGN_CHECKLIST section 14, item 2). The application and infrastructure layers may read
 * and compare a status; they may not produce a new value, neither as an enum constant nor as a SQL literal.
 */
final class StateWritePolicy {
    private static final Pattern CONSTANT = Pattern.compile("\\b([A-Z][A-Za-z]*Status)\\.([A-Z][A-Z_0-9]*)\\b");
    private static final Pattern PARSE = Pattern.compile("\\b([A-Z][A-Za-z]*Status)\\.valueOf\\(");
    private static final Pattern STRING = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);
    private static final Pattern UPDATE_SET = Pattern.compile("\\bupdate\\b.*?\\bset\\b(.*?)(?:\\bwhere\\b|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_CONFLICT_SET = Pattern.compile("\\bdo\\s+update\\s+set\\b(.*?)(?:\\bwhere\\b|\\breturning\\b|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_LITERAL = Pattern.compile("(?:\\bset|,)\\s*(?:\\w+\\.)?(\\w*status)\\s*=\\s*'[^']*'", Pattern.CASE_INSENSITIVE);
    /** Restoring what the database holds is not a new value; it is allowed in the persistence adapters only. */
    private static final Pattern RESTORE = Pattern.compile("\\.valueOf\\(\\s*(?:rs|row)\\.");
    private static final Set<String> NOT_A_STATE = Set.of("HttpStatus", "HttpStatusCode");
    private static final Pattern INSERT = Pattern.compile("\\binsert\\s+into\\s+[\\w.]+\\s*\\(([^)]*)\\)\\s*(?:values|select)\\s*\\((.*)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private StateWritePolicy() {
    }

    /** Enum constants of a *Status type are only compared, and a status is never parsed in these layers. */
    static List<String> enumWrites(String file, String source) {
        List<String> problems = new ArrayList<>();
        String code = withoutComments(source);
        Matcher parse = PARSE.matcher(code);
        while (parse.find()) {
            boolean restoring = file.contains("/infrastructure/persistence/") && RESTORE.matcher(code.substring(parse.end() - ".valueOf(".length(),
                    Math.min(code.length(), parse.end() + 12))).find();
            if (NOT_A_STATE.contains(parse.group(1)) || restoring) {
                continue;
            }
            problems.add(file + ":" + line(code, parse.start()) + ": " + parse.group(1) + ".valueOf builds a status; only the aggregate restores one");
        }
        Matcher constant = CONSTANT.matcher(code);
        while (constant.find()) {
            if (NOT_A_STATE.contains(constant.group(1))) {
                continue;
            }
            if (!isComparison(code, constant.start(), constant.end())) {
                problems.add(file + ":" + line(code, constant.start()) + ": " + constant.group() + " is used outside a comparison; "
                        + "the aggregate decides the status");
            }
        }
        return problems;
    }

    /** SQL strings may not set a status to a literal: the value comes from the aggregate through a parameter. */
    static List<String> sqlWrites(String file, String source) {
        Set<String> problems = new java.util.LinkedHashSet<>();
        String code = withoutComments(source);
        Matcher string = STRING.matcher(code);
        while (string.find()) {
            String sql = string.group(1) != null ? string.group(1) : string.group(2);
            int at = line(code, string.start());
            for (Pattern set : List.of(UPDATE_SET, ON_CONFLICT_SET)) {
                Matcher update = set.matcher(sql);
                while (update.find()) {
                    Matcher literal = STATUS_LITERAL.matcher(", " + update.group(1));
                    while (literal.find()) {
                        problems.add(file + ":" + at + ": SQL sets " + literal.group(1) + " to a literal (" + literal.group().trim() + ")");
                    }
                }
            }
            Matcher insert = INSERT.matcher(sql);
            if (insert.find()) {
                List<String> columns = split(insert.group(1));
                List<String> values = split(insert.group(2));
                for (int i = 0; i < columns.size() && i < values.size(); i++) {
                    String column = columns.get(i).trim().toLowerCase(Locale.ROOT);
                    if ((column.equals("status") || column.endsWith("_status")) && values.get(i).trim().startsWith("'")) {
                        problems.add(file + ":" + at + ": SQL inserts " + column + " as the literal " + values.get(i).trim());
                    }
                }
            }
        }
        return new ArrayList<>(problems);
    }

    private static boolean isComparison(String code, int start, int end) {
        String before = code.substring(Math.max(0, start - 14), start).replaceAll("\\s+", " ");
        String after = code.substring(end, Math.min(code.length(), end + 6)).replaceAll("\\s+", " ");
        return before.endsWith("== ") || before.endsWith("!= ") || before.endsWith(".equals(") || before.endsWith("case ")
                || after.startsWith(" ==") || after.startsWith(" !=") || after.startsWith("==") || after.startsWith("!=");
    }

    /** Splits on top-level commas, so cast(:a as x) stays one value. */
    private static List<String> split(String list) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        StringBuilder current = new StringBuilder();
        for (char ch : list.toCharArray()) {
            if (ch == '\'') {
                quoted = !quoted;
            }
            if (!quoted && ch == '(') {
                depth++;
            } else if (!quoted && ch == ')') {
                if (depth == 0) {
                    break;
                }
                depth--;
            }
            if (!quoted && depth == 0 && ch == ',') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", "");
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
