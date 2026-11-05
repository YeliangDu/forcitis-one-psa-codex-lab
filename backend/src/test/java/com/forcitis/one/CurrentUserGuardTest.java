package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DATA_DESIGN_CHECKLIST section 14, items 5 and 12 (review B-1, B3-M1): whoever answers "which legal entity is this person in" for the session
 * (CurrentUser) or for a date (EmployeeLegalEntities) answers from the organisation or fails. No literal, no constant, no default, and no
 * CurrentUser made on the spot in production code.
 */
class CurrentUserGuardTest {
    static final Path MAIN = Path.of("src/main/java");
    static final Pattern ANSWERS = Pattern.compile("\\bimplements\\b[^{]*\\b(CurrentUser|EmployeeLegalEntities)\\b");
    static final Pattern ANONYMOUS = Pattern.compile("new\\s+CurrentUser\\s*\\(");
    static final Pattern LITERAL_LEGAL_ENTITY = Pattern.compile("new\\s+LegalEntityId\\(\\s*(?:[0-9][0-9_]*[lL]?\\s*\\)|(?:new\\s+)?UUID\\b)");
    static final Pattern INITIALISED_LEGAL_ENTITY = Pattern.compile("\\bLegalEntityId\\s+\\w+\\s*=");
    static final Pattern DEFAULTED = Pattern.compile("\\.\\s*(orElse|orElseGet)\\s*\\(");

    static List<String> problems(String file, String source) {
        String code = StateWritePolicy.withoutComments(source);
        List<String> problems = new ArrayList<>();
        if (ANONYMOUS.matcher(code).find()) {
            problems.add(file + ": a CurrentUser is made on the spot; the session context is the only one");
        }
        if (!ANSWERS.matcher(code).find()) {
            return problems;
        }
        if (LITERAL_LEGAL_ENTITY.matcher(code).find()) {
            problems.add(file + ": it builds a LegalEntityId from a literal; resolve it from the organisation");
        }
        if (INITIALISED_LEGAL_ENTITY.matcher(code).find()) {
            problems.add(file + ": it holds a LegalEntityId in a constant or variable that is set where it is declared; the answer comes from the organisation only");
        }
        if (DEFAULTED.matcher(code).find()) {
            problems.add(file + ": it falls back with orElse / orElseGet; a missing answer must fail, not become a default");
        }
        return problems;
    }

