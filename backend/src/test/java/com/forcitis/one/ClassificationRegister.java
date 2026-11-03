package com.forcitis.one;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The column classification register architecture/data-classification.txt with the two dimensions of ADR-0043: access level (L1 to L4) and the personal
 * information mark. One line per column, four segments: {@code <schema.table.column> | <LEVEL> | NONE|PI|SPI | <reason>}. The parser accepts only that
 * shape and reports every bad line at once; the name rules (which column names must be registered at which level or mark) are shared by the guards.
 */
final class ClassificationRegister {
    static final Path FILE = Path.of("architecture/data-classification.txt");
    static final Path REPOSITORY_ROOT = Path.of("..");
    /** In order of access level L1 to L4. */
    static final List<String> LEVELS = List.of("PUBLIC", "INTERNAL", "SENSITIVE", "RESTRICTED");
    static final Set<String> MARKS = Set.of("NONE", "PI", "SPI");
    static final String PURPOSE = "用途：";
    /** A NONE row of a column whose name looks personal but is not (a surrogate key, a flag): the reason says why. */
    static final String EXEMPT = "豁免：";
    private static final String RULE = "D2 (ADR-0043, NAMING_STANDARD.md §2.5)";
    private static final Pattern REPORT = Pattern.compile("docs/privacy/[\\w./-]+\\.md");

    /**
     * Column names that must be registered at SENSITIVE or higher (ADR-0026 section 7; ADR-0043 guard rule 1, groups 1 and 2). The class word of an email
     * column, a name that ends in the token email_address (the email word starts the name or follows an underscore), is not a physical address (ADR-0043 :202, one-design ruling on #980); email_address_hash and every other
     * address stay sensitive.
     */
    static final Pattern SENSITIVE_NAME = Pattern.compile("(^|_)(password|secret|totp|bank|salary|wage|daily_rate|id_card|identity_number|passport|mobile|phone|birth|(?<!(?:^|_)email_)address|address(?=_)|attribute_bytes)(_|$)");
    /** Column names whose value is sensitive personal information (art. 28 PIPL; the HR list of the legal answer 2026-11-02): they must be marked SPI. */
    static final Pattern SPI_NAME = Pattern.compile("(^|_)(bank|id_card|identity_number|passport|ethnicity|household|hukou|biometric|fingerprint|face_template|gps|whereabouts|health|sick_leave|work_injury|maternity|pregnancy|special_deduction)(_|$)");
    /** Pay figures are personal information, sensitive personal information when legal says so (ADR-0043 U-5, U-11): PI or SPI. */
    static final Pattern PAY_NAME = Pattern.compile("(^|_)(salary|wage|daily_rate)(_|$)");
    /** Column names that must carry a mark of PI or SPI, shared with the data architecture guard D5. */
    static final Pattern PERSON_NAME = Pattern.compile("(^|_)(display_name|login_name|wecom|recipient|account_name|principal|employee_code|email|gender|mobile|phone|birth|address)(_|$)");

    record Row(String level, String mark, String reason) {
        boolean atLeast(String minimum) {
            return LEVELS.indexOf(level) >= LEVELS.indexOf(minimum);
        }

        boolean personal() {
            return !mark.equals("NONE");
        }

        boolean exempt() {
            return mark.equals("NONE") && reason.startsWith(EXEMPT);
        }
    }

    private ClassificationRegister() {
    }

    static Map<String, Row> read() throws IOException {
        return parse(Files.readAllLines(FILE), REPOSITORY_ROOT);
    }

    static Map<String, Row> parse(List<String> lines, Path root) {
        Map<String, Row> rows = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String at = "line " + (i + 1);
            String[] parts = line.split("\\s*\\|\\s*", -1);
            if (parts.length != 4) {
                problems.add(at + " has " + parts.length + " segments, not 4 (a three-segment line lacks the mark, and a reason must not contain |): " + line);
                continue;
            }
            Row row = new Row(parts[1].trim(), parts[2].trim(), parts[3].trim());
            String column = parts[0].trim();
            if (!LEVELS.contains(row.level())) {
                problems.add(at + " " + column + ": level '" + row.level() + "' is not one of " + LEVELS);
            } else if (!MARKS.contains(row.mark())) {
                problems.add(at + " " + column + ": mark '" + row.mark() + "' is not one of NONE, PI, SPI");
            } else if (row.reason().isEmpty()) {
                problems.add(at + " " + column + ": no reason");
            } else {
                problems.addAll(rowProblems(at + " " + column, row, root));
            }
            if (rows.put(column, row) != null) {
                problems.add(at + " " + column + " is registered twice");
            }
        }
        if (!problems.isEmpty()) {
            throw new AssertionError(GuardMessage.of(RULE, "architecture/data-classification.txt is malformed:\n  " + String.join("\n  ", problems),
                    "write each line as <schema.table.column> | PUBLIC|INTERNAL|SENSITIVE|RESTRICTED | NONE|PI|SPI | <reason>"));
        }
        return rows;
    }

    private static List<String> rowProblems(String where, Row row, Path root) {
        List<String> problems = new ArrayList<>();
        if (row.mark().equals("SPI") && !row.atLeast("SENSITIVE")) {
            problems.add(where + ": sensitive personal information is registered below SENSITIVE (ADR-0043 item 1.3)");
        }
        if (row.personal() && !row.reason().startsWith(PURPOSE)) {
            problems.add(where + ": a PI or SPI reason starts with '" + PURPOSE + "' and names the function the column serves (ADR-0043 R-1)");
        }
        if (row.mark().equals("SPI")) {
            Matcher report = REPORT.matcher(row.reason());
            if (!report.find() || !Files.exists(root.resolve(report.group()))) {
                problems.add(where + ": an SPI reason names an existing impact assessment report under docs/privacy/ (ADR-0043 R-4)");
            }
        }
        return problems;
    }

    /** Rows whose column is gone, and columns whose name demands a level or a mark the register does not give. */
    static List<String> problems(Set<String> existing, Map<String, Row> register) {
        List<String> problems = new ArrayList<>();
        for (String column : existing) {
            String name = column.substring(column.lastIndexOf('.') + 1);
            Row row = register.get(column);
            boolean sensitive = SENSITIVE_NAME.matcher(name).find() || SPI_NAME.matcher(name).find();
            if (sensitive && (row == null || !row.atLeast("SENSITIVE"))) {
                problems.add(column + " looks sensitive: register it SENSITIVE or RESTRICTED (or rename it)");
            }
            if (SPI_NAME.matcher(name).find() && (row == null || !row.mark().equals("SPI"))) {
                problems.add(column + " looks like sensitive personal information: mark it SPI (with its impact assessment report, ADR-0043 R-4), or rename it");
            }
            if (PAY_NAME.matcher(name).find() && (row == null || !row.personal())) {
                problems.add(column + " is a pay figure: mark it PI or SPI");
            }
        }
        register.keySet().stream().filter(column -> !existing.contains(column))
                .forEach(column -> problems.add(column + " is registered but does not exist: remove the stale line"));
        return problems;
    }
}
