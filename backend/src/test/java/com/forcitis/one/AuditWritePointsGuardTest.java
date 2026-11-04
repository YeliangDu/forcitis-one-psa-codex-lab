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
    static final Set<String> PERSONAL_DATA = Set.of("NONE", "SURROGATE", "POSSIBLE", "PI");
    /** Row 26 (#959, ADR-0043 U-7(b)): the one write point that may put the changed L2 profile columns into before and after text, and the only one that may be class PI. */
    static final String PROFILE_WRITER = "src/main/java/com/forcitis/one/organization/workforce/application/EmployeeProfileService.java";
    static final String PROFILE_ROW = "26";
    static final int LAST_ROW = 40;
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
                boolean profileRow = List.of(rowList.split(",")).stream().map(String::strip).anyMatch(PROFILE_ROW::equals);
                if (profileRow && !parts[0].strip().equals(PROFILE_WRITER)) {
                    throw new IllegalArgumentException("row " + PROFILE_ROW + " belongs to " + PROFILE_WRITER + " alone");
                }
                if (writer.personalData().equals("PI") && !profileRow) {
                    throw new IllegalArgumentException("the class PI is a personal data class for row " + PROFILE_ROW + " alone");
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
            // Row 26: the profile change may write the changed L2 profile columns as before and after text (ADR-0043 U-7(b); #959, one-lead ruling 2026-11-02).
            Writer writer = register.get(source.getKey());
            if (writer != null && List.of(writer.rows().split(",")).stream().map(String::strip).anyMatch("26"::equals)) {
                continue;
            }
            changeTexts(source.getValue()).forEach(wrong -> problems.add(source.getKey() + " writes a before or after text: " + wrong));
        }
        assertThat(problems).as(GuardMessage.of("DATA-REPAIR-timesheet-audit TB-01", "a file writes an audit entry without a register line, with another number of write places, or with a before or after text", "register the file in " + REGISTER + " with its row of the allowed-content table and write count, or delete a stale line, and pass the literal null as before and after text of withChange")).isEmpty();
        assertThat(register).as("the card counted 35 files, rows 20 and 21 add the start-up audit and the relaxation mark of the single operator mode, RevokeRoleByOperations is a writer of row 15, #910 adds OrgCountersigns (row 25: command id and kind of a countersigned withdrawal), row 22 adds the file service gate and row 23 its links, holds and reads, ADR-0034-IMPL PR-2 adds RoleVersionAudit (rows 4 and 6: ids, enum names and clock readings of role versions), F-1 #728 adds CreateRole (grant.refused for a two-person role's scope: reason code, scope kind and org unit ids), IMP-1030-PAR-B6 #875 adds SeparationHoldService (row 17: the release reason typed by a person, session mode, ids and the remaining count), #959 adds EmployeeProfileService (row 26: the changed L2 profile columns as before and after text, never the employee code), IMP-1030-PAR-B3 #872 adds PersonnelCommandService (rows 24 and 25: the ids, kind and date of an entry, and the withdrawal reason typed by a person with control characters replaced), #960 adds MigrateInitialVersions (row 15: operator and witness ids and the counts of one run), #938 adds AuthorizationVoiding (rows 1 and 4: the id of the voided request and, since #1032, the refusal's error enum); #895 adds CountersignRequests (row 31: ids, kind, sequence and decision of a countersign request and the reason typed by the decider) and AttributionCountersignService (row 32: the ids and kind of a countersigned withdrawal); #934 removes RecordSyncRunRelease (row 17: the old sync release), #907 adds OrgStructureCommandService (rows 29 and 25: the ids, kind and date of an entry with the changed columns before and after, and the withdrawal reason typed by a person) and OrgMergeSteps (row 30: the ids, kind and date of a finished merge); and the separation writes of HandleOrgSnapshotChange, which it deletes, sit in AccountGrants and SessionRevocation as on main.  #980 adds ProfileChangeRequestService (row 28: the id of the request and the void reason enum) and rows 27 and the amended 26 of EmployeeProfileService (the request id; the changed-profile entry of a confirmation is still written by EmployeeProfileService, the only writer of row 26).  #730 S3 adds PermissionAdjustmentAccess (row 1: the denied read or command of an item out of the caller's scope) and GetPermissionAdjustment (row 1: the item read by id).  #910 S3 adds CountersignReadAccess (row 1: the denied read of a countersign request or basis).  #895 adds ChangeFinanceHead (row 38: the ids and the date of a finance head change). #1172 adds GetPersonnelCommand (row 1: denied read outside the caller scope, no personal data in text). #1171 adds GetEmployee (row 1: the denied read of an out-of-scope roster entry). #1310 adds PublishStandardHoursVersion and VoidStandardHoursVersion (row 40: ids, scope, date and minute counts of a standard-hours version, no reason text)." + GuardMessage.of("DATA-REPAIR-timesheet-audit TB-01", "the number of registered audit writers changed", "update the expected count here only together with the allowed-content row of the new writer")).hasSize(60);
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
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01 injected (#959): row 26 and the class PI belong to the profile change writer alone, and only a row 26 file may write before and after text")
    void profileRowIsPinnedToItsWriter() {
        List<String> problems = new ArrayList<>();
        assertThat(parse(List.of(PROFILE_WRITER + " | 2 | 26 | PI"), problems)).containsOnlyKeys(PROFILE_WRITER);
        assertThat(problems).isEmpty();
        for (String bad : List.of("src/main/java/Other.java | 2 | 4,26 | NONE", "src/main/java/Other.java | 2 | 26 | PI", "src/main/java/Other.java | 1 | 4 | PI",
                PROFILE_WRITER + " | 2 | 25 | PI")) {
            List<String> one = new ArrayList<>();
            assertThat(parse(List.of(bad), one)).as(bad).isEmpty();
            assertThat(one).as(bad).hasSize(1);
        }
        assertThat(changeTexts("e.withChange(reason, before, after);")).as("a file outside row 26 cannot write the texts").hasSize(1);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01 injected: a line with a missing segment, an unknown row or class, a zero count or a duplicate is refused; a good line is read")
    void registerLinesAreChecked() {
        List<String> problems = new ArrayList<>();
        assertThat(parse(List.of("# c", "", "A.java | 1 | 1,2 | POSSIBLE"), problems)).containsOnlyKeys("A.java");
        assertThat(problems).isEmpty();
        for (String bad : List.of("A.java | 1 | 1", "A.java | 1 | 41 | NONE", "A.java | 1 | 0 | NONE", "A.java | 1 | 1 | SECRET", "A.java | 0 | 1 | NONE",
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