    @Test
    @DisplayName("no CurrentUser or EmployeeLegalEntities implementation answers a literal, a constant or a default legal entity, and no CurrentUser is made on the spot")
    void noMadeUpLegalEntity() throws IOException {
        List<String> problems = new ArrayList<>();
        int implementations = 0;
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (ANSWERS.matcher(StateWritePolicy.withoutComments(source)).find()) {
                    implementations++;
                }
                problems.addAll(problems(MAIN.relativize(file).toString(), source));
            }
        }
        assertThat(implementations).as(GuardMessage.of("CS-1.7", "the guard found fewer than 2 CurrentUser / EmployeeLegalEntities implementations, so it is scanning the wrong files",
                "check the ANSWERS pattern and MAIN in CurrentUserGuardTest")).isGreaterThanOrEqualTo(2);
        assertThat(problems).as(GuardMessage.of("CS-1.7",
                "a CurrentUser or EmployeeLegalEntities implementation answers with a literal, constant or default legal entity, or a CurrentUser is made on the spot",
                "resolve the legal entity from the organisation and fail when it is missing: no literal, no constant, no orElse / orElseGet, and take CurrentUser from the session context")).isEmpty();
    }

    @Test
    @DisplayName("injected: a literal, a constant, a default and an anonymous CurrentUser are caught; a lookup, a failure and an unrelated class are not")
    void madeUpAnswersAreCaught() {
        String hardCoded = "class X implements CurrentUser { public LegalEntityId legalEntityId() { return new LegalEntityId(new UUID(0, 1)); } }";
        String constant = "class C implements CurrentUser { private static final LegalEntityId HOME = load(); public LegalEntityId legalEntityId() { return HOME; } }";
        String defaulted = "class D implements EmployeeLegalEntities { public LegalEntityId on(EmployeeId e, LocalDate d) { return find(e, d).orElse(null); } }";
        String lazyDefault = "class L implements EmployeeLegalEntities { public LegalEntityId on(EmployeeId e, LocalDate d) { return find(e, d).orElseGet(() -> root()); } }";
        String anonymous = "class A { CurrentUser who = new CurrentUser() { public LegalEntityId legalEntityId() { return null; } }; }";
        String resolved = "class Y implements CurrentUser { public LegalEntityId legalEntityId() { return directory.on(employeeId(), today); } }";
        String failing = "class F implements EmployeeLegalEntities { public LegalEntityId on(EmployeeId e, LocalDate d) { return find(e, d).orElseThrow(); } }";
        String unrelated = "class Z { LegalEntityId id = new LegalEntityId(new UUID(0, 1)); Object o = x.orElse(1); }";
        String commented = "class W implements CurrentUser { // new LegalEntityId(new UUID(0, 1)) and .orElse(x)\n }";

        assertThat(problems("X.java", hardCoded)).hasSize(1);
        assertThat(problems("C.java", constant)).hasSize(1);
        assertThat(problems("D.java", defaulted)).hasSize(1);
        assertThat(problems("L.java", lazyDefault)).hasSize(1);
        assertThat(problems("A.java", anonymous)).hasSize(1);
        assertThat(problems("Y.java", resolved)).isEmpty();
        assertThat(problems("F.java", failing)).isEmpty();
        assertThat(problems("Z.java", unrelated)).isEmpty();
        assertThat(problems("W.java", commented)).isEmpty();
    }

    // DATA_DESIGN_CHECKLIST G-T2: production code has no hard-coded id of a dimension (legal entity, org unit, employee, position, project); a constant or literal is a default in disguise.
    static final String DIMENSION_ID = "(?:LegalEntityId|OrgUnitId|EmployeeId|PositionId|ProjectId)";
    static final Pattern LITERAL_DIMENSION_ID = Pattern.compile("\\bnew\\s+" + DIMENSION_ID + "\\(\\s*(?:\"|[0-9]|new\\s+UUID\\s*\\(|UUID\\s*\\.\\s*fromString\\(\\s*\")|\\b" + DIMENSION_ID + "\\.(?:of|from|parse)\\(\\s*(?:\"|[0-9])");
    static final Pattern CONSTANT_DIMENSION_ID = Pattern.compile("\\bstatic\\s+final\\s+" + DIMENSION_ID + "\\s+\\w+\\s*=");

    static List<String> hardCodedDimensionIds(String file, String source) {
        String code = StateWritePolicy.withoutComments(source);
        List<String> problems = new ArrayList<>();
        if (LITERAL_DIMENSION_ID.matcher(code).find()) {
            problems.add(file + ": it builds a dimension id (legal entity, org unit, employee, position, project) from a literal");
        }
        if (CONSTANT_DIMENSION_ID.matcher(code).find()) {
            problems.add(file + ": it declares a dimension id as a static final constant");
        }
        return problems;
    }

    @Test
    @DisplayName("G-T2 production code has no hard-coded or constant id of a legal entity, org unit, employee, position or project")
    void noHardCodedDimensionIds() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                problems.addAll(hardCodedDimensionIds(MAIN.relativize(file).toString(), Files.readString(file)));
            }
        }
        // the one registered exception: the approval replay derives stable ids from exported numbers; it never stands in for a missing answer
        String replay = "com/forcitis/one/platform/approval/infrastructure/replay/ExportedIds.java";
        assertThat(problems.removeIf(p -> p.startsWith(replay))).as(GuardMessage.of("G-T2", "the registered exception ExportedIds no longer builds an id from a number", "delete the exception from CurrentUserGuardTest")).isTrue();
        assertThat(problems).as(GuardMessage.of("G-T2", "production code hard-codes a dimension id", "take the id from the request, the session or the database and fail when the source is uncertain; never a literal, a constant or a default")).isEmpty();
        assertThat(hardCodedDimensionIds("A.java", "class A { LegalEntityId x = new LegalEntityId(new UUID(0, 1)); }")).hasSize(1);
        assertThat(hardCodedDimensionIds("B.java", "class B { Object o = EmployeeId.of(1); }")).hasSize(1);
        assertThat(hardCodedDimensionIds("C.java", "class C { static final OrgUnitId ROOT = load(); }")).hasSize(1);
        assertThat(hardCodedDimensionIds("D.java", "class D { OrgUnitId id = new OrgUnitId(request.orgUnitId()); // new OrgUnitId(1)\n }")).isEmpty();
    }
}
