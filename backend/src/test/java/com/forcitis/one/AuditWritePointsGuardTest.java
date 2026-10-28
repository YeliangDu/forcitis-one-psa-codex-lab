package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DATA-REPAIR-timesheet-audit TB-01: the writers of the free-text audit columns are a reviewed snapshot; a new one must name its row of the allowed-content table. */
class AuditWritePointsGuardTest {
    static final Path MAIN = Path.of("src/main/java");
    static final Path REGISTER = Path.of("architecture/audit-text-writers.txt");
    static final Path DEFINITION = Path.of("src/main/java/com/forcitis/one/platform/audit/api/AuditEntry.java");
    static final Set<String> PERSONAL_DATA = Set.of("NONE", "SURROGATE", "POSSIBLE");
    static final int LAST_ROW = 23;
    static final Pattern WRITE = Pattern.compile("new\\s+AuditEntry\\s*\\(|\\.withChange\\s*\\(");

    record Writer(int writes, String rows, String personalData) {
    }

    static Map<String, Writer> parse(List<String> lines, List<String> problems) {
        Map<String, Writer> rows = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            try {
                if (parts.length != 4) {
                    throw new IllegalArgumentException("four segments are needed");
                }
                String rowList = parts[2].strip();
                for (String row : rowList.split(",", -1)) {
                    int number = Integer.parseInt(row.strip());
                    if (number < 1 || number > LAST_ROW) {
                        throw new IllegalArgumentException("row " + number + " is not in 1.." + LAST_ROW);
                    }
                }
                Writer writer = new Writer(Integer.parseInt(parts[1].strip()), rowList, parts[3].strip());
                if (writer.writes() < 1 || !PERSONAL_DATA.contains(writer.personalData())) {
                    throw new IllegalArgumentException("writes must be positive and personal data one of " + PERSONAL_DATA);
                }
                if (rows.put(parts[0].strip(), writer) != null) {
                    throw new IllegalArgumentException("two lines for one file");
                }
            } catch (IllegalArgumentException wrong) {
                problems.add("bad line (" + wrong.getMessage() + "): " + line);
            }
        }
        return rows;
    }

    static int writes(String source) {
        Matcher m = WRITE.matcher(JavaCode.withoutComments(source));
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    /** Row 19 of the table: before_text and after_text are never written non-null, so the second and third argument of every withChange call is the literal null. */
    static List<String> changeTexts(String source) {
        String code = JavaCode.withoutComments(source);
        List<String> problems = new ArrayList<>();
        Matcher m = Pattern.compile("\\.withChange\\s*\\(").matcher(code);
        while (m.find()) {
            List<String> arguments = AuditDetailPolicy.arguments(code, m.end() - 1);
            if (arguments.size() != 3 || !arguments.get(1).equals("null") || !arguments.get(2).equals("null")) {
                problems.add("withChange(" + String.join(", ", arguments) + ")");
            }
        }
        return problems;
    }

    static List<String> check(Map<String, String> sources, Map<String, Writer> register) {
        List<String> problems = new ArrayList<>();
        sources.forEach((path, source) -> {
            int found = writes(source);
            Writer registered = register.get(path);
            if (found > 0 && registered == null) {
                problems.add(path + " writes an audit entry (" + found + " place(s)) but is not in " + REGISTER + ": name its row of the allowed-content table");
            } else if (registered != null && found != registered.writes()) {
                problems.add(path + " has " + found + " write place(s), the register says " + registered.writes());
            }
        });
        register.keySet().stream().filter(path -> !sources.containsKey(path) || writes(sources.get(path)) == 0)
                .forEach(path -> problems.add(path + " no longer writes an audit entry: delete its line"));
        return problems;
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: the files that write audit entries are exactly the registered ones, with their number of write places")
    void writersMatchTheRegister() throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).filter(f -> !f.equals(DEFINITION)).sorted().toList()) {
                sources.put(file.toString(), Files.readString(file));
            }
        }
        List<String> problems = new ArrayList<>();
        Map<String, Writer> register = parse(Files.readAllLines(REGISTER), problems);
        problems.addAll(check(sources, register));
        for (Map.Entry<String, String> source : sources.entrySet()) {
            changeTexts(source.getValue()).forEach(wrong -> problems.add(source.getKey() + " writes a before or after text: " + wrong));
        }
        assertThat(problems).isEmpty();
        assertThat(register).as("the card counted 35 files, rows 20 and 21 add the start-up audit and the relaxation mark of the single operator mode, RevokeRoleByOperations is a writer of row 15, row 22 adds the file service gate and row 23 its links, holds and reads, ADR-0034-IMPL PR-2 adds RoleVersionAudit (rows 4 and 6: ids, enum names and clock readings of role versions)").hasSize(41);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01 injected: an unregistered writer, a changed count and a stale line fail; a registered writer passes")
    void snapshotIsEnforcedBothWays() {
        Map<String, String> one = Map.of("A.java", "audit.record(new AuditEntry(\"a\", u, null, s, \"x\"));");
        Writer registered = new Writer(1, "1", "NONE");

        assertThat(check(one, Map.of("A.java", registered))).isEmpty();
        assertThat(check(one, Map.of())).singleElement().asString().contains("not in");
        assertThat(check(one, Map.of("A.java", new Writer(2, "1", "NONE")))).singleElement().asString().contains("register says 2");
        assertThat(check(Map.of("A.java", "class A {}"), Map.of("A.java", registered))).anyMatch(p -> p.contains("delete its line"));
        assertThat(check(Map.of("A.java", "e.withChange(r, null, null); // new AuditEntry("), Map.of("A.java", registered))).isEmpty();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01 injected: a non-null before or after text is caught, the literal null is not, and the class PI is refused in the register")
    void beforeAndAfterTextsStayNull() {
        assertThat(changeTexts("e.withChange(reason, null, null);")).isEmpty();
        assertThat(changeTexts("e.withChange(reason.strip(), null,\n null)")).isEmpty();
        assertThat(changeTexts("e.withChange(reason, old, null);")).hasSize(1);
        assertThat(changeTexts("e.withChange(reason, null, String.valueOf(a, b));")).hasSize(1);
        assertThat(changeTexts("e.withChange(reason, \"null\", null);")).hasSize(1);
        assertThat(changeTexts("e.withChange(reason, null);")).hasSize(1);
        assertThat(changeTexts("// e.withChange(r, a, b)")).isEmpty();
        List<String> problems = new ArrayList<>();
        assertThat(parse(List.of("A.java | 1 | 1 | PI"), problems)).isEmpty();
        assertThat(problems).singleElement().asString().contains("personal data");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01 injected: a line with a missing segment, an unknown row or class, a zero count or a duplicate is refused; a good line is read")
    void registerLinesAreChecked() {
        List<String> problems = new ArrayList<>();
        assertThat(parse(List.of("# c", "", "A.java | 1 | 1,2 | POSSIBLE"), problems)).containsOnlyKeys("A.java");
        assertThat(problems).isEmpty();
        for (String bad : List.of("A.java | 1 | 1", "A.java | 1 | 24 | NONE", "A.java | 1 | 0 | NONE", "A.java | 1 | 1 | SECRET", "A.java | 0 | 1 | NONE",
                "A.java | x | 1 | NONE", "A.java | 1 | | NONE")) {
            List<String> one = new ArrayList<>();
            assertThat(parse(List.of(bad), one)).as(bad).isEmpty();
            assertThat(one).as(bad).hasSize(1);
        }
        List<String> twice = new ArrayList<>();
        parse(List.of("A.java | 1 | 1 | NONE", "A.java | 1 | 1 | NONE"), twice);
        assertThat(twice).singleElement().asString().contains("two lines");
    }
}
