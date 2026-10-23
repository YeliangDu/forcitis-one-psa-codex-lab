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
        assertThat(implementations).as("the guard must see both implementations").isGreaterThanOrEqualTo(2);
        assertThat(problems).isEmpty();
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
}
